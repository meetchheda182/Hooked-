package com.example.data.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "VideoMediaManager"
const val MAX_UPLOAD_BYTES = 200L * 1024L * 1024L // 200 MB
const val MAX_AI_INLINE_BYTES = 18L * 1024L * 1024L // Safe ceiling below 20 MB inline limit

data class ImportedVideo(
    val id: String,
    val title: String,
    val localVideoPath: String,
    val coverFramePath: String?,
    val fileSizeBytes: Long,
    val durationMs: Long
)

sealed interface AnalysisPayload {
    data class VideoBytes(val bytes: ByteArray, val mimeType: String) : AnalysisPayload
    data class KeyframesAndAudioFallback(val frames: List<Bitmap>, val note: String) : AnalysisPayload
}

class VideoMediaManager(private val context: Context) {

    private val videosDir: File
        get() = File(context.filesDir, "videos").apply { mkdirs() }

    private val coversDir: File
        get() = File(context.filesDir, "covers").apply { mkdirs() }

    private val exportsDir: File
        get() = File(context.filesDir, "exports").apply { mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, "thumbnails").apply { mkdirs() }

    suspend fun importPickedVideo(id: String, sourceUri: Uri): ImportedVideo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "Hooked Video"
        var declaredSize = -1L

        resolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        declaredSize = cursor.getLong(sizeIdx)
                    }
                }
            }

        if (declaredSize > MAX_UPLOAD_BYTES) {
            throw IllegalArgumentException(
                "Video is ${formatMegabytes(declaredSize)} MB, which exceeds the 200 MB upload limit."
            )
        }

        val destFile = File(videosDir, "$id.mp4")
        var copiedBytes = 0L
        resolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    copiedBytes += read
                    if (copiedBytes > MAX_UPLOAD_BYTES) {
                        destFile.delete()
                        throw IllegalArgumentException("Video exceeds the 200 MB upload limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalStateException("Could not open selected video from gallery.")

        if (copiedBytes == 0L) {
            destFile.delete()
            throw IllegalStateException("Selected video file is empty.")
        }

        val cleanTitle = displayName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Untitled Video" }

        val durationMs = extractDurationMs(destFile.absolutePath)
        val coverPath = extractCoverFrame(id, destFile.absolutePath)

        ImportedVideo(
            id = id,
            title = cleanTitle,
            localVideoPath = destFile.absolutePath,
            coverFramePath = coverPath,
            fileSizeBytes = copiedBytes,
            durationMs = durationMs
        )
    }

    private fun extractDurationMs(videoPath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract video duration", e)
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractCoverFrame(id: String, videoPath: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val outFile = File(coversDir, "$id.jpg")
                FileOutputStream(outFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                }
                bitmap.recycle()
                outFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract cover frame", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun loadCoverBitmap(coverFramePath: String?): Bitmap? {
        if (coverFramePath.isNullOrBlank()) return null
        val file = File(coverFramePath)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    @OptIn(UnstableApi::class)
    suspend fun prepareAnalysisPayload(
        videoPath: String,
        durationMs: Long,
        onStatusUpdate: (String) -> Unit
    ): AnalysisPayload {
        val originalFile = File(videoPath)
        if (!originalFile.exists()) {
            throw IllegalStateException("Local video file no longer exists on this device.")
        }

        if (originalFile.length() <= MAX_AI_INLINE_BYTES) {
            onStatusUpdate("Sending video & audio to Gemini...")
            val bytes = withContext(Dispatchers.IO) { originalFile.readBytes() }
            return AnalysisPayload.VideoBytes(bytes, "video/mp4")
        }

        // Video exceeds 18 MB: transcode a compact 360p (or 240p for longer clips) proxy with audio
        onStatusUpdate("Optimizing video proxy for Gemini analysis...")
        val targetHeight = if (durationMs > 240_000L) 240 else 360
        val proxyFile = File(context.cacheDir, "ai_proxy_${originalFile.nameWithoutExtension}.mp4")
        if (proxyFile.exists()) proxyFile.delete()

        return try {
            transcodeCompactProxy(
                inputUri = Uri.fromFile(originalFile),
                outputPath = proxyFile.absolutePath,
                targetHeight = targetHeight
            )
            if (proxyFile.exists() && proxyFile.length() in 1L..MAX_AI_INLINE_BYTES) {
                onStatusUpdate("Analyzing video & audio moments...")
                val bytes = withContext(Dispatchers.IO) { proxyFile.readBytes() }
                proxyFile.delete()
                AnalysisPayload.VideoBytes(bytes, "video/mp4")
            } else {
                proxyFile.delete()
                onStatusUpdate("Sampling visual keyframes for long video...")
                val frames = extractKeyframes(videoPath, durationMs, count = 12)
                if (frames.isEmpty()) {
                    throw IllegalStateException("Video is too large for inline analysis and keyframes could not be extracted.")
                }
                AnalysisPayload.KeyframesAndAudioFallback(
                    frames = frames,
                    note = "Long video sampled at 12 evenly spaced timestamps across ${durationMs / 1000}s."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Proxy transcode fallback to keyframes", e)
            proxyFile.delete()
            val frames = extractKeyframes(videoPath, durationMs, count = 12)
            if (frames.isNotEmpty()) {
                AnalysisPayload.KeyframesAndAudioFallback(
                    frames = frames,
                    note = "Sampled 12 evenly spaced visual frames across ${durationMs / 1000}s."
                )
            } else {
                throw e
            }
        }
    }

    private suspend fun extractKeyframes(
        videoPath: String,
        durationMs: Long,
        count: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(videoPath)
            val safeDuration = durationMs.coerceAtLeast(1000L)
            for (i in 0 until count) {
                val timeUs = ((safeDuration * i) / count.coerceAtLeast(1)) * 1000L
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(
                        frame,
                        480,
                        (480f * frame.height / frame.width.coerceAtLeast(1)).toInt().coerceAtLeast(270),
                        true
                    )
                    if (scaled !== frame) frame.recycle()
                    frames.add(scaled)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Keyframe extraction failed", e)
        } finally {
            runCatching { retriever.release() }
        }
        frames
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcodeCompactProxy(
        inputUri: Uri,
        outputPath: String,
        targetHeight: Int
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val mediaItem = MediaItem.fromUri(inputUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(Presentation.createForHeight(targetHeight))
                    )
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputPath)
            continuation.invokeOnCancellation {
                transformer.cancel()
            }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun exportHighlightClip(
        videoPath: String,
        highlightId: String,
        startMs: Long,
        endMs: Long,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.Main) {
        val sourceFile = File(videoPath)
        if (!sourceFile.exists()) {
            throw IllegalStateException("Original video file not found for clip export.")
        }

        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart + 1000L)
        val outFile = File(exportsDir, "hooked_clip_${highlightId}.mp4")
        if (outFile.exists()) outFile.delete()

        suspendCancellableCoroutine { continuation ->
            val clippedMediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(sourceFile))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(safeStart)
                        .setEndPositionMs(safeEnd)
                        .build()
                )
                .build()

            val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem).build()

            var progressJob: kotlinx.coroutines.Job? = null

            val transformer = Transformer.Builder(context)
                .experimentalSetTrimOptimizationEnabled(true)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob?.cancel()
                        onProgress(100)
                        if (continuation.isActive) continuation.resume(outFile.absolutePath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressJob?.cancel()
                        outFile.delete()
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            transformer.start(editedMediaItem, outFile.absolutePath)

            val progressHolder = ProgressHolder()
            progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                while (continuation.isActive) {
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 99))
                    }
                    delay(200L)
                }
            }

            continuation.invokeOnCancellation {
                progressJob.cancel()
                transformer.cancel()
                outFile.delete()
            }
        }
    }

    suspend fun saveGeneratedThumbnail(
        videoId: String,
        ideaId: String,
        bitmap: Bitmap
    ): String = withContext(Dispatchers.IO) {
        val outFile = File(thumbnailsDir, "${videoId}_${ideaId}.png")
        FileOutputStream(outFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        outFile.absolutePath
    }

    fun deleteVideoArtifacts(
        videoPath: String,
        coverPath: String?,
        clipPaths: List<String?>,
        thumbnailPaths: List<String?>
    ) {
        runCatching { File(videoPath).delete() }
        coverPath?.let { runCatching { File(it).delete() } }
        clipPaths.filterNotNull().forEach { runCatching { File(it).delete() } }
        thumbnailPaths.filterNotNull().forEach { runCatching { File(it).delete() } }
    }

    fun shareFile(filePath: String, mimeType: String, chooserTitle: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    companion object {
        fun formatMegabytes(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.1f", mb)
        }
    }
}

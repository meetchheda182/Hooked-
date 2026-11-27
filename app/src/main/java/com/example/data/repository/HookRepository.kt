package com.example.data.repository

import android.net.Uri
import android.util.Log
import com.example.data.ai.HookAiService
import com.example.data.local.CreatorPrefsDataStore
import com.example.data.local.UploadEventEntity
import com.example.data.local.VideoAnalysisDao
import com.example.data.local.VideoAnalysisEntity
import com.example.data.media.VideoMediaManager
import com.example.domain.model.AnalysisStatus
import com.example.domain.model.CreatorCadenceSummary
import com.example.domain.model.UploadEventType
import com.example.domain.model.VideoAnalysis
import com.example.domain.model.WeeklyUploadBucket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "HookRepository"

class HookRepository(
    private val dao: VideoAnalysisDao,
    private val prefs: CreatorPrefsDataStore,
    private val mediaManager: VideoMediaManager,
    private val aiService: HookAiService
) {

    fun observeHistory(): Flow<List<VideoAnalysis>> =
        dao.observeAllAnalyses().map { list -> list.map { it.toDomain() } }

    fun observeAnalysis(id: String): Flow<VideoAnalysis?> =
        dao.observeAnalysisById(id).map { it?.toDomain() }

    fun observeTargetUploadsPerWeek(): Flow<Int> = prefs.targetUploadsPerWeek

    suspend fun setTargetUploadsPerWeek(target: Int) {
        prefs.setTargetUploadsPerWeek(target)
    }

    suspend fun importAndAnalyzeVideo(sourceUri: Uri): String {
        val id = UUID.randomUUID().toString()
        val imported = mediaManager.importPickedVideo(id, sourceUri)
        val initial = VideoAnalysis(
            id = imported.id,
            title = imported.title,
            localVideoPath = imported.localVideoPath,
            coverFramePath = imported.coverFramePath,
            fileSizeBytes = imported.fileSizeBytes,
            durationMs = imported.durationMs,
            status = AnalysisStatus.ANALYZING,
            statusDetail = "Preparing video & audio...",
            createdAtEpochMs = System.currentTimeMillis()
        )
        dao.upsertAnalysis(VideoAnalysisEntity.fromDomain(initial))
        runAnalysisPipeline(initial)
        return id
    }

    suspend fun retryAnalysis(videoId: String) {
        val existing = dao.getAnalysisById(videoId)?.toDomain() ?: return
        val resetting = existing.copy(
            status = AnalysisStatus.ANALYZING,
            statusDetail = "Retrying analysis...",
            errorMessage = null
        )
        dao.upsertAnalysis(VideoAnalysisEntity.fromDomain(resetting))
        runAnalysisPipeline(resetting)
    }

    private suspend fun runAnalysisPipeline(record: VideoAnalysis) {
        val startTime = System.currentTimeMillis()
        try {
            val payload = mediaManager.prepareAnalysisPayload(
                videoPath = record.localVideoPath,
                durationMs = record.durationMs
            ) { statusText ->
                Log.d(TAG, "Pipeline status: $statusText")
            }

            dao.upsertAnalysis(
                VideoAnalysisEntity.fromDomain(
                    record.copy(statusDetail = "Gemini 3.8 Flash watching & listening...")
                )
            )

            val aiResult = aiService.analyzeVideo(
                videoTitle = record.title,
                durationMs = record.durationMs,
                payload = payload
            )

            val finishedAt = System.currentTimeMillis()
            val completed = record.copy(
                status = AnalysisStatus.COMPLETED,
                statusDetail = null,
                errorMessage = null,
                analyzedAtEpochMs = finishedAt,
                analysisDurationMs = finishedAt - startTime,
                highlights = aiResult.highlights,
                titleIdeas = aiResult.titleIdeas,
                thumbnailIdeas = aiResult.thumbnailIdeas
            )
            dao.upsertAnalysis(VideoAnalysisEntity.fromDomain(completed))

            dao.insertEvent(
                UploadEventEntity(
                    id = UUID.randomUUID().toString(),
                    videoId = record.id,
                    eventType = UploadEventType.VIDEO_ANALYZED.name,
                    label = "Analyzed \"${record.title}\"",
                    timestampEpochMs = finishedAt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Analysis pipeline failed for ${record.id}", e)
            val failed = record.copy(
                status = AnalysisStatus.FAILED,
                statusDetail = null,
                errorMessage = e.localizedMessage ?: "Analysis failed. Tap Retry to try again."
            )
            dao.upsertAnalysis(VideoAnalysisEntity.fromDomain(failed))
        }
    }

    suspend fun generateThumbnailForIdea(videoId: String, ideaId: String) {
        val current = dao.getAnalysisById(videoId)?.toDomain()
            ?: throw IllegalStateException("Video record not found.")
        val idea = current.thumbnailIdeas.firstOrNull { it.id == ideaId }
            ?: throw IllegalStateException("Thumbnail idea not found.")

        val coverBitmap = withContext(Dispatchers.IO) {
            mediaManager.loadCoverBitmap(current.coverFramePath)
        }

        val generatedBitmap = try {
            aiService.generateThumbnailImage(
                idea = idea,
                videoTitle = current.title,
                coverBitmap = coverBitmap
            )
        } finally {
            coverBitmap?.recycle()
        }

        val savedPath = mediaManager.saveGeneratedThumbnail(
            videoId = videoId,
            ideaId = ideaId,
            bitmap = generatedBitmap
        )
        generatedBitmap.recycle()

        val updatedIdeas = current.thumbnailIdeas.map { item ->
            if (item.id == ideaId) item.copy(generatedImagePath = savedPath) else item
        }
        dao.upsertAnalysis(VideoAnalysisEntity.fromDomain(current.copy(thumbnailIdeas = updatedIdeas)))

        dao.insertEvent(
            UploadEventEntity(
                id = UUID.randomUUID().toString(),
                videoId = videoId,
                eventType = UploadEventType.THUMBNAIL_GENERATED.name,
                label = "Generated thumbnail \"${idea.conceptTitle}\"",
                timestampEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun exportHighlightClip(
        videoId: String,
        highlightId: String,
        onProgress: (Int) -> Unit
    ): String {
        val current = dao.getAnalysisById(videoId)?.toDomain()
            ?: throw IllegalStateException("Video record not found.")
        val highlight = current.highlights.firstOrNull { it.id == highlightId }
            ?: throw IllegalStateException("Highlight not found.")

        val exportedPath = mediaManager.exportHighlightClip(
            videoPath = current.localVideoPath,
            highlightId = highlightId,
            startMs = highlight.startMs,
            endMs = highlight.endMs,
            onProgress = onProgress
        )

        val updatedHighlights = current.highlights.map { item ->
            if (item.id == highlightId) item.copy(exportedClipPath = exportedPath) else item
        }
        dao.upsertAnalysis(VideoAnalysisEntity.fromDomain(current.copy(highlights = updatedHighlights)))

        dao.insertEvent(
            UploadEventEntity(
                id = UUID.randomUUID().toString(),
                videoId = videoId,
                eventType = UploadEventType.CLIP_EXPORTED.name,
                label = "Exported clip \"${highlight.title}\"",
                timestampEpochMs = System.currentTimeMillis()
            )
        )

        return exportedPath
    }

    suspend fun recordTitleCopied(videoId: String, titleText: String) {
        dao.insertEvent(
            UploadEventEntity(
                id = UUID.randomUUID().toString(),
                videoId = videoId,
                eventType = UploadEventType.TITLE_COPIED.name,
                label = "Copied title \"${titleText.take(36)}\"",
                timestampEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun logExternalUpload(note: String = "Published without Hooked") {
        dao.insertEvent(
            UploadEventEntity(
                id = UUID.randomUUID().toString(),
                videoId = null,
                eventType = UploadEventType.EXTERNAL_UPLOAD_LOGGED.name,
                label = note,
                timestampEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteVideo(videoId: String) {
        val existing = dao.getAnalysisById(videoId)?.toDomain()
        if (existing != null) {
            withContext(Dispatchers.IO) {
                mediaManager.deleteVideoArtifacts(
                    videoPath = existing.localVideoPath,
                    coverPath = existing.coverFramePath,
                    clipPaths = existing.highlights.map { it.exportedClipPath },
                    thumbnailPaths = existing.thumbnailIdeas.map { it.generatedImagePath }
                )
            }
        }
        dao.deleteEventsForVideo(videoId)
        dao.deleteAnalysisById(videoId)
    }

    fun shareClip(clipPath: String) {
        mediaManager.shareFile(clipPath, "video/mp4", "Share Hooked Highlight Clip")
    }

    fun shareThumbnail(imagePath: String) {
        mediaManager.shareFile(imagePath, "image/png", "Share Hooked Thumbnail")
    }

    fun observeCadenceSummary(): Flow<CreatorCadenceSummary> = combine(
        dao.observeAllAnalyses(),
        dao.observeAllEvents(),
        prefs.targetUploadsPerWeek
    ) { analyses, events, targetPerWeek ->
        buildCadenceSummary(
            analyses = analyses.map { it.toDomain() },
            events = events,
            targetPerWeek = targetPerWeek
        )
    }

    private fun buildCadenceSummary(
        analyses: List<VideoAnalysis>,
        events: List<UploadEventEntity>,
        targetPerWeek: Int
    ): CreatorCadenceSummary {
        val now = System.currentTimeMillis()
        val weekMs = 7L * 24L * 60L * 60L * 1000L
        val monthMs = 30L * 24L * 60L * 60L * 1000L

        val completedAnalyses = analyses.filter { it.status == AnalysisStatus.COMPLETED }
        val totalHooked = analyses.size
        val externalUploads = events.filter { it.eventType == UploadEventType.EXTERNAL_UPLOAD_LOGGED.name }
        val totalExternal = externalUploads.size
        val totalCombinedUploads = totalHooked + totalExternal
        val coveragePct = if (totalCombinedUploads == 0) {
            100
        } else {
            ((totalHooked * 100.0) / totalCombinedUploads).toInt().coerceIn(0, 100)
        }

        val uploadsLast7 = analyses.count { now - it.createdAtEpochMs <= weekMs }
        val uploadsLast30 = analyses.count { now - it.createdAtEpochMs <= monthMs }

        val sortedTimes = analyses.map { it.createdAtEpochMs }.sorted()
        val avgDaysBetween = if (sortedTimes.size >= 2) {
            val diffs = sortedTimes.zipWithNext { a, b -> (b - a) / (24.0 * 60.0 * 60.0 * 1000.0) }
            diffs.average()
        } else {
            null
        }

        // Compute 6-week buckets (oldest -> newest)
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val currentWeekStart = cal.timeInMillis
        val dateFmt = SimpleDateFormat("MMM d", Locale.US)

        val buckets = (5 downTo 0).map { weeksAgo ->
            val start = currentWeekStart - weeksAgo * weekMs
            val end = start + weekMs
            val hookedCount = analyses.count { it.createdAtEpochMs in start until end }
            val extCount = externalUploads.count { it.timestampEpochMs in start until end }
            WeeklyUploadBucket(
                label = if (weeksAgo == 0) "This wk" else dateFmt.format(Date(start)),
                hookedUploads = hookedCount,
                externalUploads = extCount
            )
        }

        // Current consecutive weekly streak (from current or previous week backwards)
        var streak = 0
        for (bucket in buckets.reversed()) {
            if (bucket.hookedUploads > 0) {
                streak++
            } else if (streak > 0) {
                break
            } else if (bucket === buckets.last()) {
                // Allow current week to be in-progress without breaking streak from last week
                continue
            } else {
                break
            }
        }

        val clipsExported = events.count { it.eventType == UploadEventType.CLIP_EXPORTED.name }
        val thumbsGenerated = events.count { it.eventType == UploadEventType.THUMBNAIL_GENERATED.name }
        val titlesCopied = events.count { it.eventType == UploadEventType.TITLE_COPIED.name }

        val engagedVideoIds = events
            .filter {
                it.videoId != null && (
                    it.eventType == UploadEventType.CLIP_EXPORTED.name ||
                        it.eventType == UploadEventType.THUMBNAIL_GENERATED.name ||
                        it.eventType == UploadEventType.TITLE_COPIED.name
                    )
            }
            .mapNotNull { it.videoId }
            .toSet()

        val packagingAdoptionPct = if (completedAnalyses.isEmpty()) {
            0
        } else {
            val matched = completedAnalyses.count { it.id in engagedVideoIds }
            ((matched * 100.0) / completedAnalyses.size).toInt().coerceIn(0, 100)
        }

        return CreatorCadenceSummary(
            totalHookedUploads = totalHooked,
            totalExternalUploads = totalExternal,
            hookedCoveragePercent = coveragePct,
            uploadsLast7Days = uploadsLast7,
            uploadsLast30Days = uploadsLast30,
            avgDaysBetweenUploads = avgDaysBetween,
            currentWeeklyStreak = streak,
            targetUploadsPerWeek = targetPerWeek,
            packagingAdoptionPercent = packagingAdoptionPct,
            totalClipsExported = clipsExported,
            totalThumbnailsGenerated = thumbsGenerated,
            totalTitlesCopied = titlesCopied,
            weeklyBuckets = buckets,
            recentActivities = events.take(15).map { it.toDomain() }
        )
    }
}

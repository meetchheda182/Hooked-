package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.media.AnalysisPayload
import com.example.domain.model.HighlightMoment
import com.example.domain.model.ThumbnailIdea
import com.example.domain.model.TitleIdea
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "HookAiService"

data class VideoAnalysisResult(
    val highlights: List<HighlightMoment>,
    val titleIdeas: List<TitleIdea>,
    val thumbnailIdeas: List<ThumbnailIdea>
)

class HookAiService {

    private val analysisSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "highlights" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Short punchy label for this highlight moment"),
                            "startSeconds" to Schema.integer(description = "Start timestamp in whole seconds from 0"),
                            "endSeconds" to Schema.integer(description = "End timestamp in whole seconds"),
                            "hookScore" to Schema.integer(description = "Hook strength score from 1 to 100"),
                            "reason" to Schema.string(description = "Why this visual and audio moment hooks viewers"),
                            "type" to Schema.enumeration(
                                values = listOf(
                                    "Pattern Interrupt",
                                    "Curiosity Gap",
                                    "Emotional Peak",
                                    "Bold Claim",
                                    "Visual Payoff",
                                    "Story Twist"
                                ),
                                description = "Category of hook"
                            )
                        )
                    ),
                    description = "Between 3 and 8 best hook moments in the video"
                ),
                "titleIdeas" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "High-CTR video title"),
                            "angle" to Schema.string(description = "Psychological angle, e.g. Curiosity, Contrarian, Outcome, Urgency, Story")
                        )
                    ),
                    description = "Exactly 5 compelling title ideas"
                ),
                "thumbnailIdeas" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "conceptTitle" to Schema.string(description = "Short concept headline for the thumbnail"),
                            "visualDescription" to Schema.string(description = "Detailed scene composition, subject expression, lighting, and props for generating a 16:9 thumbnail image"),
                            "textOverlay" to Schema.string(description = "Bold 2 to 4 word text overlay idea"),
                            "colorMood" to Schema.string(description = "Color palette & lighting mood")
                        )
                    ),
                    description = "Exactly 5 distinct thumbnail concepts"
                )
            )
        )
    }

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
            }
        )
    }

    private val thumbnailImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    suspend fun analyzeVideo(
        videoTitle: String,
        durationMs: Long,
        payload: AnalysisPayload
    ): VideoAnalysisResult {
        val durationSeconds = (durationMs / 1000L).coerceAtLeast(1L)
        val prompt = buildString {
            appendLine("You are Hooked, an expert short-form and YouTube video packaging strategist.")
            appendLine("Watch and listen closely to this video (title: \"$videoTitle\", total duration: $durationSeconds seconds).")
            appendLine("Identify 3 to 8 of the strongest highlight / hook moments.")
            appendLine("Rules:")
            appendLine("- Every highlight MUST have startSeconds >= 0 and endSeconds <= $durationSeconds and endSeconds > startSeconds.")
            appendLine("- Prefer tight, shareable clip windows (typically 5 to 45 seconds when the video allows).")
            appendLine("- Rank hookScore from 1 to 100 based on audio energy, visual contrast, storytelling tension, and scroll-stopping power.")
            appendLine("- Provide 5 distinct, high-CTR title ideas.")
            appendLine("- Provide 5 distinct, vivid thumbnail ideas with rich visual composition details suitable for image generation.")
        }

        val response = try {
            withTimeout(90_000L) {
                when (payload) {
                    is AnalysisPayload.VideoBytes -> {
                        analysisModel.generateContent(
                            content {
                                inlineData(payload.bytes, payload.mimeType)
                                text(prompt)
                            }
                        )
                    }
                    is AnalysisPayload.KeyframesAndAudioFallback -> {
                        analysisModel.generateContent(
                            content {
                                text("${payload.note}\n$prompt")
                                payload.frames.forEach { bitmap ->
                                    image(bitmap)
                                }
                            }
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Video analysis timed out", e)
            throw IllegalStateException("Analysis timed out. Try a shorter clip or check your connection.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Gemini quota exceeded", e)
            throw IllegalStateException("AI quota limit reached. Please wait a moment and retry.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI video analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Video analysis failed.")
        }

        val jsonText = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned an empty analysis response.")

        return parseAnalysisJson(jsonText, durationMs)
    }

    suspend fun generateThumbnailImage(
        idea: ThumbnailIdea,
        videoTitle: String,
        coverBitmap: Bitmap?
    ): Bitmap {
        val prompt = buildString {
            appendLine("Create a bold, ultra-clickable 16:9 YouTube / creator video thumbnail.")
            appendLine("Video topic: $videoTitle")
            appendLine("Concept: ${idea.conceptTitle}")
            appendLine("Visual composition: ${idea.visualDescription}")
            appendLine("Color & lighting mood: ${idea.colorMood}")
            if (idea.textOverlay.isNotBlank()) {
                appendLine("Include bold, clean, high-contrast display typography reading: \"${idea.textOverlay}\"")
            }
            appendLine("Style: crisp studio photography, dramatic rim lighting, expressive focal subject, uncluttered background, vibrant contrast, no watermarks.")
        }

        return try {
            withTimeout(60_000L) {
                val response = if (coverBitmap != null) {
                    thumbnailImageModel.generateContent(
                        content {
                            image(coverBitmap)
                            text("Use this video frame for subject and aesthetic inspiration, and generate a fresh polished thumbnail:\n$prompt")
                        }
                    )
                } else {
                    thumbnailImageModel.generateContent(prompt)
                }

                val imagePart = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()

                imagePart?.image
                    ?: throw IllegalStateException("No thumbnail image was returned by the model.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Thumbnail image generation timed out", e)
            throw IllegalStateException("Thumbnail generation timed out after 60 seconds. Please try again.")
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Thumbnail image quota exceeded", e)
            throw IllegalStateException("Rate limit or Blaze quota reached for image generation. Please try again later.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail image generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Thumbnail image generation failed.")
        }
    }

    private fun parseAnalysisJson(jsonText: String, durationMs: Long): VideoAnalysisResult {
        val root = JSONObject(jsonText)
        val maxSeconds = (durationMs / 1000L).coerceAtLeast(5L)

        val highlightsArr = root.optJSONArray("highlights")
        val highlights = mutableListOf<HighlightMoment>()
        if (highlightsArr != null) {
            for (i in 0 until highlightsArr.length()) {
                val obj = highlightsArr.getJSONObject(i)
                val rawStart = obj.optLong("startSeconds", 0L).coerceIn(0L, maxSeconds - 1L)
                val rawEnd = obj.optLong("endSeconds", rawStart + 10L)
                    .coerceIn(rawStart + 1L, maxSeconds)
                highlights.add(
                    HighlightMoment(
                        id = "hl_${System.currentTimeMillis()}_$i",
                        title = obj.optString("title", "Hook ${i + 1}"),
                        startMs = rawStart * 1000L,
                        endMs = rawEnd * 1000L,
                        hookScore = obj.optInt("hookScore", 82).coerceIn(1, 100),
                        reason = obj.optString("reason", ""),
                        type = obj.optString("type", "Pattern Interrupt")
                    )
                )
            }
        }

        val titlesArr = root.optJSONArray("titleIdeas")
        val titleIdeas = mutableListOf<TitleIdea>()
        if (titlesArr != null) {
            for (i in 0 until titlesArr.length()) {
                val obj = titlesArr.getJSONObject(i)
                titleIdeas.add(
                    TitleIdea(
                        id = "title_$i",
                        title = obj.optString("title", "Title Idea ${i + 1}"),
                        angle = obj.optString("angle", "Curiosity")
                    )
                )
            }
        }

        val thumbsArr = root.optJSONArray("thumbnailIdeas")
        val thumbnailIdeas = mutableListOf<ThumbnailIdea>()
        if (thumbsArr != null) {
            for (i in 0 until thumbsArr.length()) {
                val obj = thumbsArr.getJSONObject(i)
                thumbnailIdeas.add(
                    ThumbnailIdea(
                        id = "thumb_$i",
                        conceptTitle = obj.optString("conceptTitle", "Concept ${i + 1}"),
                        visualDescription = obj.optString("visualDescription", ""),
                        textOverlay = obj.optString("textOverlay", ""),
                        colorMood = obj.optString("colorMood", "High-Contrast")
                    )
                )
            }
        }

        if (highlights.isEmpty() && titleIdeas.isEmpty()) {
            throw IllegalStateException("Analysis returned no usable highlights or titles.")
        }

        return VideoAnalysisResult(
            highlights = highlights.sortedByDescending { it.hookScore },
            titleIdeas = titleIdeas.take(5),
            thumbnailIdeas = thumbnailIdeas.take(5)
        )
    }
}

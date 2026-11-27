package com.example.domain.model

enum class AnalysisStatus {
    ANALYZING,
    COMPLETED,
    FAILED
}

enum class UploadEventType {
    VIDEO_ANALYZED,
    EXTERNAL_UPLOAD_LOGGED,
    CLIP_EXPORTED,
    THUMBNAIL_GENERATED,
    TITLE_COPIED
}

data class HighlightMoment(
    val id: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val hookScore: Int,
    val reason: String,
    val type: String,
    val exportedClipPath: String? = null
) {
    val durationSeconds: Int
        get() = (((endMs - startMs).coerceAtLeast(1000L)) / 1000L).toInt()
}

data class TitleIdea(
    val id: String,
    val title: String,
    val angle: String
)

data class ThumbnailIdea(
    val id: String,
    val conceptTitle: String,
    val visualDescription: String,
    val textOverlay: String,
    val colorMood: String,
    val generatedImagePath: String? = null
)

data class VideoAnalysis(
    val id: String,
    val title: String,
    val localVideoPath: String,
    val coverFramePath: String?,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val status: AnalysisStatus,
    val statusDetail: String? = null,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long,
    val analyzedAtEpochMs: Long? = null,
    val analysisDurationMs: Long? = null,
    val highlights: List<HighlightMoment> = emptyList(),
    val titleIdeas: List<TitleIdea> = emptyList(),
    val thumbnailIdeas: List<ThumbnailIdea> = emptyList()
)

data class WeeklyUploadBucket(
    val label: String,
    val hookedUploads: Int,
    val externalUploads: Int
)

data class UploadActivityItem(
    val id: String,
    val eventType: UploadEventType,
    val label: String,
    val timestampEpochMs: Long
)

data class CreatorCadenceSummary(
    val totalHookedUploads: Int,
    val totalExternalUploads: Int,
    val hookedCoveragePercent: Int,
    val uploadsLast7Days: Int,
    val uploadsLast30Days: Int,
    val avgDaysBetweenUploads: Double?,
    val currentWeeklyStreak: Int,
    val targetUploadsPerWeek: Int,
    val packagingAdoptionPercent: Int,
    val totalClipsExported: Int,
    val totalThumbnailsGenerated: Int,
    val totalTitlesCopied: Int,
    val weeklyBuckets: List<WeeklyUploadBucket>,
    val recentActivities: List<UploadActivityItem>
) {
    val verdictTitle: String
        get() = when {
            totalHookedUploads == 0 -> "Ready for your first upload"
            hookedCoveragePercent >= 90 -> "Used on nearly every upload"
            hookedCoveragePercent >= 65 -> "Consistent upload habit"
            hookedCoveragePercent >= 35 -> "Growing studio adoption"
            else -> "Occasional hook check-ins"
        }
}

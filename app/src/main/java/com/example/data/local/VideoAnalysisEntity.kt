package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.AnalysisStatus
import com.example.domain.model.VideoAnalysis

@Entity(tableName = "video_analyses")
data class VideoAnalysisEntity(
    @PrimaryKey val id: String,
    val title: String,
    val localVideoPath: String,
    val coverFramePath: String?,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val status: String,
    val statusDetail: String?,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val analyzedAtEpochMs: Long?,
    val analysisDurationMs: Long?,
    val highlightsJson: String,
    val titleIdeasJson: String,
    val thumbnailIdeasJson: String
) {
    fun toDomain(): VideoAnalysis = VideoAnalysis(
        id = id,
        title = title,
        localVideoPath = localVideoPath,
        coverFramePath = coverFramePath,
        fileSizeBytes = fileSizeBytes,
        durationMs = durationMs,
        status = runCatching { AnalysisStatus.valueOf(status) }.getOrDefault(AnalysisStatus.FAILED),
        statusDetail = statusDetail,
        errorMessage = errorMessage,
        createdAtEpochMs = createdAtEpochMs,
        analyzedAtEpochMs = analyzedAtEpochMs,
        analysisDurationMs = analysisDurationMs,
        highlights = JsonCodecs.decodeHighlights(highlightsJson),
        titleIdeas = JsonCodecs.decodeTitles(titleIdeasJson),
        thumbnailIdeas = JsonCodecs.decodeThumbnails(thumbnailIdeasJson)
    )

    companion object {
        fun fromDomain(domain: VideoAnalysis): VideoAnalysisEntity = VideoAnalysisEntity(
            id = domain.id,
            title = domain.title,
            localVideoPath = domain.localVideoPath,
            coverFramePath = domain.coverFramePath,
            fileSizeBytes = domain.fileSizeBytes,
            durationMs = domain.durationMs,
            status = domain.status.name,
            statusDetail = domain.statusDetail,
            errorMessage = domain.errorMessage,
            createdAtEpochMs = domain.createdAtEpochMs,
            analyzedAtEpochMs = domain.analyzedAtEpochMs,
            analysisDurationMs = domain.analysisDurationMs,
            highlightsJson = JsonCodecs.encodeHighlights(domain.highlights),
            titleIdeasJson = JsonCodecs.encodeTitles(domain.titleIdeas),
            thumbnailIdeasJson = JsonCodecs.encodeThumbnails(domain.thumbnailIdeas)
        )
    }
}

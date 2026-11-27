package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.UploadActivityItem
import com.example.domain.model.UploadEventType

@Entity(tableName = "upload_events")
data class UploadEventEntity(
    @PrimaryKey val id: String,
    val videoId: String?,
    val eventType: String,
    val label: String,
    val timestampEpochMs: Long
) {
    fun toDomain(): UploadActivityItem = UploadActivityItem(
        id = id,
        eventType = runCatching { UploadEventType.valueOf(eventType) }
            .getOrDefault(UploadEventType.VIDEO_ANALYZED),
        label = label,
        timestampEpochMs = timestampEpochMs
    )
}

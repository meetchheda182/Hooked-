package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoAnalysisDao {
    @Query("SELECT * FROM video_analyses ORDER BY createdAtEpochMs DESC")
    fun observeAllAnalyses(): Flow<List<VideoAnalysisEntity>>

    @Query("SELECT * FROM video_analyses WHERE id = :id LIMIT 1")
    fun observeAnalysisById(id: String): Flow<VideoAnalysisEntity?>

    @Query("SELECT * FROM video_analyses WHERE id = :id LIMIT 1")
    suspend fun getAnalysisById(id: String): VideoAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(entity: VideoAnalysisEntity)

    @Query("DELETE FROM video_analyses WHERE id = :id")
    suspend fun deleteAnalysisById(id: String)

    @Query("SELECT * FROM upload_events ORDER BY timestampEpochMs DESC")
    fun observeAllEvents(): Flow<List<UploadEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: UploadEventEntity)

    @Query("DELETE FROM upload_events WHERE videoId = :videoId")
    suspend fun deleteEventsForVideo(videoId: String)
}

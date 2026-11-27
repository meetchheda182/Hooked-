package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VideoAnalysisEntity::class, UploadEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HookDatabase : RoomDatabase() {
    abstract fun videoAnalysisDao(): VideoAnalysisDao

    companion object {
        @Volatile
        private var instance: HookDatabase? = null

        fun getInstance(context: Context): HookDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HookDatabase::class.java,
                    "hooked_studio.db"
                ).build().also { instance = it }
            }
        }
    }
}

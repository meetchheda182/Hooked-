package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.creatorPrefsStore by preferencesDataStore(name = "hooked_creator_prefs")

class CreatorPrefsDataStore(private val context: Context) {
    private object Keys {
        val TARGET_UPLOADS_PER_WEEK = intPreferencesKey("target_uploads_per_week")
    }

    val targetUploadsPerWeek: Flow<Int> = context.creatorPrefsStore.data.map { prefs ->
        prefs[Keys.TARGET_UPLOADS_PER_WEEK] ?: 2
    }

    suspend fun setTargetUploadsPerWeek(target: Int) {
        context.creatorPrefsStore.edit { prefs ->
            prefs[Keys.TARGET_UPLOADS_PER_WEEK] = target.coerceIn(1, 7)
        }
    }
}

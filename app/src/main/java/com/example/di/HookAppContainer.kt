package com.example.di

import android.content.Context
import com.example.data.ai.HookAiService
import com.example.data.local.CreatorPrefsDataStore
import com.example.data.local.HookDatabase
import com.example.data.media.VideoMediaManager
import com.example.data.repository.HookRepository

class HookAppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database by lazy { HookDatabase.getInstance(appContext) }
    private val prefs by lazy { CreatorPrefsDataStore(appContext) }
    private val mediaManager by lazy { VideoMediaManager(appContext) }
    private val aiService by lazy { HookAiService() }

    val repository: HookRepository by lazy {
        HookRepository(
            dao = database.videoAnalysisDao(),
            prefs = prefs,
            mediaManager = mediaManager,
            aiService = aiService
        )
    }
}

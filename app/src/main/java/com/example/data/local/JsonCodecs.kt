package com.example.data.local

import com.example.domain.model.HighlightMoment
import com.example.domain.model.ThumbnailIdea
import com.example.domain.model.TitleIdea
import org.json.JSONArray
import org.json.JSONObject

object JsonCodecs {
    fun encodeHighlights(items: List<HighlightMoment>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("startMs", item.startMs)
                    put("endMs", item.endMs)
                    put("hookScore", item.hookScore)
                    put("reason", item.reason)
                    put("type", item.type)
                    if (item.exportedClipPath != null) {
                        put("exportedClipPath", item.exportedClipPath)
                    }
                }
            )
        }
        return array.toString()
    }

    fun decodeHighlights(raw: String): List<HighlightMoment> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                HighlightMoment(
                    id = obj.optString("id", "hl_$idx"),
                    title = obj.optString("title", "Highlight ${idx + 1}"),
                    startMs = obj.optLong("startMs", 0L),
                    endMs = obj.optLong("endMs", 15_000L),
                    hookScore = obj.optInt("hookScore", 80),
                    reason = obj.optString("reason", ""),
                    type = obj.optString("type", "Pattern Interrupt"),
                    exportedClipPath = obj.optString("exportedClipPath", "").ifBlank { null }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeTitles(items: List<TitleIdea>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("angle", item.angle)
                }
            )
        }
        return array.toString()
    }

    fun decodeTitles(raw: String): List<TitleIdea> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                TitleIdea(
                    id = obj.optString("id", "title_$idx"),
                    title = obj.optString("title", ""),
                    angle = obj.optString("angle", "Curiosity")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeThumbnails(items: List<ThumbnailIdea>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("conceptTitle", item.conceptTitle)
                    put("visualDescription", item.visualDescription)
                    put("textOverlay", item.textOverlay)
                    put("colorMood", item.colorMood)
                    if (item.generatedImagePath != null) {
                        put("generatedImagePath", item.generatedImagePath)
                    }
                }
            )
        }
        return array.toString()
    }

    fun decodeThumbnails(raw: String): List<ThumbnailIdea> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                ThumbnailIdea(
                    id = obj.optString("id", "thumb_$idx"),
                    conceptTitle = obj.optString("conceptTitle", "Concept ${idx + 1}"),
                    visualDescription = obj.optString("visualDescription", ""),
                    textOverlay = obj.optString("textOverlay", ""),
                    colorMood = obj.optString("colorMood", "High-Contrast"),
                    generatedImagePath = obj.optString("generatedImagePath", "").ifBlank { null }
                )
            }
        }.getOrDefault(emptyList())
    }
}

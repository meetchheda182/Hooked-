package com.example

import com.example.data.local.JsonCodecs
import com.example.domain.model.HighlightMoment
import com.example.domain.model.ThumbnailIdea
import com.example.domain.model.TitleIdea
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun jsonCodecs_roundTripHighlightsTitlesAndThumbnails() {
        val highlights = listOf(
            HighlightMoment(
                id = "hl_1",
                title = "Cold Open Hook",
                startMs = 2_000L,
                endMs = 14_000L,
                hookScore = 94,
                reason = "High-energy vocal drop with immediate visual payoff",
                type = "Pattern Interrupt",
                exportedClipPath = "/tmp/clip.mp4"
            )
        )
        val titles = listOf(
            TitleIdea(id = "t1", title = "Why 90% of Edits Fail", angle = "Contrarian")
        )
        val thumbs = listOf(
            ThumbnailIdea(
                id = "th1",
                conceptTitle = "Split Timeline Shock",
                visualDescription = "Creator holding glowing red scissor icon over dark timeline",
                textOverlay = "DON'T POST YET",
                colorMood = "Electric Coral",
                generatedImagePath = "/tmp/thumb.png"
            )
        )

        assertEquals(highlights, JsonCodecs.decodeHighlights(JsonCodecs.encodeHighlights(highlights)))
        assertEquals(titles, JsonCodecs.decodeTitles(JsonCodecs.encodeTitles(titles)))
        assertEquals(thumbs, JsonCodecs.decodeThumbnails(JsonCodecs.encodeThumbnails(thumbs)))
    }
}

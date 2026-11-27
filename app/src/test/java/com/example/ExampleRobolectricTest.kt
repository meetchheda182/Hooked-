package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JsonCodecs
import com.example.domain.model.HighlightMoment
import com.example.domain.model.ThumbnailIdea
import com.example.domain.model.TitleIdea
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hooked", appName)
    }

    @Test
    fun `json codecs round-trip highlights titles and thumbnails`() {
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
        val titles = listOf(TitleIdea(id = "t1", title = "Why 90% of Edits Fail", angle = "Contrarian"))
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

        val decodedHighlights = JsonCodecs.decodeHighlights(JsonCodecs.encodeHighlights(highlights))
        val decodedTitles = JsonCodecs.decodeTitles(JsonCodecs.encodeTitles(titles))
        val decodedThumbs = JsonCodecs.decodeThumbnails(JsonCodecs.encodeThumbnails(thumbs))

        assertEquals(highlights, decodedHighlights)
        assertEquals(titles, decodedTitles)
        assertEquals(thumbs, decodedThumbs)
    }
}

package com.example.asr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.EpisodeEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MediaPcmSourceInstrumentedTest {

    @Test
    fun `decodes the first ten seconds of a real episode`() = runTest {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val ep = EpisodeEntity(
            id = "x",
            podcastId = "p",
            podcastTitle = "",
            podcastCoverUrl = "",
            title = "",
            description = "",
            durationSeconds = 600,
            publishDate = "",
            audioUrl = "https://traffic.megaphone.fm/SCIM7156610982.mp3",
        )
        val source = MediaPcmSourceFactory(ctx, ctx.cacheDir).create(ep)
        val reader = source.reader(30_000)
        val out = FloatArray(1_600)
        var total = 0
        try {
            repeat(100) { total += reader.read(out) }
        } finally {
            reader.close()
        }
        assertTrue(total > 100_000)
        assertTrue(out.any { kotlin.math.abs(it) > 0.01f })
    }
}

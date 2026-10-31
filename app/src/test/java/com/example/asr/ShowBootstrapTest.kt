package com.example.asr

import com.example.data.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowBootstrapTest {

    private fun episode(transcript: String = "", chapters: String = "") = EpisodeEntity(
        id = "e",
        podcastId = "p",
        podcastTitle = "Show",
        podcastCoverUrl = "",
        title = "Episode",
        description = "",
        durationSeconds = 600,
        publishDate = "",
        audioUrl = "",
        transcript = transcript,
        chapters = chapters,
    )

    @Test
    fun `feed transcript yields candidate ads`() {
        val ep = episode(
            transcript = "00:00 [Host] Welcome\n" +
                "05:00 [Sponsor Break] Sponsors: Brought to you by AG1. Use code SAVE.\n" +
                "06:00 [Host] And now back to the show."
        )
        val ads = ShowBootstrap().candidates(ep)
        assertEquals(1, ads.size)
        assertEquals(300_000L, ads[0].startMs)
        assertFalse(ads[0].confirmed)
    }

    @Test
    fun `sponsor chapters yield candidates`() {
        val ep = episode(chapters = "0:Intro|300:Sponsor: AG1|360:Main topic")
        assertEquals(1, ShowBootstrap().candidates(ep).size)
    }

    @Test
    fun `empty evidence yields nothing`() {
        assertTrue(ShowBootstrap().candidates(episode()).isEmpty())
    }
}

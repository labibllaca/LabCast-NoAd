package com.example.player

import com.example.data.PodcastChapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSponsorSkipGuardTest {
    @Test
    fun sameSponsorChapterIsNotSkippedTwiceDuringOneEpisode() {
        val guard = ChapterSponsorSkipGuard()
        val chapter = PodcastChapter(id = "sponsor-1", title = "Sponsors", startTimeSeconds = 300)

        assertTrue(guard.claim("episode-1", chapter))
        assertFalse(guard.claim("episode-1", chapter))
    }

    @Test
    fun sameNamedSponsorChapterCanBeSkippedInAnotherEpisode() {
        val guard = ChapterSponsorSkipGuard()
        val chapter = PodcastChapter(id = "sponsor-1", title = "Sponsors", startTimeSeconds = 300)

        assertTrue(guard.claim("episode-1", chapter))
        assertTrue(guard.claim("episode-2", chapter))
    }
}

package com.example.player

import com.example.data.PodcastChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsorChapterSkipTargetTest {
    @Test
    fun prefersTheEndOfTheMarkedSponsorChapter() {
        val chapters = listOf(
            PodcastChapter(id = "topic", title = "Topic", startTimeSeconds = 0, durationSeconds = 300),
            PodcastChapter(id = "sponsor", title = "Sponsors", startTimeSeconds = 300, durationSeconds = 90),
            PodcastChapter(id = "next", title = "Next Topic", startTimeSeconds = 390, durationSeconds = 600)
        )

        val target = SponsorChapterSkipTarget.at(positionMs = 345_000L, chapters = chapters)

        assertEquals(390_000L, target?.targetMs)
    }

    @Test
    fun doesNotCreateSponsorTargetWhenCurrentChapterIsNotMarked() {
        val chapters = listOf(
            PodcastChapter(id = "topic", title = "Topic", startTimeSeconds = 0, durationSeconds = 300),
            PodcastChapter(id = "next", title = "Next Topic", startTimeSeconds = 300, durationSeconds = 600)
        )

        assertNull(SponsorChapterSkipTarget.at(positionMs = 120_000L, chapters = chapters))
    }
}

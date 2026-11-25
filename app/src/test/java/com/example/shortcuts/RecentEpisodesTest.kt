package com.example.shortcuts

import com.example.data.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentEpisodesTest {
    @Test
    fun ordersByLastPlayedDescendingAndCapsAtThree() {
        val episodes = listOf(ep("a", 1_000), ep("b", 3_000), ep("c", 2_000), ep("d", 4_000))

        assertEquals(listOf("d", "b", "c"), RecentEpisodes.select(episodes).map { it.id })
    }

    @Test
    fun includesFinishedEpisodes() {
        val selected = RecentEpisodes.select(listOf(ep("done", 9_000, completed = true)))

        assertTrue(selected.single().isCompleted)
    }

    @Test
    fun ignoresEpisodesThatWereNeverPlayed() {
        val selected = RecentEpisodes.select(listOf(ep("new", 0), ep("played", 5_000)))

        assertEquals(listOf("played"), selected.map { it.id })
    }

    @Test
    fun breaksTimestampTiesByIdForStableOrder() {
        assertEquals(
            listOf("a", "b"),
            RecentEpisodes.select(listOf(ep("a", 5_000), ep("b", 5_000))).map { it.id }
        )
    }

    @Test
    fun collapsesDuplicateIds() {
        val selected = RecentEpisodes.select(listOf(ep("dup", 1_000), ep("dup", 9_000)))

        assertEquals(listOf("dup"), selected.map { it.id })
    }

    @Test
    fun respectsALowerLimitAndZero() {
        val episodes = listOf(ep("a", 3_000), ep("b", 2_000), ep("c", 1_000))

        assertEquals(listOf("a"), RecentEpisodes.select(episodes, limit = 1).map { it.id })
        assertEquals(emptyList<EpisodeEntity>(), RecentEpisodes.select(episodes, limit = 0))
    }

    @Test
    fun buildsShortAndLongLabels() {
        val spec = RecentEpisodes
            .shortcutSpecs(listOf(ep("a", 1_000, title = "Deep Work", podcast = "Show")))
            .single()

        assertEquals("episode_a", spec.id)
        assertEquals("Deep Work", spec.shortLabel)
        assertEquals("Show · Deep Work", spec.longLabel)
        assertEquals("a", spec.episodeId)
    }

    @Test
    fun truncatesLongTitlesAndFallsBackForBlankTitles() {
        val blank = RecentEpisodes.shortcutSpecs(listOf(ep("a", 1_000, title = "   "))).single()
        val long = RecentEpisodes.shortcutSpecs(listOf(ep("b", 1_000, title = "x".repeat(80)))).single()
        val spaced = RecentEpisodes.shortcutSpecs(listOf(ep("c", 1_000, title = "Deep   Work"))).single()

        assertEquals("Episode", blank.shortLabel)
        assertEquals(40, long.shortLabel.length)
        assertEquals("Deep Work", spaced.shortLabel)
    }

    private fun ep(
        id: String,
        lastPlayedAt: Long,
        completed: Boolean = false,
        title: String = "Episode $id",
        podcast: String = "Show"
    ) = EpisodeEntity(
        id = id,
        podcastId = "podcast-$id",
        podcastTitle = podcast,
        podcastCoverUrl = "",
        title = title,
        description = "",
        durationSeconds = 600,
        publishDate = "2026-01-01",
        audioUrl = "https://example.invalid/$id.mp3",
        isCompleted = completed,
        lastPlayedAt = lastPlayedAt
    )
}

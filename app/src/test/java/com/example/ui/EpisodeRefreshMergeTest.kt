package com.example.ui

import com.example.data.EpisodeEntity
import com.example.data.ManualAdRange
import com.example.data.ManualAdRangeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeRefreshMergeTest {
    @Test
    fun refreshKeepsLastPlayedAt() {
        val fresh = episode(lastPlayedAt = 0L)
        val previous = fresh.copy(lastPlayedAt = 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, fresh.withPreservedStateFrom(previous).lastPlayedAt)
    }

    @Test
    fun refreshKeepsListenerState() {
        val fresh = episode(lastPlayedAt = 0L)
        val previous = fresh.copy(
            playbackPositionMs = 120_000L,
            isCompleted = true,
            isFavorite = true,
            chapters = "0:Intro|60:Topic",
            transcript = "00:00 hello",
            adTimestampsSeconds = "45,210",
            manualAdRanges = ManualAdRangeCodec.encode(listOf(ManualAdRange(10_000L, 20_000L)))
        )

        val merged = fresh.withPreservedStateFrom(previous)

        assertEquals(120_000L, merged.playbackPositionMs)
        assertTrue(merged.isCompleted)
        assertTrue(merged.isFavorite)
        assertEquals("0:Intro|60:Topic", merged.chapters)
        assertEquals("00:00 hello", merged.transcript)
        assertEquals("45,210", merged.adTimestampsSeconds)
        assertEquals(previous.manualAdRanges, merged.manualAdRanges)
    }

    @Test
    fun firstRefreshWithoutPreviousStateStaysEmpty() {
        val merged = episode(lastPlayedAt = 0L).withPreservedStateFrom(null)

        assertEquals(0L, merged.lastPlayedAt)
        assertEquals(0L, merged.playbackPositionMs)
        assertEquals("", merged.manualAdRanges)
    }

    private fun episode(lastPlayedAt: Long) = EpisodeEntity(
        id = "ep-1",
        podcastId = "podcast-1",
        podcastTitle = "Show",
        podcastCoverUrl = "",
        title = "Episode",
        description = "",
        durationSeconds = 600,
        publishDate = "2026-01-01",
        audioUrl = "https://example.invalid/a.mp3",
        lastPlayedAt = lastPlayedAt
    )
}

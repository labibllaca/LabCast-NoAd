package com.example.asr

import com.example.data.EpisodeAdSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSelectionTest {

    private fun ad(
        id: String,
        source: String,
        confirmed: Boolean = true,
        startMs: Long = 0,
        endMs: Long = 60_000,
        episodeId: String = "e",
    ) = EpisodeAdSegmentEntity(
        id = id,
        episodeId = episodeId,
        podcastId = "p",
        startMs = startMs,
        endMs = endMs,
        label = "ad",
        confidence = 1f,
        source = source,
        confirmed = confirmed,
        createdAt = 1,
    )

    @Test
    fun `asr off keeps only user marks`() {
        val all = listOf(ad("u", "USER"), ad("a", "ASR", startMs = 120_000, endMs = 180_000))
        assertEquals(listOf("u"), AdSelection.visible(all, asrEnabled = false).map { it.id })
    }

    @Test
    fun `asr on keeps every confirmed ad`() {
        val all = listOf(
            ad("u", "USER"),
            ad("a", "ASR", startMs = 120_000, endMs = 180_000),
            ad("x", "ASR", confirmed = false, startMs = 240_000, endMs = 300_000),
        )
        assertEquals(listOf("u", "a"), AdSelection.visible(all, asrEnabled = true).map { it.id })
    }

    @Test
    fun `a user mark hides a detected ad that overlaps it`() {
        val user = ad("u", "USER", startMs = 10_000, endMs = 60_000)
        val detected = ad("a", "ASR", startMs = 10_000, endMs = 100_000)
        assertEquals(listOf("u"), AdSelection.visible(listOf(user, detected), asrEnabled = true).map { it.id })
    }

    @Test
    fun `stale controller user rows are not played`() {
        val stale = ad("u", "USER")
        assertTrue(AdSelection.playback(emptyList(), listOf(stale), asrEnabled = true, episodeId = "e").isEmpty())
    }

    @Test
    fun `stored ads from another episode are ignored`() {
        val other = ad("o", "ASR", episodeId = "other")
        assertTrue(AdSelection.playback(listOf(other), listOf(other), asrEnabled = true, episodeId = "e").isEmpty())
    }
}

package com.example.asr

import com.example.data.EpisodeAdSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AdSelectionTest {

    private fun ad(id: String, source: String, confirmed: Boolean = true) = EpisodeAdSegmentEntity(
        id = id,
        episodeId = "e",
        podcastId = "p",
        startMs = 0,
        endMs = 60_000,
        label = "ad",
        confidence = 1f,
        source = source,
        confirmed = confirmed,
        createdAt = 1,
    )

    @Test
    fun `asr off keeps only user marks`() {
        val all = listOf(ad("u", "USER"), ad("a", "ASR"))
        assertEquals(listOf("u"), AdSelection.visible(all, asrEnabled = false).map { it.id })
    }

    @Test
    fun `asr on keeps every confirmed ad`() {
        val all = listOf(ad("u", "USER"), ad("a", "ASR"), ad("x", "ASR", confirmed = false))
        assertEquals(listOf("u", "a"), AdSelection.visible(all, asrEnabled = true).map { it.id })
    }
}

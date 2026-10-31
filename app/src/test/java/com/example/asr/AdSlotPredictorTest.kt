package com.example.asr

import com.example.data.AdSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSlotPredictorTest {

    private fun slot(
        anchor: Long = 300_000,
        duration: Long = 60_000,
        ratio: Float = 0.5f,
        index: Int = 0,
        enabled: Boolean = true,
        hits: Int = 1,
        misses: Int = 0,
        source: String = "ASR",
        confidence: Float = hits.toFloat() / (hits + misses + 1).toFloat(),
    ) = AdSlotEntity(
        podcastId = "p",
        slotIndex = index,
        startRatio = ratio,
        startMsAnchor = anchor,
        durationMs = duration,
        startVarianceMs = 0,
        hits = hits,
        misses = misses,
        confidence = confidence,
        source = source,
        enabled = enabled,
        updatedAt = 0,
    )

    @Test
    fun `mid-roll window is padded`() {
        val slot = slot(anchor = 300_000, duration = 60_000, ratio = 0.5f)
        val w = AdSlotPredictor().predict(listOf(slot), 600_000).single()
        assertEquals(270_000L, w.startMs)
        assertEquals(420_000L, w.endMs)
    }

    @Test
    fun `unknown duration only predicts the opening`() {
        val slots = listOf(
            slot(anchor = 0, duration = 60_000, ratio = 0f),
            slot(anchor = 300_000, duration = 60_000, ratio = 0.5f, index = 1),
        )
        val out = AdSlotPredictor().predict(slots, 0)
        assertEquals(1, out.size)
        assertEquals(0L, out[0].startMs)
    }

    @Test
    fun `disabled slots are not predicted`() {
        assertTrue(AdSlotPredictor().predict(listOf(slot(enabled = false)), 600_000).isEmpty())
    }

    @Test
    fun `low-confidence automatic slots are not predicted`() {
        assertTrue(AdSlotPredictor().predict(listOf(slot(hits = 0, misses = 2)), 600_000).isEmpty())
    }

    @Test
    fun `feed candidates are predicted for scanning only`() {
        val candidate = slot(hits = 0, source = "TRANSCRIPT", confidence = 0.5f)
        assertEquals(
            1,
            AdSlotPredictor().predict(listOf(candidate), 600_000, includeCandidates = true).size,
        )
        assertTrue(
            AdSlotPredictor().predict(listOf(candidate), 600_000, includeCandidates = false).isEmpty(),
        )
    }
}

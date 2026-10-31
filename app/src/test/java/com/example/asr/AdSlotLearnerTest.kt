package com.example.asr

import com.example.data.AdSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSlotLearnerTest {
    private val learner = AdSlotLearner()

    private fun ad(startMs: Long, endMs: Long) = DetectedAd(
        startMs = startMs,
        endMs = endMs,
        label = "AG1",
        confidence = 0.95f,
        startCue = "sponsored by",
        endCue = "back to the show",
        confirmed = true,
        source = AdSource.ASR,
    )

    @Test
    fun `two detections merge into one slot`() {
        val first = learner.learn(emptyList(), "p", 600_000, listOf(ad(300_000, 360_000)))
        val second = learner.learn(first, "p", 620_000, listOf(ad(305_000, 365_000)))
        assertEquals(1, second.size)
        assertEquals(2, second[0].hits)
        assertEquals(302_500L, second[0].startMsAnchor)
    }

    @Test
    fun `pre-roll is anchored at zero`() {
        val slots = learner.learn(emptyList(), "p", 600_000, listOf(ad(0, 60_000)))
        assertEquals(0L, slots[0].startMsAnchor)
        assertEquals(0f, slots[0].startRatio)
        assertEquals(0, slots[0].slotIndex)
    }

    @Test
    fun `three misses disable a slot`() {
        var slots = learner.learn(emptyList(), "p", 600_000, listOf(ad(300_000, 360_000)))
        repeat(3) { slots = learner.recordMiss(slots, 0) }
        assertFalse(slots[0].enabled)
        assertTrue(slots[0].misses >= 3)
    }

    @Test
    fun `user boundaries are frozen while hits still update`() {
        val user = learner.learn(emptyList(), "p", 600_000, listOf(ad(300_000, 360_000)))
            .map { it.copy(source = AdSource.USER.name, startMsAnchor = 299_000) }
        val updated = learner.learn(user, "p", 600_000, listOf(ad(310_000, 370_000)))
        assertEquals(299_000L, updated[0].startMsAnchor)
        assertEquals(2, updated[0].hits)
    }

    @Test
    fun `confidence starts at half after one hit`() {
        assertEquals(0.5f, learner.confidence(1, 0))
        assertEquals(2f / 3f, learner.confidence(2, 0), 0.001f)
    }
}

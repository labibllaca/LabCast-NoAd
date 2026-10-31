package com.example.asr

import com.example.data.AdSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotEditReducerTest {

    private fun slot(
        anchor: Long = 300_000,
        duration: Long = 60_000,
        enabled: Boolean = true,
    ) = AdSlotEntity(
        podcastId = "p",
        slotIndex = 0,
        startRatio = 0.5f,
        startMsAnchor = anchor,
        durationMs = duration,
        startVarianceMs = 0,
        hits = 1,
        misses = 0,
        confidence = 0.5f,
        source = "ASR",
        enabled = enabled,
        updatedAt = 0,
    )

    @Test
    fun `edit marks the slot as user and freezes the new boundary`() {
        val e = SlotEditReducer.edit(slot(), 310_000, 30_000, 600_000)
        assertEquals("USER", e.source)
        assertEquals(310_000L, e.startMsAnchor)
        assertEquals(30_000L, e.durationMs)
        assertTrue(e.enabled)
    }

    @Test
    fun `edit clamps to the episode`() {
        val e = SlotEditReducer.edit(slot(), 590_000, 30_000, 600_000)
        assertEquals(590_000L, e.startMsAnchor)
        assertEquals(10_000L, e.durationMs)
    }

    @Test
    fun `toggle flips enabled`() {
        assertFalse(SlotEditReducer.toggle(slot(enabled = true)).enabled)
    }
}

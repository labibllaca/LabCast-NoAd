package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualAdMarkTest {
    @Test
    fun `arm then close produces a range`() {
        val armed = ManualAdMarker.arm(10_000)
        val result = ManualAdMarker.close(armed, 70_000, 600_000)
        assertEquals(AdMarkResult.Closed(ManualAdRange(10_000, 70_000)), result)
    }

    @Test
    fun `shorter than five seconds is rejected`() {
        assertEquals(
            AdMarkResult.RejectedTooShort,
            ManualAdMarker.close(ManualAdMarker.arm(10_000), 13_000, 600_000),
        )
    }

    @Test
    fun `end before start is rejected without losing the arm`() {
        assertEquals(
            AdMarkResult.RejectedOrder,
            ManualAdMarker.close(ManualAdMarker.arm(10_000), 4_000, 600_000),
        )
    }

    @Test
    fun `end is clamped to the episode duration`() {
        val result = ManualAdMarker.close(ManualAdMarker.arm(590_000), 610_000, 600_000)
        assertEquals(AdMarkResult.Closed(ManualAdRange(590_000, 600_000)), result)
    }

    @Test
    fun `longer than fifteen minutes still closes`() {
        val result = ManualAdMarker.close(ManualAdMarker.arm(0), 1_000_000, 1_200_000)
        assertTrue(result is AdMarkResult.Closed)
    }

    @Test
    fun `entity uses user source and confirmed`() {
        val e = ManualAdMarker.toEntity("ep", "pod", ManualAdRange(10_000, 70_000))
        assertEquals("ep:10000:70000", e.id)
        assertEquals("USER", e.source)
        assertTrue(e.confirmed)
        assertEquals(1f, e.confidence, 0f)
    }

    @Test
    fun `nudge moves one boundary`() {
        val e = ManualAdMarker.toEntity("ep", "pod", ManualAdRange(10_000, 70_000))
        val end = ManualAdMarker.nudge(e, moveStart = false, deltaMs = 1_000, durationMs = 600_000)!!
        assertEquals(71_000, end.endMs)
        assertEquals(10_000, end.startMs)
    }

    @Test
    fun `nudge refuses to shrink below five seconds or cross the edges`() {
        val e = ManualAdMarker.toEntity("ep", "pod", ManualAdRange(10_000, 70_000))
        assertNull(ManualAdMarker.nudge(e, moveStart = true, deltaMs = 66_000, durationMs = 600_000))
        assertNull(ManualAdMarker.nudge(e, moveStart = true, deltaMs = -11_000, durationMs = 600_000))
        assertNull(ManualAdMarker.nudge(e, moveStart = false, deltaMs = 600_000, durationMs = 600_000))
    }

    @Test
    fun `duration limit follows the live playhead when the feed is too short`() {
        assertEquals(1_900_000L, ManualAdMarker.durationLimit(1_800_000L, 1_900_000L))
        assertEquals(1_800_000L, ManualAdMarker.durationLimit(1_800_000L, 1_700_000L))
    }
}

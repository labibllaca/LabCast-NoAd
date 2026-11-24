package com.example.player

import com.example.data.ManualAdRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualAdMarkSessionTest {
    @Test
    fun capturesStartAndEndAndClearsPendingMark() {
        val session = ManualAdMarkSession()

        assertTrue(session.start(10_000L))
        assertEquals(10_000L, session.pendingStartMs)
        assertEquals(ManualAdRange(10_000L, 25_000L), session.finish(25_000L, 100_000L))
        assertNull(session.pendingStartMs)
    }

    @Test
    fun cancelAndEpisodeResetDiscardPendingMark() {
        val session = ManualAdMarkSession()
        session.start(10_000L)
        session.cancel()
        assertNull(session.pendingStartMs)

        session.start(20_000L)
        session.reset()
        assertNull(session.pendingStartMs)
    }

    @Test
    fun rejectsEndBeforeStartAndClampsToEpisodeDuration() {
        val session = ManualAdMarkSession()
        session.start(30_000L)
        assertNull(session.finish(20_000L, 100_000L))

        session.start(80_000L)
        assertEquals(ManualAdRange(80_000L, 100_000L), session.finish(120_000L, 100_000L))
        assertFalse(session.start(-1L))
    }
}

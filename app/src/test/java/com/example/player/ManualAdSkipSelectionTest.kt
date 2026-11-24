package com.example.player

import com.example.data.ManualAdRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualAdSkipSelectionTest {
    @Test
    fun selectsExactSavedRangeWhilePlaybackIsInsideIt() {
        val interval = ManualAdSkipSelection.activeAt(
            episodeId = "episode-1",
            ranges = listOf(ManualAdRange(10_000L, 25_000L)),
            durationMs = 100_000L,
            positionMs = 15_000L
        )

        assertEquals(10_000L, interval?.startMs)
        assertEquals(25_000L, interval?.endMs)
        assertEquals("Manual ad mark", interval?.triggerWord)
    }

    @Test
    fun doesNotSelectSavedRangeOutsideItsBounds() {
        assertNull(
            ManualAdSkipSelection.activeAt(
                episodeId = "episode-1",
                ranges = listOf(ManualAdRange(10_000L, 25_000L)),
                durationMs = 100_000L,
                positionMs = 25_000L
            )
        )
    }
}

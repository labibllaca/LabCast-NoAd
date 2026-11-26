package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VozScanPlannerTest {
    @Test
    fun openingWindowStartsAtZero() {
        val w = VozScanPlanner.plan(3_600_000L, emptyList())
        assertEquals(0L, w.first().startMs)
        assertTrue(w.any { it.startMs == 0L && it.endMs == 60_000L })
    }

    @Test
    fun candidateIsPaddedAndSplitIntoWindows() {
        val w = VozScanPlanner.plan(3_600_000L, listOf(600_000L..660_000L))
        val first = w.first { it.startMs in 540_000L..575_000L }
        assertEquals(570_000L, first.startMs)
        assertTrue(w.any { it.startMs == 627_000L })
    }

    @Test
    fun overlappingCandidatesAreMerged() {
        val w = VozScanPlanner.plan(3_600_000L, listOf(600_000L..660_000L, 640_000L..700_000L))
        assertFalse(w.any { it.startMs == 610_000L })
        assertTrue(w.any { it.startMs == 627_000L })
    }

    @Test
    fun windowsAreClampedToEpisodeEnd() {
        val w = VozScanPlanner.plan(30_000L, listOf(10_000L..25_000L))
        assertTrue(w.all { it.endMs <= 30_000L })
    }

    @Test
    fun coverageStaysUnderTwentyPercentAndCapIsTwelve() {
        val many = (0..20).map { (it * 100_000L)..(it * 100_000L + 10_000L) }
        val w = VozScanPlanner.plan(3_600_000L, many)
        assertTrue(w.size <= VozScanPlanner.MAX_WINDOWS)
        assertTrue(w.sumOf { it.durationMs } <= 3_600_000L * 0.2)
    }
}

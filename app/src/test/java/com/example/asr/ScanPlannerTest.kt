package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanPlannerTest {

    @Test
    fun `full scan starts with opening and look-ahead`() {
        val ranges = ScanPlanner()
            .plan(ScanMode.BOOTSTRAP_FULL, 3_600_000, 1_200_000, emptyList(), emptyList())
        assertEquals(0L, ranges.first().startMs)
        assertEquals(1_380_000L, ranges.first().endMs)
    }

    @Test
    fun `windowed scan plans opening plus windows and subtracts covered`() {
        val windows = listOf(PredictedWindow(1, 270_000, 420_000, true))
        val ranges = ScanPlanner()
            .plan(ScanMode.WINDOWED, 600_000, 0, windows, listOf(0L..120_000L))
        assertEquals(1, ranges.size)
        assertEquals(270_000L, ranges[0].startMs)
        assertEquals(420_000L, ranges[0].endMs)
    }

    @Test
    fun `overlapping windows merge`() {
        val windows = listOf(
            PredictedWindow(1, 270_000, 420_000, true),
            PredictedWindow(2, 400_000, 500_000, true),
        )
        val ranges = ScanPlanner()
            .plan(ScanMode.WINDOWED, 900_000, 0, windows, listOf(0L..120_000L))
        assertEquals(1, ranges.size)
        assertEquals(500_000L, ranges[0].endMs)
    }
}

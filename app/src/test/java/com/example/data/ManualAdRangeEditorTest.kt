package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualAdRangeEditorTest {

    @Test
    fun nudgeStartBelowZeroClampsAtZero() {
        val target = ManualAdRange(2_000L, 30_000L)

        val result = ManualAdRangeEditor.nudge(
            ranges = listOf(target),
            target = target,
            isStart = true,
            deltaMs = -5_000L,
            durationMs = 100_000L
        )

        assertEquals(listOf(ManualAdRange(0L, 30_000L)), result)
    }

    @Test
    fun nudgeEndBeyondDurationClampsAtDuration() {
        val target = ManualAdRange(10_000L, 98_000L)

        val result = ManualAdRangeEditor.nudge(
            ranges = listOf(target),
            target = target,
            isStart = false,
            deltaMs = 5_000L,
            durationMs = 100_000L
        )

        assertEquals(listOf(ManualAdRange(10_000L, 100_000L)), result)
    }

    @Test
    fun nudgeThatCollapsesRangeLeavesListUnchanged() {
        val target = ManualAdRange(10_000L, 11_000L)
        val ranges = listOf(target, ManualAdRange(40_000L, 50_000L))

        val result = ManualAdRangeEditor.nudge(
            ranges = ranges,
            target = target,
            isStart = false,
            deltaMs = -2_000L,
            durationMs = 100_000L
        )

        assertEquals(ranges, result)
    }

    @Test
    fun removeDropsOnlyTheExactTarget() {
        val first = ManualAdRange(10_000L, 20_000L)
        val second = ManualAdRange(20_000L, 30_000L)

        val result = ManualAdRangeEditor.remove(listOf(first, second), first)

        assertEquals(listOf(second), result)
    }

    @Test
    fun nudgePreservesOrderAndDeduplicates() {
        val target = ManualAdRange(10_000L, 20_000L)
        val other = ManualAdRange(30_000L, 40_000L)

        val result = ManualAdRangeEditor.nudge(
            ranges = listOf(target, target, other),
            target = target,
            isStart = false,
            deltaMs = 1_000L,
            durationMs = 100_000L
        )

        assertEquals(listOf(ManualAdRange(10_000L, 21_000L), other), result)
    }
}

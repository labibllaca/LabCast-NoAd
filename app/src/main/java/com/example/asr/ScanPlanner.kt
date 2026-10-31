package com.example.asr

import kotlin.math.max
import kotlin.math.min

/** A contiguous range the scanner should transcribe, with lower priority first. */
data class ScanRange(val startMs: Long, val endMs: Long, val priority: Int)

/**
 * Plans which audio ranges to transcribe.
 *
 * A full scan covers the whole episode but starts with the opening and the
 * look-ahead window around the playhead. A windowed scan covers the opening
 * plus the predicted ad windows. Already-covered ranges are removed so a seek
 * or a resumed scan never decodes the same audio twice.
 */
class ScanPlanner {

    fun plan(
        mode: ScanMode,
        durationMs: Long,
        playheadMs: Long,
        windows: List<PredictedWindow>,
        covered: List<LongRange>,
        lookAheadMs: Long = LOOK_AHEAD_MS,
    ): List<ScanRange> {
        val safeDuration = if (durationMs > 0L) durationMs else OPENING_SCAN_MS
        val base = when (mode) {
            ScanMode.BOOTSTRAP_FULL, ScanMode.FULL_MANUAL -> {
                val split = (playheadMs + lookAheadMs).coerceIn(0L, safeDuration)
                buildList {
                    if (split > 0L) add(0L..split)
                    if (split < safeDuration) add(split..safeDuration)
                }
            }
            ScanMode.WINDOWED -> buildList {
                add(0L..min(OPENING_SCAN_MS, safeDuration))
                windows.forEach { add(max(0L, it.startMs)..min(safeDuration, it.endMs)) }
            }
        }

        val merged = when (mode) {
            ScanMode.WINDOWED -> merge(base)
            ScanMode.BOOTSTRAP_FULL, ScanMode.FULL_MANUAL -> base.sortedBy { it.first }
        }
        val remaining = merged
            .flatMap { subtract(it, covered) }
            .filter { it.last > it.first }

        return remaining
            .map { range ->
                ScanRange(
                    startMs = range.first,
                    endMs = range.last,
                    priority = priority(range, playheadMs, lookAheadMs),
                )
            }
            .sortedWith(compareBy({ it.priority }, { it.startMs }))
    }

    private fun priority(range: LongRange, playheadMs: Long, lookAheadMs: Long): Int {
        val ahead = playheadMs..(playheadMs + lookAheadMs)
        return when {
            intersects(range, ahead) -> 0
            intersects(range, 0L..OPENING_SCAN_MS) -> 1
            else -> 2
        }
    }

    private fun intersects(a: LongRange, b: LongRange): Boolean {
        return a.first <= b.last && b.first <= a.last
    }

    private fun merge(ranges: List<LongRange>): List<LongRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<LongRange>()
        sorted.forEach { range ->
            val last = merged.lastOrNull()
            if (last == null || range.first > last.last) {
                merged += range
            } else {
                merged[merged.lastIndex] = last.first..max(last.last, range.last)
            }
        }
        return merged
    }

    private fun subtract(range: LongRange, covered: List<LongRange>): List<LongRange> {
        val result = mutableListOf<LongRange>()
        var start = range.first
        val end = range.last
        covered.sortedBy { it.first }.forEach { c ->
            if (c.last < start || c.first > end) return@forEach
            if (c.first > start) result += start..(c.first - 1)
            start = max(start, c.last + 1)
        }
        if (start <= end) result += start..end
        return result
    }

    companion object {
        const val LOOK_AHEAD_MS = 180_000L
        const val OPENING_SCAN_MS = 120_000L
    }
}

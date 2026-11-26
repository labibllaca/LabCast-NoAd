package com.example.player

/** A bounded slice of episode audio to transcribe, in milliseconds from the start. */
data class ScanWindow(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/** Picks the small set of windows worth listening to, never the whole episode. */
object VozScanPlanner {
    const val WINDOW_MS: Long = 60_000L
    const val OVERLAP_MS: Long = 3_000L
    const val OPENING_MS: Long = 180_000L
    const val POST_ROLL_MS: Long = 120_000L
    const val PAD_BEFORE_MS: Long = 30_000L
    const val PAD_AFTER_MS: Long = 60_000L
    const val MAX_WINDOWS: Int = 12

    fun plan(durationMs: Long, candidates: List<LongRange>): List<ScanWindow> {
        if (durationMs <= 0L) return emptyList()

        val opening = 0L..minOf(OPENING_MS, durationMs)
        val padded: List<LongRange> = candidates.map { range ->
            maxOf(0L, range.first - PAD_BEFORE_MS)..minOf(durationMs, range.last + PAD_AFTER_MS)
        }.filter { it.first < it.last }
        val postRoll: LongRange = maxOf(0L, durationMs - POST_ROLL_MS)..durationMs

        val ranges: List<LongRange> = listOf<LongRange>(opening) + padded + listOf<LongRange>(postRoll)
        val windows = mergeRanges(ranges)
            .flatMap { split(it) }
            .sortedBy { it.startMs }

        var kept = windows.take(MAX_WINDOWS)
        while (kept.size > 1 && kept.sumOf { it.durationMs } > durationMs * 0.2) {
            kept = kept.dropLast(1)
        }
        return kept
    }

    private fun mergeRanges(ranges: List<LongRange>): List<LongRange> {
        val merged = mutableListOf<LongRange>()
        ranges.sortedBy { it.first }.forEach { range ->
            val last = merged.lastOrNull()
            if (last == null || range.first > last.last) {
                merged.add(range)
            } else {
                merged[merged.size - 1] = last.first..maxOf(last.last, range.last)
            }
        }
        return merged
    }

    private fun split(range: LongRange): List<ScanWindow> {
        val step = WINDOW_MS - OVERLAP_MS
        val windows = mutableListOf<ScanWindow>()
        var start = range.first
        while (start < range.last) {
            val end = minOf(start + WINDOW_MS, range.last)
            windows.add(ScanWindow(start, end))
            if (end == range.last) break
            start += step
        }
        return windows
    }
}

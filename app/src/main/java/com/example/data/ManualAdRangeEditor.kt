package com.example.data

/** Pure edits to a saved list of manual ad ranges. */
object ManualAdRangeEditor {

    fun nudge(
        ranges: List<ManualAdRange>,
        target: ManualAdRange,
        isStart: Boolean,
        deltaMs: Long,
        durationMs: Long
    ): List<ManualAdRange> {
        if (target !in ranges) return ranges

        val moved = if (isStart) {
            ManualAdRange((target.startMs + deltaMs).coerceAtLeast(0L), target.endMs)
        } else {
            ManualAdRange(target.startMs, (target.endMs + deltaMs).coerceAtLeast(0L))
        }
        val clamped = ManualAdRangeCodec.clamp(moved, durationMs) ?: return ranges

        return ranges.map { if (it == target) clamped else it }.distinct()
    }

    fun remove(ranges: List<ManualAdRange>, target: ManualAdRange): List<ManualAdRange> =
        ranges.filterNot { it == target }
}

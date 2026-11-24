package com.example.player

import com.example.data.ManualAdRange
import com.example.data.ManualAdRangeCodec

class ManualAdMarkSession {
    var pendingStartMs: Long? = null
        private set

    fun start(positionMs: Long): Boolean {
        if (positionMs < 0L || pendingStartMs != null) return false
        pendingStartMs = positionMs
        return true
    }

    fun finish(positionMs: Long, durationMs: Long): ManualAdRange? {
        val startMs = pendingStartMs ?: return null
        pendingStartMs = null
        return ManualAdRangeCodec.clamp(ManualAdRange(startMs, positionMs), durationMs)
    }

    fun cancel() {
        pendingStartMs = null
    }

    fun reset() = cancel()
}

object ManualAdSkipSelection {
    fun intervals(
        episodeId: String,
        ranges: List<ManualAdRange>,
        durationMs: Long
    ): List<AdSkipInterval> = ranges.mapNotNull { range ->
        val validRange = ManualAdRangeCodec.clamp(range, durationMs) ?: return@mapNotNull null
        AdSkipInterval(
            id = "manual_${episodeId}_${validRange.startMs}_${validRange.endMs}",
            startMs = validRange.startMs,
            endMs = validRange.endMs,
            triggerWord = "Manual ad mark",
            resumeWord = "Marked ad end",
            confidence = 1f,
            source = "Manual User Mark",
            description = "Manually marked ad range"
        )
    }

    fun activeAt(
        episodeId: String,
        ranges: List<ManualAdRange>,
        durationMs: Long,
        positionMs: Long
    ): AdSkipInterval? = intervals(episodeId, ranges, durationMs)
        .firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
}

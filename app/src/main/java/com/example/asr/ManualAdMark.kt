package com.example.asr

import com.example.data.EpisodeAdSegmentEntity

/** In-memory arm/close state for one manual ad mark; never persisted. */
data class AdMarkState(val startMs: Long? = null) {
    val isArmed: Boolean get() = startMs != null
}

sealed class AdMarkResult {
    data class Armed(val startMs: Long) : AdMarkResult()
    data class Closed(val range: ManualAdRange) : AdMarkResult()
    data object RejectedTooShort : AdMarkResult()
    data object RejectedOrder : AdMarkResult()
}

data class ManualAdRange(val startMs: Long, val endMs: Long)

/**
 * Pure rules for the two-press manual mark.
 *
 * A mark is a user assertion, not a detection: it is always confirmed, and its
 * boundaries are whatever the playhead said, corrected by [nudge].
 */
object ManualAdMarker {
    const val MIN_DURATION_MS = 5_000L
    const val MAX_DURATION_MS = 900_000L
    const val LABEL = "Manual mark"
    const val SOURCE = "USER"

    fun arm(positionMs: Long): AdMarkState = AdMarkState(positionMs)

    fun cancel(): AdMarkState = AdMarkState()

    /**
     * The boundary limit for a new mark or nudge.
     *
     * Feed durations are often wrong and dynamic ad insertion can make the
     * audio longer, so a boundary the listener has actually reached wins.
     */
    fun durationLimit(feedDurationMs: Long, positionMs: Long): Long =
        maxOf(feedDurationMs, positionMs)

    fun close(state: AdMarkState, positionMs: Long, durationMs: Long): AdMarkResult {
        val start = state.startMs ?: return AdMarkResult.RejectedOrder
        val end = positionMs.coerceAtMost(durationMs)
        if (end <= start) return AdMarkResult.RejectedOrder
        if (end - start < MIN_DURATION_MS) return AdMarkResult.RejectedTooShort
        return AdMarkResult.Closed(ManualAdRange(start, end))
    }

    fun toEntity(
        episodeId: String,
        podcastId: String,
        range: ManualAdRange,
    ): EpisodeAdSegmentEntity = EpisodeAdSegmentEntity(
        id = idFor(episodeId, range.startMs, range.endMs),
        episodeId = episodeId,
        podcastId = podcastId,
        startMs = range.startMs,
        endMs = range.endMs,
        label = LABEL,
        confidence = 1f,
        source = SOURCE,
        confirmed = true,
        createdAt = System.currentTimeMillis(),
    )

    fun nudge(
        segment: EpisodeAdSegmentEntity,
        moveStart: Boolean,
        deltaMs: Long,
        durationMs: Long,
    ): EpisodeAdSegmentEntity? {
        val start = if (moveStart) segment.startMs + deltaMs else segment.startMs
        val end = if (moveStart) segment.endMs else segment.endMs + deltaMs
        if (start < 0L || end > durationMs || end - start < MIN_DURATION_MS) return null
        return segment.copy(
            id = idFor(segment.episodeId, start, end),
            startMs = start,
            endMs = end,
        )
    }

    private fun idFor(episodeId: String, startMs: Long, endMs: Long): String =
        "$episodeId:$startMs:$endMs"
}

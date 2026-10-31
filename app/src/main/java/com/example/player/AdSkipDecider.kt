package com.example.player

import com.example.data.EpisodeAdSegmentEntity

sealed class SkipDecision {
    data class Skip(val ad: EpisodeAdSegmentEntity) : SkipDecision()
    data class AwaitEnd(val startMs: Long) : SkipDecision()
    object None : SkipDecision()
}

/**
 * Pure skip decision. Only a confirmed segment may move the playhead.
 */
class AdSkipDecider {

    fun decide(
        positionMs: Long,
        ads: List<EpisodeAdSegmentEntity>,
        autoSkip: Boolean,
    ): SkipDecision {
        if (!autoSkip) return SkipDecision.None

        val active = ads.firstOrNull { ad ->
            positionMs >= ad.startMs && positionMs < ad.endMs
        } ?: return SkipDecision.None

        if (!active.confirmed) return SkipDecision.AwaitEnd(active.startMs)

        return if (positionMs < active.endMs - END_GUARD_MS) {
            SkipDecision.Skip(active)
        } else {
            SkipDecision.None
        }
    }

    private companion object {
        const val END_GUARD_MS = 1_000L
    }
}

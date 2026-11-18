package com.example.asr

import com.example.data.AdSlotEntity
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns confirmed detections into per-show ad slots.
 *
 * Slots are learned from evidence only. A user-edited slot keeps its boundaries
 * while its hit counter still moves; a user-disabled slot is never re-enabled
 * by learning.
 */
class AdSlotLearner {

    fun learn(
        existing: List<AdSlotEntity>,
        podcastId: String,
        durationMs: Long,
        ads: List<DetectedAd>,
    ): List<AdSlotEntity> {
        val working = existing.toMutableList()

        ads.filter { it.confirmed }.forEach { ad ->
            val adDuration = (ad.endMs - ad.startMs).coerceAtLeast(1L)
            val ratio = if (durationMs > 0L) ad.startMs.toFloat() / durationMs.toFloat() else 0f
            val isPreRoll = ad.startMs < PRE_ROLL_MAX_START_MS && ratio < PRE_ROLL_MAX_RATIO
            val anchor = if (isPreRoll) 0L else ad.startMs
            val normalizedRatio = if (isPreRoll) 0f else ratio

            val matchIndex = working.indexOfFirst { slot ->
                abs(anchor - slot.startMsAnchor) <= max(MATCH_ABS_MS, (durationMs * MATCH_RATIO).toLong()) ||
                    abs(normalizedRatio - slot.startRatio) <= MATCH_RATIO
            }

            if (matchIndex >= 0) {
                val slot = working[matchIndex]
                val newHits = slot.hits + 1
                if (slot.source == AdSource.USER.name) {
                    working[matchIndex] = slot.copy(
                        hits = newHits,
                        confidence = confidence(newHits, slot.misses),
                        updatedAt = System.currentTimeMillis(),
                    )
                } else {
                    val previousHits = slot.hits.toLong()
                    val newAnchor = ((slot.startMsAnchor * previousHits) + anchor) / newHits
                    val newDuration = ((slot.durationMs * previousHits) + adDuration) / newHits
                    val newRatio = ((slot.startRatio * previousHits) + normalizedRatio) / newHits
                    val newVariance = ((slot.startVarianceMs * previousHits) + abs(anchor - newAnchor)) / newHits
                    working[matchIndex] = slot.copy(
                        startMsAnchor = newAnchor,
                        durationMs = newDuration,
                        startRatio = newRatio,
                        startVarianceMs = newVariance,
                        hits = newHits,
                        confidence = confidence(newHits, slot.misses),
                        source = AdSource.ASR.name,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            } else {
                working += AdSlotEntity(
                    podcastId = podcastId,
                    slotIndex = working.size,
                    startRatio = normalizedRatio,
                    startMsAnchor = anchor,
                    durationMs = adDuration,
                    startVarianceMs = 0L,
                    hits = 1,
                    misses = 0,
                    confidence = confidence(1, 0),
                    source = AdSource.ASR.name,
                    enabled = true,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

        return working
            .sortedBy { it.startMsAnchor }
            .mapIndexed { index, slot -> slot.copy(slotIndex = index) }
    }

    fun recordMiss(slots: List<AdSlotEntity>, slotIndex: Int): List<AdSlotEntity> {
        return slots.map { slot ->
            if (slot.slotIndex != slotIndex) return@map slot
            val misses = slot.misses + 1
            slot.copy(
                misses = misses,
                confidence = confidence(slot.hits, misses),
                enabled = slot.enabled && misses < MISS_LIMIT,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun invalidateAll(slots: List<AdSlotEntity>): List<AdSlotEntity> {
        return slots.map { slot ->
            if (slot.source == AdSource.USER.name) slot else slot.copy(enabled = false)
        }
    }

    fun confidence(hits: Int, misses: Int): Float {
        return hits.toFloat() / (hits + misses + 1).toFloat()
    }

    private companion object {
        const val MATCH_ABS_MS = 45_000L
        const val MATCH_RATIO = 0.02f
        const val PRE_ROLL_MAX_START_MS = 90_000L
        const val PRE_ROLL_MAX_RATIO = 0.03f
        const val MISS_LIMIT = 3
    }
}

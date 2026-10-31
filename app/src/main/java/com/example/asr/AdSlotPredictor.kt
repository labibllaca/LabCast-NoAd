package com.example.asr

import com.example.data.AdSlotEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** A padded region around a predicted ad slot that the scanner should transcribe. */
data class PredictedWindow(
    val slotIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val confirmed: Boolean,
)

/**
 * Turns learned slots into scan windows.
 *
 * A slot is only predicted when it has enough evidence. Feed candidates have no
 * hits yet, so they are scanned but never skippable until an ASR cue confirms
 * them (`confirmed = false`).
 */
class AdSlotPredictor {

    fun predict(
        slots: List<AdSlotEntity>,
        durationMs: Long,
        includeCandidates: Boolean = true,
    ): List<PredictedWindow> {
        val windows = mutableListOf<PredictedWindow>()

        slots.filter { it.enabled }.forEach { slot ->
            val isCandidate = slot.hits == 0 &&
                (slot.source == AdSource.TRANSCRIPT.name || slot.source == AdSource.CHAPTER.name)

            if (isCandidate && !includeCandidates) return@forEach
            if (!isCandidate && slot.confidence < MIN_CONFIDENCE) return@forEach

            val isPreRoll = slot.startMsAnchor < PRE_ROLL_MAX_START_MS &&
                slot.startRatio < PRE_ROLL_MAX_RATIO

            if (durationMs <= 0L) {
                if (!isPreRoll) return@forEach
                windows += PredictedWindow(
                    slotIndex = slot.slotIndex,
                    startMs = 0L,
                    endMs = OPENING_SCAN_MS,
                    confirmed = false,
                )
                return@forEach
            }

            val predictedStart = if (isPreRoll) {
                0L
            } else {
                val byRatio = slot.startRatio * durationMs
                (byRatio * 0.6f + slot.startMsAnchor * 0.4f).roundToLong()
            }.coerceIn(0L, durationMs)

            val start = (predictedStart - BEFORE_PADDING_MS).coerceAtLeast(0L)
            val end = min(
                durationMs,
                max(predictedStart + slot.durationMs + AFTER_PADDING_MS, start + MIN_WINDOW_MS),
            )
            if (end <= start) return@forEach

            windows += PredictedWindow(
                slotIndex = slot.slotIndex,
                startMs = start,
                endMs = end,
                confirmed = !isCandidate && slot.hits > 0,
            )
        }

        return windows.sortedBy { it.startMs }
    }

    companion object {
        const val MIN_CONFIDENCE = 0.5f
        const val OPENING_SCAN_MS = 120_000L
        const val BEFORE_PADDING_MS = 30_000L
        const val AFTER_PADDING_MS = 60_000L
        const val MIN_WINDOW_MS = 120_000L
        private const val PRE_ROLL_MAX_START_MS = 90_000L
        private const val PRE_ROLL_MAX_RATIO = 0.03f
    }
}

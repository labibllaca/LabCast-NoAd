package com.example.asr

import com.example.data.AdSlotEntity
import kotlin.math.max

/** Pure slot-edit rules for the per-show slot editor. */
object SlotEditReducer {

    fun edit(
        slot: AdSlotEntity,
        startMs: Long,
        durationMs: Long,
        episodeDurationMs: Long,
    ): AdSlotEntity {
        val safeDuration = episodeDurationMs.coerceAtLeast(1L)
        val start = startMs.coerceIn(0L, safeDuration)
        val maxDuration = (safeDuration - start).coerceAtLeast(1L)
        val duration = durationMs.coerceIn(1L, maxDuration)
        return slot.copy(
            startMsAnchor = start,
            startRatio = start.toFloat() / safeDuration.toFloat(),
            durationMs = duration,
            source = AdSource.USER.name,
            enabled = true,
            confidence = max(slot.confidence, 0.9f),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun toggle(slot: AdSlotEntity): AdSlotEntity {
        return slot.copy(enabled = !slot.enabled, updatedAt = System.currentTimeMillis())
    }

    fun delete(slots: List<AdSlotEntity>, slotIndex: Int): List<AdSlotEntity> {
        return slots
            .filterNot { it.slotIndex == slotIndex }
            .sortedBy { it.startMsAnchor }
            .mapIndexed { index, slot -> slot.copy(slotIndex = index) }
    }
}

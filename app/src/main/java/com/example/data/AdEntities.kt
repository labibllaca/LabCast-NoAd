package com.example.data

import androidx.room.Entity

/**
 * A learned ad slot for one podcast show.
 *
 * `startRatio` is the position relative to episode duration; `startMsAnchor`
 * is the observed absolute start. Both are kept so prediction survives
 * episodes of different lengths.
 */
@Entity(tableName = "ad_slots", primaryKeys = ["podcastId", "slotIndex"])
data class AdSlotEntity(
    val podcastId: String,
    val slotIndex: Int,
    val startRatio: Float,
    val startMsAnchor: Long,
    val durationMs: Long,
    val startVarianceMs: Long,
    val hits: Int,
    val misses: Int,
    val confidence: Float,
    val source: String,
    val enabled: Boolean,
    val updatedAt: Long,
)

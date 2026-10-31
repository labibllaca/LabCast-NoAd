package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

/** An exact or candidate ad segment detected in one episode. */
@Entity(tableName = "episode_ad_segments", indices = [Index("episodeId")])
data class EpisodeAdSegmentEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val podcastId: String,
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val confidence: Float,
    val source: String,
    val confirmed: Boolean,
    val createdAt: Long,
)

/** A recognized speech segment. This is the source of truth for resume/seek. */
@Entity(
    tableName = "episode_asr_segments",
    indices = [Index(value = ["episodeId", "startMs"])],
)
data class EpisodeAsrSegmentEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float,
    val source: String,
    val createdAt: Long,
)

/** Bookkeeping for one episode's scan: mode, status, covered ranges, duration. */
@Entity(tableName = "episode_scans")
data class EpisodeScanEntity(
    @PrimaryKey val episodeId: String,
    val mode: String,
    val status: String,
    val coveredRanges: String,
    val modelVersion: String,
    val actualDurationMs: Long,
    val durationSource: String,
    val updatedAt: Long,
)

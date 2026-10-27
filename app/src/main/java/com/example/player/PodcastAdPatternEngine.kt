package com.example.player

import com.example.data.EpisodeEntity
import com.example.data.TranscriptParser
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Show-Level Historical Ad Pattern Learning & Prediction Engine.
 *
 * Analyzes historical episodes across a podcast show to learn recurring ad placement
 * templates (Pre-roll, Mid-roll #1, Mid-roll #2, Post-roll). When new episodes are loaded,
 * this engine predicts and pinpoints exact ad skip intervals in milliseconds.
 */
data class LearnedAdSlot(
    val slotType: String, // "PRE_ROLL", "MID_ROLL", "POST_ROLL"
    val startRatio: Float, // 0.0 to 1.0 (relative to total episode duration)
    val endRatio: Float,   // 0.0 to 1.0
    val defaultDurationMs: Long,
    val occurrenceCount: Int
)

data class ShowAdTemplate(
    val podcastId: String,
    val hasPreRoll: Boolean,
    val preRollEndMs: Long = 75000L,
    val midRollSlots: List<LearnedAdSlot> = emptyList(),
    val hasPostRoll: Boolean = false,
    val postRollDurationMs: Long = 60000L
)

class PodcastAdPatternEngine {

    /**
     * Learns recurring ad timing templates across historical episodes of the same podcast show.
     */
    fun learnShowAdPattern(podcastId: String, showEpisodes: List<EpisodeEntity>): ShowAdTemplate {
        if (showEpisodes.isEmpty()) {
            return getDefaultTemplateForShow(podcastId)
        }

        var preRollHits = 0
        val preRollDurations = mutableListOf<Long>()
        val midRollRatios = mutableListOf<Pair<Float, Float>>()

        showEpisodes.forEach { ep ->
            val totalMs = max(100000L, ep.durationSeconds * 1000L)

            // 1. Check description or chapters for pre-roll / sponsors
            val descLower = ep.description.lowercase(Locale.ROOT)
            val titleLower = ep.title.lowercase(Locale.ROOT)
            val transcript = ep.transcript

            val hasPreRollMention = descLower.contains("sponsor") ||
                    descLower.contains("brought to you by") ||
                    descLower.contains("sponsored by") ||
                    titleLower.contains("sponsor") ||
                    TranscriptParser.isSponsorText(transcript)

            if (hasPreRollMention || ep.adTimestampsSeconds.startsWith("0") || ep.adTimestampsSeconds.contains(",0")) {
                preRollHits++
                preRollDurations.add(85000L)
            }

            // 2. Extract ad timestamps from adTimestampsSeconds field if present
            if (ep.adTimestampsSeconds.isNotBlank()) {
                val stamps = ep.adTimestampsSeconds.split(",").mapNotNull { it.trim().toLongOrNull() }
                stamps.forEach { stampSec ->
                    val stampMs = stampSec * 1000L
                    val ratio = (stampMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 0.95f)
                    if (ratio < 0.08f) {
                        preRollHits++
                        preRollDurations.add((stampMs + 60000L).coerceAtLeast(60000L))
                    } else if (ratio in 0.15f..0.85f) {
                        midRollRatios.add(Pair(ratio, (ratio + 0.035f).coerceAtMost(0.95f)))
                    }
                }
            }
        }

        val avgPreRollMs = if (preRollDurations.isNotEmpty()) {
            preRollDurations.average().toLong()
        } else {
            75000L
        }

        val compiledMidSlots = mutableListOf<LearnedAdSlot>()
        if (midRollRatios.isNotEmpty()) {
            val avgStart = midRollRatios.map { it.first }.average().toFloat()
            val avgEnd = midRollRatios.map { it.second }.average().toFloat()
            compiledMidSlots.add(
                LearnedAdSlot(
                    slotType = "MID_ROLL",
                    startRatio = avgStart.coerceIn(0.18f, 0.45f),
                    endRatio = avgEnd.coerceIn(0.22f, 0.50f),
                    defaultDurationMs = 90000L,
                    occurrenceCount = midRollRatios.size
                )
            )
        } else {
            // Default show mid-roll slot around ~22% of show duration
            compiledMidSlots.add(
                LearnedAdSlot(
                    slotType = "MID_ROLL",
                    startRatio = 0.20f,
                    endRatio = 0.24f,
                    defaultDurationMs = 75000L,
                    occurrenceCount = 1
                )
            )
        }

        val isPreRollDominant = preRollHits > 0 || (showEpisodes.size > 0 && preRollHits.toFloat() / showEpisodes.size >= 0.3f)

        return ShowAdTemplate(
            podcastId = podcastId,
            hasPreRoll = isPreRollDominant,
            preRollEndMs = avgPreRollMs,
            midRollSlots = compiledMidSlots,
            hasPostRoll = true,
            postRollDurationMs = 60000L
        )
    }

    /**
     * Generates predicted SkipRanges for a target episode using learned show ad patterns.
     */
    fun predictEpisodeAdSkips(episode: EpisodeEntity, template: ShowAdTemplate): List<SkipRange> {
        val skips = mutableListOf<SkipRange>()
        val totalMs = max(100000L, episode.durationSeconds * 1000L)

        // 1. Pre-Roll Ad (0ms to preRollEndMs)
        if (template.hasPreRoll) {
            val preRollEnd = template.preRollEndMs.coerceAtMost((totalMs * 0.15).toLong())
            if (preRollEnd > 15000L) {
                skips.add(
                    SkipRange(
                        id = "pattern_preroll_${episode.id}_0_$preRollEnd",
                        startMs = 0L,
                        endMs = preRollEnd,
                        label = "Pre-Roll Sponsor Ad (Pattern Learned)",
                        confidence = 0.95f,
                        isAutoSkipEligible = true
                    )
                )
            }
        }

        // 2. Mid-Roll Ads
        template.midRollSlots.forEachIndexed { idx, slot ->
            val startMs = (totalMs * slot.startRatio).toLong()
            val endMs = (totalMs * slot.endRatio).toLong().coerceAtLeast(startMs + 45000L)
            if (startMs in 30000L..(totalMs - 60000L)) {
                skips.add(
                    SkipRange(
                        id = "pattern_midroll_${episode.id}_${idx}_$startMs",
                        startMs = startMs,
                        endMs = endMs.coerceAtMost(totalMs - 30000L),
                        label = "Mid-Roll Sponsor Ad #${idx + 1} (Pattern Learned)",
                        confidence = 0.92f,
                        isAutoSkipEligible = true
                    )
                )
            }
        }

        return skips
    }

    private fun getDefaultTemplateForShow(podcastId: String): ShowAdTemplate {
        val isHubermanOrRogan = podcastId.contains("huberman", ignoreCase = true) ||
                podcastId.contains("rogan", ignoreCase = true) ||
                podcastId.contains("lex", ignoreCase = true)

        val preRollMs = if (isHubermanOrRogan) 120000L else 75000L

        return ShowAdTemplate(
            podcastId = podcastId,
            hasPreRoll = true,
            preRollEndMs = preRollMs,
            midRollSlots = listOf(
                LearnedAdSlot("MID_ROLL", 0.22f, 0.25f, 90000L, 1),
                LearnedAdSlot("MID_ROLL", 0.55f, 0.58f, 75000L, 1)
            ),
            hasPostRoll = false
        )
    }
}

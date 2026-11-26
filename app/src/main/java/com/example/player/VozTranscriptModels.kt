package com.example.player

import kotlin.math.max

data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 0.95f
)

data class SentenceSegment(
    val speaker: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val isSponsor: Boolean = false,
    val sponsorBrand: String? = null,
    val words: List<WordTimestamp> = emptyList()
)

data class SkipRange(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val confidence: Float = 0.92f,
    val isAutoSkipEligible: Boolean = true
) {
    val durationMs: Long get() = max(0L, endMs - startMs)
    val durationSeconds: Long get() = durationMs / 1000L
}

data class VozTranscriptResult(
    val episodeId: String,
    val modelName: String = "Desert Ant Voz (on-device)",
    val sentences: List<SentenceSegment>,
    val skipRanges: List<SkipRange>,
    val overallConfidence: Float = 0.95f
)

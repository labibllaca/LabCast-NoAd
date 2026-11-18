package com.example.asr

/** A timestamped piece of recognized speech. */
data class AsrSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float,
)

enum class AdSource { ASR, TRANSCRIPT, CHAPTER, USER }

/** An ad segment found in speech. Only `confirmed` segments may be skipped. */
data class DetectedAd(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val confidence: Float,
    val startCue: String?,
    val endCue: String?,
    val confirmed: Boolean,
    val source: AdSource,
)

enum class ScanMode { BOOTSTRAP_FULL, WINDOWED, FULL_MANUAL }

enum class ScanStatus { IDLE, SCANNING, PAUSED, PARTIAL, COMPLETE, FAILED }

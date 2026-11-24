package com.example.player

/**
 * Placeholder for real audio analysis. The app no longer fabricates waveform
 * samples or ad segments; until a real decoder-based analyser exists these
 * methods report no data.
 */
data class AcousticAdSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
    val reason: String,
    val peakAmplitude: Float
) {
    val durationMs: Long get() = endMs - startMs
    val durationSeconds: Long get() = durationMs / 1000
}

class AudioWaveAdDetector {
    fun analyzeWaveform(episodeId: String, durationSeconds: Long): Pair<List<Float>, List<AcousticAdSegment>> =
        emptyList<Float>() to emptyList()

    fun findAcousticAdAtPosition(positionMs: Long, segments: List<AcousticAdSegment>): AcousticAdSegment? = null

    fun detectMusicIntroAcoustics(podcastTitle: String, episodeId: String, durationSeconds: Long): IntroMusicInterval? = null

    fun isCommercialLoudnessSurge(positionMs: Long, waveform: List<Float>, durationMs: Long): Boolean = false

    fun getInstantaneousEnergy(positionMs: Long, waveform: List<Float>, durationMs: Long): Float = 0f
}

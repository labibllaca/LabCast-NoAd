package com.example.player

import kotlin.math.abs
import kotlin.math.sin

/**
 * Deterministic, cosmetic level strip for the player.
 *
 * This is not evidence for ad detection; it only draws a stable bar profile
 * per episode. All ad decisions come from confirmed ASR segments.
 */
class AudioWaveformVisualizer {

    fun buildWaveform(episodeId: String, durationSeconds: Long): List<Float> {
        val totalSec = durationSeconds.coerceAtLeast(300L)
        val numBars = 120
        val seed = abs(episodeId.hashCode())
        return List(numBars) { i ->
            val base = 0.25f + 0.35f * abs(sin((i + seed % 20) * 0.45f)) +
                ((i * 3 + seed) % 15) * 0.015f
            val timeWeight = 0.85f + 0.15f * abs(sin((i.toFloat() / numBars) * totalSec * 0.001f))
            (base * timeWeight).coerceIn(0.08f, 0.9f)
        }
    }

    fun getInstantaneousEnergy(
        positionMs: Long,
        waveform: List<Float>,
        durationMs: Long,
    ): Float {
        if (waveform.isEmpty() || durationMs <= 0L) return 0.4f
        val ratio = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val index = (ratio * (waveform.size - 1)).toInt().coerceIn(0, waveform.size - 1)
        return waveform[index]
    }
}

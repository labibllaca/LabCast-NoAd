package com.example.player

import kotlin.math.min
import kotlin.math.roundToInt

/** Downmixes PCM to mono and resamples to the 16 kHz Voz works at. */
object PcmConverter {
    private const val TARGET_RATE = 16_000

    fun toMono16k(pcm: ShortArray, channelCount: Int, sourceRate: Int): FloatArray {
        require(channelCount > 0) { "channelCount must be positive" }
        require(sourceRate > 0) { "sourceRate must be positive" }

        val frames = pcm.size / channelCount
        if (frames == 0) return FloatArray(0)

        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channelCount) {
                sum += pcm[frame * channelCount + channel] / 32_768f
            }
            mono[frame] = sum / channelCount
        }
        if (sourceRate == TARGET_RATE) return mono

        val outputSize = (frames.toLong() * TARGET_RATE).toDouble()
            .div(sourceRate).roundToInt().coerceAtLeast(1)
        val output = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            val position = i.toDouble() * sourceRate / TARGET_RATE
            val left = min(position.toInt(), frames - 1)
            val right = min(left + 1, frames - 1)
            val fraction = position - left
            output[i] = (mono[left] * (1.0 - fraction) + mono[right] * fraction).toFloat()
        }
        return output
    }
}

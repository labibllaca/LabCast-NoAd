package com.example.asr

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Converts decoder output to 16 kHz mono float samples. */
object PcmConverter {

    fun pcm16ToFloat(bytes: ByteArray, channels: Int): FloatArray {
        if (bytes.isEmpty() || channels <= 0) return FloatArray(0)
        val frameCount = bytes.size / 2 / channels
        val out = FloatArray(frameCount)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frameCount) {
            var sum = 0f
            for (channel in 0 until channels) {
                sum += buffer.short / 32768f
            }
            out[frame] = sum / channels
        }
        return out
    }

    fun pcmFloatToFloat(bytes: ByteArray, channels: Int): FloatArray {
        if (bytes.isEmpty() || channels <= 0) return FloatArray(0)
        val frameCount = bytes.size / 4 / channels
        val out = FloatArray(frameCount)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frameCount) {
            var sum = 0f
            for (channel in 0 until channels) {
                sum += buffer.float
            }
            out[frame] = sum / channels
        }
        return out
    }

    fun resample(input: FloatArray, fromHz: Int, toHz: Int = 16_000): FloatArray {
        if (input.isEmpty() || fromHz <= 0 || toHz <= 0 || fromHz == toHz) return input
        val outSize = (input.size.toLong() * toHz / fromHz).toInt().coerceAtLeast(1)
        val out = FloatArray(outSize)
        for (i in 0 until outSize) {
            val src = i.toDouble() * fromHz / toHz
            val i0 = src.toInt().coerceIn(0, input.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(input.lastIndex)
            val frac = (src - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }
}

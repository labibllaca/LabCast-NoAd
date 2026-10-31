package com.example.asr

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE reader for the instrumented ASR smoke test. */
object WavReader {

    data class Wav(val samples: FloatArray, val sampleRate: Int)

    fun read(input: InputStream): Wav {
        val bytes = input.use { it.readBytes() }
        require(bytes.size > 44) { "WAV too short" }
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV file" }

        var offset = 12
        var channels = 1
        var sampleRate = 16_000
        var bitsPerSample = 16
        var dataStart = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int
            val data = offset + 8
            when (id) {
                "fmt " -> {
                    channels = u16(bytes, data + 2)
                    sampleRate = ByteBuffer.wrap(bytes, data + 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample = u16(bytes, data + 14)
                }
                "data" -> {
                    dataStart = data
                    dataSize = size.coerceAtMost(bytes.size - data)
                }
            }
            if (dataStart >= 0 && id == "data") break
            offset = data + size + (size and 1)
        }

        require(dataStart >= 0) { "No data chunk" }
        require(bitsPerSample == 16) { "Expected 16-bit PCM, got $bitsPerSample" }

        val frameCount = dataSize / 2 / channels
        val samples = FloatArray(frameCount)
        val buffer = ByteBuffer.wrap(bytes, dataStart, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frameCount) {
            var sum = 0f
            for (channel in 0 until channels) {
                sum += buffer.short / 32768f
            }
            samples[frame] = sum / channels
        }
        return Wav(samples, sampleRate)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
}

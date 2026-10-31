package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmConverterTest {

    @Test
    fun `stereo 48k one second becomes 16k mono`() {
        val frames = 48_000
        val bytes = ByteArray(frames * 4)
        for (i in 0 until frames) {
            val s = (Short.MAX_VALUE / 2).toShort()
            bytes[i * 4] = s.toByte()
            bytes[i * 4 + 1] = (s.toInt() shr 8).toByte()
            bytes[i * 4 + 2] = s.toByte()
            bytes[i * 4 + 3] = (s.toInt() shr 8).toByte()
        }
        val mono = PcmConverter.pcm16ToFloat(bytes, channels = 2)
        assertEquals(frames, mono.size)
        val out = PcmConverter.resample(mono, 48_000)
        assertTrue(kotlin.math.abs(out.size - 16_000) <= 2)
    }

    @Test
    fun `float pcm is copied`() {
        val bytes = ByteArray(8)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(0, 0.5f)
        assertEquals(0.5f, PcmConverter.pcmFloatToFloat(bytes, 1)[0], 0.001f)
    }
}

package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmConverterTest {
    @Test
    fun oneSecondOf48kMonoBecomes16kSamples() {
        assertEquals(16_000, PcmConverter.toMono16k(ShortArray(48_000), 1, 48_000).size)
    }

    @Test
    fun stereoIsDownmixed() {
        val pcm = shortArrayOf(1_000, 3_000, 1_000, 3_000)
        val out = PcmConverter.toMono16k(pcm, 2, 16_000)
        assertEquals(2_000f / 32_768f, out[0], 0.0001f)
    }

    @Test
    fun samplesStayInFloatRange() {
        val out = PcmConverter.toMono16k(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), 1, 16_000)
        assertTrue(out.all { it in -1f..1f })
    }
}

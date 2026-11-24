package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualAdRangeCodecTest {
    @Test
    fun roundTripsMultipleRanges() {
        val ranges = listOf(
            ManualAdRange(10_000L, 25_000L),
            ManualAdRange(80_000L, 110_000L)
        )

        assertEquals(ranges, ManualAdRangeCodec.decode(ManualAdRangeCodec.encode(ranges)))
    }

    @Test
    fun decodesEmptyAndMalformedInputSafely() {
        assertEquals(emptyList<ManualAdRange>(), ManualAdRangeCodec.decode(""))
        assertEquals(
            listOf(ManualAdRange(10_000L, 20_000L)),
            ManualAdRangeCodec.decode("bad;10000-20000;20-20;40-10")
        )
    }

    @Test
    fun clampsRangeToEpisodeDuration() {
        assertEquals(
            ManualAdRange(80_000L, 100_000L),
            ManualAdRangeCodec.clamp(ManualAdRange(80_000L, 120_000L), 100_000L)
        )
    }

    @Test
    fun rejectsRangeThatBecomesEmptyAfterClamping() {
        assertNull(ManualAdRangeCodec.clamp(ManualAdRange(100_000L, 120_000L), 100_000L))
        assertNull(ManualAdRangeCodec.clamp(ManualAdRange(20_000L, 20_000L), 100_000L))
    }

    @Test
    fun preservesLocallyMarkedRangesWhenFeedProvidesNoManualRanges() {
        val saved = "10000-20000"

        assertEquals(saved, ManualAdRangeCodec.preserveExisting(saved, ""))
    }
}

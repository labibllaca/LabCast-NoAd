package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdCueDetectorTest {
    private val detector = AdCueDetector()

    @Test
    fun `start and end cues produce a confirmed ad`() {
        val ads = detector.adsFromSegments(
            listOf(
                AsrSegment(10_000, 15_000, "This episode is sponsored by AG1.", 0.9f),
                AsrSegment(15_000, 20_000, "Use code SAVE for twenty percent off.", 0.9f),
                AsrSegment(70_000, 74_000, "And now back to the show.", 0.9f),
            )
        )
        assertEquals(1, ads.size)
        assertTrue(ads[0].confirmed)
        assertEquals(10_000L, ads[0].startMs)
        assertEquals(74_000L, ads[0].endMs)
    }

    @Test
    fun `a brand alone is not an ad`() {
        assertTrue(
            detector.adsFromSegments(
                listOf(
                    AsrSegment(0, 5_000, "I really like AG1 and drink it daily.", 0.9f)
                )
            ).isEmpty()
        )
    }

    @Test
    fun `a cue split across segments is joined`() {
        val ads = detector.adsFromSegments(
            listOf(
                AsrSegment(0, 2_000, "This episode is sponsored", 0.9f),
                AsrSegment(2_000, 4_000, "by BetterHelp.", 0.9f),
                AsrSegment(60_000, 62_000, "Back to the show.", 0.9f),
            )
        )
        assertEquals(1, ads.size)
        assertTrue(ads[0].confirmed)
    }

    @Test
    fun `a missing end cue stays unconfirmed and is capped`() {
        val ads = detector.adsFromSegments(
            listOf(
                AsrSegment(5_000, 10_000, "Support for this podcast comes from Shopify.", 0.9f),
                AsrSegment(240_000, 245_000, "Unrelated conversation continues here.", 0.9f),
            )
        )
        assertEquals(1, ads.size)
        assertFalse(ads[0].confirmed)
        assertEquals(185_000L, ads[0].endMs)
    }
}

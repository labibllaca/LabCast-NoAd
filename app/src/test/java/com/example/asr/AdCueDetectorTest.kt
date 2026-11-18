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

    @Test
    fun `a misheard start cue still opens an ad`() {
        val ads = detector.adsFromSegments(
            listOf(
                AsrSegment(20_000, 24_000, "This episode is brought to you buy Better Help.", 0.8f),
                AsrSegment(80_000, 84_000, "And now back to the show.", 0.9f),
            )
        )
        assertEquals(1, ads.size)
        assertTrue(ads[0].confirmed)
        assertEquals(20_000L, ads[0].startMs)
    }

    @Test
    fun `a spoken phone number with promo language opens an ad`() {
        val ads = detector.adsFromSegments(
            listOf(
                AsrSegment(
                    30_000,
                    35_000,
                    "Call one eight hundred five five five zero one zero and use code SAVE.",
                    0.8f,
                ),
                AsrSegment(90_000, 94_000, "Back to our conversation.", 0.9f),
            )
        )
        assertEquals(1, ads.size)
        assertTrue(ads[0].confirmed)
    }

    @Test
    fun `a fuzzy cue in the first eight seconds is ignored`() {
        val ads = detector.adsFromSegments(
            listOf(AsrSegment(3_000, 6_000, "so this episode is brought to you buy us.", 0.7f))
        )
        assertTrue(ads.isEmpty())
    }

    @Test
    fun `a resume phrase does not close a young ad`() {
        val ads = detector.adsFromSegments(
            listOf(
                AsrSegment(10_000, 14_000, "This episode is sponsored by AG1.", 0.9f),
                AsrSegment(20_000, 23_000, "So anyway.", 0.9f),
            )
        )
        assertEquals(1, ads.size)
        assertFalse(ads[0].confirmed)
    }

    @Test
    fun `the canonical cue is recorded`() {
        val ads = detector.adsFromSegments(
            listOf(
                AsrSegment(20_000, 24_000, "brought to you buy Squarespace", 0.8f),
                AsrSegment(80_000, 84_000, "back to the show", 0.9f),
            )
        )
        assertEquals("brought to you by", ads[0].startCue)
    }
}

package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VozCueDetectorTest {
    private fun words(vararg text: String) = text.mapIndexed { i, t ->
        TimedWord(t, i * 1_000L, i * 1_000L + 900L)
    }

    @Test
    fun startAndResumeCueMakeAutoEligibleRange() {
        val r = VozCueDetector.detect(words("Sponsored by", "NordVPN", "and now back to the show"))
        assertEquals(1, r.size)
        assertTrue(r[0].isAutoSkipEligible)
        assertEquals(0L, r[0].startMs)
        assertEquals(2_000L, r[0].endMs)
    }

    @Test
    fun startCueWithoutResumeIsMarkerOnly() {
        val r = VozCueDetector.detect(words("Sponsored by", "NordVPN", "a great episode"))
        assertEquals(1, r.size)
        assertFalse(r[0].isAutoSkipEligible)
    }

    @Test
    fun brandMentionAloneDoesNotOpenRange() {
        assertTrue(VozCueDetector.detect(words("I use NordVPN", "every day")).isEmpty())
    }

    @Test
    fun noCuesProduceNoRanges() {
        assertTrue(VozCueDetector.detect(words("Welcome to the show", "today")).isEmpty())
    }
}

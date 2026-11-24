package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptUnmarkTest {

    @Test
    fun unmarkedSponsorSegmentStaysUnmarkedAfterSerializeAndParse() {
        val raw = "03:00 [Host] Sponsored by BetterHelp. Visit betterhelp.com/jordan for 10% off."
        val parsed = TranscriptParser.parseTranscript(raw)
        assertTrue(parsed.single().isSponsor)

        val unmarked = parsed.map { it.copy(isSponsor = false, manuallyUnmarked = true) }
        val reparsed = TranscriptParser.parseTranscript(
            TranscriptParser.serializeSegmentsToTranscriptText(unmarked)
        )

        assertFalse("Unmarking a sponsor segment did not persist", reparsed.single().isSponsor)
        assertTrue("Unmark override should survive reparsing", reparsed.single().manuallyUnmarked)
        assertTrue(
            "Unmarked segment must not be suggested as an ad again",
            TranscriptParser.analyzeTranscriptForSuggestedAds(reparsed).isEmpty()
        )
    }
}

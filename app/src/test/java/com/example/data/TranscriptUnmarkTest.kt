package com.example.data

import org.junit.Assert.assertEquals
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

    @Test
    fun repeatedSerializationDoesNotAccumulateSponsorTags() {
        val raw = "03:00 [Host] Sponsored by BetterHelp. Visit betterhelp.com/jordan for 10% off."
        val first = TranscriptParser.parseTranscript(raw).single()

        val once = TranscriptParser.serializeSegmentsToTranscriptText(
            listOf(first.copy(isSponsor = false, manuallyUnmarked = true))
        )
        val twice = TranscriptParser.serializeSegmentsToTranscriptText(
            TranscriptParser.parseTranscript(once)
        )
        val thrice = TranscriptParser.serializeSegmentsToTranscriptText(
            TranscriptParser.parseTranscript(twice)
        )

        assertEquals(once, twice)
        assertEquals(twice, thrice)
        assertFalse(TranscriptParser.parseTranscript(thrice).single().isSponsor)
    }

    @Test
    fun legacyRepeatedTagsAreStrippedFromSpeaker() {
        val raw = "00:00 [Pre-Roll Sponsor (BetterHelp) [Sponsor Break [Sponsor Break]] ] " +
            "This episode is sponsored by BetterHelp."

        val parsed = TranscriptParser.parseTranscript(raw).single()

        assertEquals("Pre-Roll Sponsor (BetterHelp)", parsed.speaker)
        assertTrue(parsed.isSponsor)
        val serialized = TranscriptParser.serializeSegmentsToTranscriptText(listOf(parsed))
        assertEquals(1, serialized.split("[Sponsor Break").size - 1)
    }
}

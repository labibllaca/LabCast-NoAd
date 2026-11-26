package com.example.ui

import com.example.player.AdSkipInterval
import com.example.player.AutoSkipSelection
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSkipEligibilityTest {
    @Test
    fun markerOnlyVozRangeIsNotAutoSkipped() {
        val marker = AdSkipInterval(
            id = "voz_marker",
            startMs = 0L,
            endMs = 60_000L,
            triggerWord = "Sponsored by",
            resumeWord = "",
            confidence = 0.95f,
            source = "Desert Ant Voz STT",
            isAutoSkipEligible = false
        )
        assertNull(AutoSkipSelection.select(listOf(marker), 30_000L))
    }

    @Test
    fun eligibleVozRangeIsAutoSkipped() {
        val skip = AdSkipInterval(
            id = "voz_cue",
            startMs = 0L,
            endMs = 60_000L,
            triggerWord = "Sponsored by",
            resumeWord = "back to the show",
            confidence = 0.95f,
            source = "Desert Ant Voz STT"
        )
        assertNotNull(AutoSkipSelection.select(listOf(skip), 30_000L))
    }
}

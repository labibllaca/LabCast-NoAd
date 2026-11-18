package com.example.player

import com.example.data.EpisodeAdSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AdSkipDeciderTest {
    private val ad = EpisodeAdSegmentEntity(
        "a", "e", "p", 10_000, 70_000, "AG1", 0.9f, "ASR", true, 1,
    )

    @Test
    fun `inside a confirmed ad skips to the end`() {
        val d = AdSkipDecider().decide(20_000, listOf(ad), autoSkip = true)
        assertEquals(SkipDecision.Skip(ad), d)
    }

    @Test
    fun `outside the ad does nothing`() {
        assertEquals(SkipDecision.None, AdSkipDecider().decide(80_000, listOf(ad), true))
    }

    @Test
    fun `auto skip off does nothing`() {
        assertEquals(SkipDecision.None, AdSkipDecider().decide(20_000, listOf(ad), false))
    }

    @Test
    fun `an unconfirmed ad awaits its end without seeking`() {
        val pending = ad.copy(confirmed = false)
        assertEquals(
            SkipDecision.AwaitEnd(10_000),
            AdSkipDecider().decide(20_000, listOf(pending), true),
        )
    }

    @Test
    fun `near the end does nothing to avoid a loop`() {
        assertEquals(SkipDecision.None, AdSkipDecider().decide(69_500, listOf(ad), true))
    }

    @Test
    fun `a confirmed user mark skips`() {
        val user = ad.copy(source = "USER", confirmed = true)
        assertEquals(SkipDecision.Skip(user), AdSkipDecider().decide(20_000, listOf(user), true))
    }
}

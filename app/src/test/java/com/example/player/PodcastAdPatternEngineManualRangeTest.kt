package com.example.player

import com.example.data.EpisodeEntity
import com.example.data.ManualAdRange
import com.example.data.ManualAdRangeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastAdPatternEngineManualRangeTest {
    private val engine = PodcastAdPatternEngine()

    @Test
    fun oneMarkedEpisodeDoesNotCreateManualDerivedSlot() {
        val episode = episode(
            id = "one",
            durationSeconds = 1_000,
            ranges = listOf(ManualAdRange(500_000L, 600_000L))
        )

        val template = engine.learnShowAdPattern("show", listOf(episode))

        assertFalse(template.midRollSlots.any { it.startRatio in 0.49f..0.51f })
    }

    @Test
    fun correspondingRangesAcrossTwoEpisodesCreateLearnedSlot() {
        val episodes = listOf(
            episode("one", 1_000, listOf(ManualAdRange(500_000L, 600_000L))),
            episode("two", 2_000, listOf(ManualAdRange(1_080_000L, 1_260_000L)))
        )

        val slot = engine.learnShowAdPattern("show", episodes).midRollSlots
            .firstOrNull { it.startRatio in 0.49f..0.55f }

        assertTrue("expected a slot supported by both episodes", slot != null)
        assertEquals(2, slot!!.occurrenceCount)
        assertEquals(0.52f, slot.startRatio, 0.001f)
        assertEquals(0.615f, slot.endRatio, 0.001f)
        assertEquals(140_000L, slot.defaultDurationMs)

        val predicted = engine.predictEpisodeAdSkips(episode("target", 1_000, emptyList()),
            engine.learnShowAdPattern("show", episodes))
            .single { it.label == "Ad Pattern Learned from Manual Marks" }
        assertEquals(520_000L, predicted.startMs)
        assertEquals(615_000L, predicted.endMs)
    }

    @Test
    fun twoMarksFromOneEpisodeStillCountAsOnlyOneSupportingEpisode() {
        val episode = episode(
            id = "one",
            durationSeconds = 1_000,
            ranges = listOf(
                ManualAdRange(500_000L, 600_000L),
                ManualAdRange(520_000L, 620_000L)
            )
        )

        val template = engine.learnShowAdPattern("show", listOf(episode))

        assertFalse(template.midRollSlots.any { it.startRatio in 0.49f..0.55f })
    }

    private fun episode(id: String, durationSeconds: Long, ranges: List<ManualAdRange>) = EpisodeEntity(
        id = id,
        podcastId = "show",
        podcastTitle = "Test show",
        podcastCoverUrl = "",
        title = "Episode $id",
        description = "",
        durationSeconds = durationSeconds,
        publishDate = "2026-01-01",
        audioUrl = "https://example.invalid/audio.mp3",
        manualAdRanges = ManualAdRangeCodec.encode(ranges)
    )
}

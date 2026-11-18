package com.example.asr

import com.example.data.AdSlotEntity
import com.example.data.EpisodeAdSegmentEntity
import com.example.data.EpisodeEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdScanControllerTest {

    private fun episode(id: String = "e") = EpisodeEntity(
        id = id,
        podcastId = "p",
        podcastTitle = "Show",
        podcastCoverUrl = "",
        title = "Episode",
        description = "",
        durationSeconds = 600,
        publishDate = "",
        audioUrl = "https://example.com/a.mp3",
    )

    private fun TestScope.controller(
        store: FakeStore,
        source: FakePcmSource,
        engine: FakeEngine,
        isEnabled: () -> Boolean = { true },
        bootstrap: (EpisodeEntity) -> List<DetectedAd> = { emptyList() },
    ): AdScanController {
        val dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
        return AdScanController(
            store = store,
            engine = engine,
            pcmSources = { source },
            cueDetector = AdCueDetector(),
            slotLearner = AdSlotLearner(),
            predictor = AdSlotPredictor(),
            planner = ScanPlanner(),
            scope = this,
            throttle = {},
            dispatcher = dispatcher,
            isEnabled = isEnabled,
            bootstrap = bootstrap,
            logger = {},
        )
    }

    private fun userAd(startMs: Long, endMs: Long, createdAt: Long = 1) = EpisodeAdSegmentEntity(
        id = "e:$startMs:$endMs",
        episodeId = "e",
        podcastId = "p",
        startMs = startMs,
        endMs = endMs,
        label = "Manual mark",
        confidence = 1f,
        source = "USER",
        confirmed = true,
        createdAt = createdAt,
    )

    private fun asrAd(startMs: Long, endMs: Long) = EpisodeAdSegmentEntity(
        id = "e:$startMs:$endMs",
        episodeId = "e",
        podcastId = "p",
        startMs = startMs,
        endMs = endMs,
        label = "AG1",
        confidence = 0.9f,
        source = "ASR",
        confirmed = true,
        createdAt = 1,
    )

    @Test
    fun `a rescan never replaces a user mark`() = runTest {
        val controller = controller(FakeStore(), FakePcmSource(durationMs = 600_000), FakeEngine(emptyMap()))
        val merged = controller.mergeAds(
            existing = listOf(userAd(10_000, 70_000)),
            detected = listOf(asrAd(20_000, 60_000)),
        )
        assertEquals(listOf("USER"), merged.map { it.source })
        assertEquals(10_000L, merged.single().startMs)
    }

    @Test
    fun `overlapping user marks keep the newer one`() = runTest {
        val controller = controller(FakeStore(), FakePcmSource(durationMs = 600_000), FakeEngine(emptyMap()))
        val older = userAd(0, 60_000, createdAt = 1)
        val newer = userAd(30_000, 90_000, createdAt = 2)
        val merged = controller.mergeAds(existing = listOf(older, newer), detected = emptyList())
        assertEquals(listOf(newer.id), merged.map { it.id })
    }

    @Test
    fun `start clears ads before the disabled early return`() = runTest {
        val controller = controller(
            FakeStore(),
            FakePcmSource(durationMs = 600_000),
            FakeEngine(emptyMap()),
            isEnabled = { false },
        )
        controller.start(episode(), 0)
        assertTrue(controller.currentAds.value.isEmpty())
    }

    private fun learnedSlotsForLongEpisode() = listOf(
        AdSlotEntity("p", 0, 0f, 0, 60_000, 0, 1, 0, 0.5f, "ASR", true, 0),
        AdSlotEntity("p", 1, 0.5f, 1_800_000, 60_000, 0, 1, 0, 0.5f, "ASR", true, 0),
    )

    @Test
    fun `full scan learns pre-roll and mid-roll slots`() = runTest {
        val store = FakeStore()
        val source = FakePcmSource(durationMs = 600_000)
        val engine = FakeEngine(
            mapOf(
                0L to "This episode is sponsored by AG1.",
                60_000L to "And now back to the show.",
                300_000L to "A word from our sponsors. Use code SAVE.",
                360_000L to "Back to the show.",
            )
        )
        val controller = controller(store, source, engine)

        controller.start(episode(), 600_000)
        controller.updatePlayhead(600_000, true)
        controller.awaitIdle()

        val slots = store.slots("p")
        assertEquals(2, slots.size)
        assertEquals(0L, slots[0].startMsAnchor)
        assertEquals(300_000L, slots[1].startMsAnchor)
        assertTrue(store.ads("e").isNotEmpty())
    }

    @Test
    fun `second episode uses windowed mode and reads under 15 percent`() = runTest {
        val store = FakeStore().apply { saveSlots("p", learnedSlotsForLongEpisode()) }
        val source = FakePcmSource(durationMs = 3_600_000)
        val controller = controller(store, source, FakeEngine(emptyMap()))

        controller.start(episode(id = "e2"), 3_600_000)
        controller.updatePlayhead(3_600_000, true)
        controller.awaitIdle()

        assertEquals(ScanMode.WINDOWED, controller.state.value.mode)
        assertTrue(source.totalReadMs < 3_600_000 * 0.15)
    }

    @Test
    fun `seek during a scan reads the target range`() = runTest {
        val source = FakePcmSource(durationMs = 600_000)
        val controller = controller(FakeStore(), source, FakeEngine(emptyMap()))

        controller.start(episode(), 0)
        controller.updatePlayhead(600_000, true)
        controller.seek(500_000)
        controller.awaitIdle()

        assertTrue(source.readRanges.toString(), source.readRanges.any { it.first == 500_000L })
    }

    @Test
    fun `stopping saves partial coverage`() = runTest {
        val store = FakeStore()
        val controller = controller(store, FakePcmSource(600_000), FakeEngine(emptyMap()))

        controller.start(episode(), 0)
        controller.stop()
        controller.awaitIdle()

        assertEquals(ScanStatus.PARTIAL.name, store.scan("e")!!.status)
    }

    @Test
    fun `feed bootstrap starts a windowed scan`() = runTest {
        val store = FakeStore()
        val source = FakePcmSource(durationMs = 600_000)
        val candidate = DetectedAd(
            startMs = 240_000,
            endMs = 300_000,
            label = "AG1",
            confidence = 0.5f,
            startCue = null,
            endCue = null,
            confirmed = false,
            source = AdSource.TRANSCRIPT,
        )
        val controller = controller(store, source, FakeEngine(emptyMap())) { listOf(candidate) }

        controller.start(episode(), 300_000)
        controller.awaitIdle()

        assertEquals(ScanMode.WINDOWED, controller.state.value.mode)
        assertEquals(1, store.slots("p").size)
    }
}

package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.EpisodeEntity
import com.example.data.ManualAdRange
import com.example.data.ManualAdRangeCodec
import com.example.data.PodcastDatabase
import com.example.data.resetPodcastDatabaseSingleton
import com.example.player.AdSkipInterval
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualAdRangeViewModelEditTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PodcastViewModel
    private lateinit var dao: com.example.data.PodcastDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        resetPodcastDatabaseSingleton()
        val app = ApplicationProvider.getApplicationContext<Application>()
        dao = PodcastDatabase.getDatabase(app).podcastDao()
        viewModel = PodcastViewModel(app)
    }

    @After
    fun tearDown() {
        resetPodcastDatabaseSingleton()
        Dispatchers.resetMain()
    }

    @Test
    fun nudgePersistsNewBoundaryAndRebuildsSkipInterval() = runTest {
        val episode = insertEpisode("episode-1", ManualAdRange(10_000L, 20_000L))
        setCurrentEpisode(episode)

        viewModel.nudgeManualAdRange(ManualAdRange(10_000L, 20_000L), false, 1_000L)
        dispatcher.scheduler.advanceUntilIdle()
        awaitCondition {
            viewModel.currentPlayingEpisode.value?.manualAdRanges ==
                ManualAdRangeCodec.encode(listOf(ManualAdRange(10_000L, 21_000L)))
        }

        assertEquals(
            listOf(ManualAdRange(10_000L, 21_000L)),
            ManualAdRangeCodec.decode(dao.getEpisodeById(episode.id)!!.manualAdRanges)
        )
        val interval = viewModel.detectedTranscriptAds.value.single { it.id.startsWith("manual_") }
        assertEquals(10_000L, interval.startMs)
        assertEquals(21_000L, interval.endMs)
    }

    @Test
    fun deleteRemovesRangeAndItsInterval() = runTest {
        val episode = insertEpisode("episode-2", ManualAdRange(10_000L, 20_000L))
        setCurrentEpisode(episode)

        viewModel.deleteManualAdRange(ManualAdRange(10_000L, 20_000L))
        dispatcher.scheduler.advanceUntilIdle()
        awaitCondition { viewModel.currentPlayingEpisode.value?.manualAdRanges == "" }

        assertEquals(
            emptyList<ManualAdRange>(),
            ManualAdRangeCodec.decode(dao.getEpisodeById(episode.id)!!.manualAdRanges)
        )
        assertFalse(viewModel.detectedTranscriptAds.value.any { it.id.startsWith("manual_") })
    }

    @Test
    fun invalidNudgeLeavesStoredRangeAndIntervalUnchanged() = runTest {
        val episode = insertEpisode("episode-3", ManualAdRange(10_000L, 11_000L))
        setCurrentEpisode(episode)
        setDetectedAds(
            AdSkipInterval(
                id = "manual_episode-3_10000_11000",
                startMs = 10_000L,
                endMs = 11_000L,
                triggerWord = "Manual ad mark",
                resumeWord = "Marked ad end",
                confidence = 1f,
                source = "Manual User Mark"
            )
        )

        viewModel.nudgeManualAdRange(ManualAdRange(10_000L, 11_000L), false, -2_000L)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(ManualAdRange(10_000L, 11_000L)),
            ManualAdRangeCodec.decode(viewModel.currentPlayingEpisode.value!!.manualAdRanges)
        )
        val interval = viewModel.detectedTranscriptAds.value.single { it.id.startsWith("manual_") }
        assertEquals(11_000L, interval.endMs)
    }

    @Test
    fun changingEpisodeBeforeWriteDoesNotMutateNewEpisodeState() = runTest {
        val first = insertEpisode("episode-4", ManualAdRange(10_000L, 20_000L))
        val second = insertEpisode("episode-5", ManualAdRange(30_000L, 40_000L))
        setCurrentEpisode(first)

        viewModel.nudgeManualAdRange(ManualAdRange(10_000L, 20_000L), false, 1_000L)
        setCurrentEpisode(second)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(ManualAdRange(30_000L, 40_000L)),
            ManualAdRangeCodec.decode(viewModel.currentPlayingEpisode.value!!.manualAdRanges)
        )
        assertEquals(
            listOf(ManualAdRange(30_000L, 40_000L)),
            ManualAdRangeCodec.decode(dao.getEpisodeById(second.id)!!.manualAdRanges)
        )
    }

    private suspend fun insertEpisode(id: String, range: ManualAdRange): EpisodeEntity {
        val episode = EpisodeEntity(
            id = id,
            podcastId = "podcast-1",
            podcastTitle = "Test podcast",
            podcastCoverUrl = "",
            title = "Test episode",
            description = "",
            durationSeconds = 100L,
            publishDate = "2026-01-01",
            audioUrl = "https://example.invalid/audio.mp3",
            manualAdRanges = ManualAdRangeCodec.encode(listOf(range))
        )
        dao.insertEpisodes(listOf(episode))
        return dao.getEpisodeById(id)!!
    }

    private fun setCurrentEpisode(episode: EpisodeEntity) {
        val field = PodcastViewModel::class.java.getDeclaredField("_currentPlayingEpisode")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableStateFlow<EpisodeEntity?>).value = episode
    }

    @Test
    fun republishAfterStaleAnalysisKeepsNewManualBoundaryAndOtherAds() = runTest {
        val episode = insertEpisode("episode-6", ManualAdRange(10_000L, 20_000L))
        setCurrentEpisode(episode)
        setDetectedAds(
            AdSkipInterval(
                id = "manual_episode-6_10000_20000",
                startMs = 10_000L,
                endMs = 20_000L,
                triggerWord = "Manual ad mark",
                resumeWord = "Marked ad end",
                confidence = 1f,
                source = "Manual User Mark"
            ),
            AdSkipInterval(
                id = "transcript-ad",
                startMs = 30_000L,
                endMs = 40_000L,
                triggerWord = "Sponsor",
                resumeWord = "Back to topic",
                confidence = 0.9f,
                source = "Transcript"
            )
        )

        // An edit lands while a background analysis still holds the old episode.
        dao.updateManualAdRanges(
            episode.id,
            ManualAdRangeCodec.encode(listOf(ManualAdRange(10_000L, 21_000L)))
        )

        viewModel.syncManualAdRanges(episode.id, 100_000L)
        awaitCondition {
            viewModel.detectedTranscriptAds.value.firstOrNull { it.id.startsWith("manual_") }?.endMs == 21_000L
        }

        val ads = viewModel.detectedTranscriptAds.value
        assertEquals(2, ads.size)
        assertEquals("transcript-ad", ads.single { !it.id.startsWith("manual_") }.id)
    }

    private suspend fun awaitCondition(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {

            delay(10L)
        }
        check(predicate()) {
            "Condition not met within ${timeoutMs}ms; ranges=${viewModel.currentPlayingEpisode.value?.manualAdRanges} " +
                "ads=${viewModel.detectedTranscriptAds.value.map { it.id }} saving=${viewModel.isManualAdRangeSaving.value} " +
                "event=${viewModel.sponsorSkipEvent.value}"
        }
    }

    private fun setDetectedAds(vararg intervals: AdSkipInterval) {
        val field = PodcastViewModel::class.java.getDeclaredField("_detectedTranscriptAds")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableStateFlow<List<AdSkipInterval>>).value = intervals.toList()
    }
}

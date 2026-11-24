package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.EpisodeEntity
import com.example.data.PodcastDatabase
import com.example.data.TranscriptParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptUnmarkViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun tappingUnmarkPersistsOverrideInTranscript() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = PodcastDatabase.getDatabase(app).podcastDao()
        val episode = EpisodeEntity(
            id = "unmark-vm-1",
            podcastId = "podcast-1",
            podcastTitle = "Test podcast",
            podcastCoverUrl = "",
            title = "Test episode",
            description = "",
            durationSeconds = 600L,
            publishDate = "2026-01-01",
            audioUrl = "https://example.invalid/audio.mp3",
            transcript = "03:00 [Host] Sponsored by BetterHelp. Visit betterhelp.com/jordan for 10% off."
        )
        dao.insertEpisodes(listOf(episode))
        val viewModel = PodcastViewModel(app)
        setCurrentEpisode(viewModel, dao.getEpisodeById(episode.id)!!)
        val collector = launch { viewModel.transcriptSegments.collect {} }
        dispatcher.scheduler.runCurrent()

        val segment = viewModel.transcriptSegments.value.single { it.startTimeSeconds == 180L }
        assertTrue(segment.isSponsor)

        viewModel.toggleSegmentAdStatus(180L)
        dispatcher.scheduler.runCurrent()

        val deadline = System.currentTimeMillis() + 2_000L
        while (!dao.getEpisodeById(episode.id)!!.transcript.contains(TranscriptParser.UNMARK_TOKEN) &&
            System.currentTimeMillis() < deadline
        ) {
            delay(10L)
        }
        val stored = dao.getEpisodeById(episode.id)!!.transcript
        assertTrue("Unmark override was not persisted", stored.contains(TranscriptParser.UNMARK_TOKEN))
        val reparsed = TranscriptParser.parseTranscript(stored)
        assertFalse(reparsed.single().isSponsor)
        assertTrue(reparsed.single().manuallyUnmarked)
        collector.cancel()
    }

    private fun setCurrentEpisode(viewModel: PodcastViewModel, episode: EpisodeEntity) {
        val field = PodcastViewModel::class.java.getDeclaredField("_currentPlayingEpisode")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableStateFlow<EpisodeEntity?>).value = episode
    }
}

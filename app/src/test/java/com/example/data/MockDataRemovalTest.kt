package com.example.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.player.VozCueDetector
import com.example.player.VozScanPlanner
import com.example.ui.PodcastViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MockDataRemovalTest {

    @Test
    fun initialDataIsEmptyWithoutDemoPodcasts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java)
            .allowMainThreadQueries().build()
        val repository = PodcastRepository(db.podcastDao())

        repository.populateInitialDataIfNeeded(context)

        assertTrue(repository.allPodcasts.first().isEmpty())
        assertTrue(repository.allEpisodes.first().isEmpty())
        db.close()
    }

    @Test
    fun episodeWithoutTranscriptProducesNoSynthesizedVozSegments() = runBlocking {
        val episode = EpisodeEntity(
            id = "ep-no-transcript",
            podcastId = "pod-1",
            podcastTitle = "Real podcast",
            podcastCoverUrl = "",
            title = "Real episode",
            description = "",
            durationSeconds = 600L,
            publishDate = "2026-01-01",
            audioUrl = "https://example.invalid/audio.mp3"
        )

        val windows = VozScanPlanner.plan(episode.durationSeconds * 1_000L, emptyList())

        assertTrue(windows.isNotEmpty())
        assertTrue(VozCueDetector.detect(emptyList()).isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playerHasNoGeneratedChaptersWhenEpisodeHasNone() = runTest {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = PodcastViewModel(app)
            val episode = EpisodeEntity(
                id = "ep-no-chapters",
                podcastId = "pod-1",
                podcastTitle = "Real podcast",
                podcastCoverUrl = "",
                title = "Real episode",
                description = "",
                durationSeconds = 600L,
                publishDate = "2026-01-01",
                audioUrl = "https://example.invalid/audio.mp3"
            )
            val field = PodcastViewModel::class.java.getDeclaredField("_currentPlayingEpisode")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(viewModel) as MutableStateFlow<EpisodeEntity?>).value = episode
            val collector = launch { viewModel.currentChapters.collect {} }
            dispatcher.scheduler.runCurrent()

            assertTrue(viewModel.currentChapters.value.isEmpty())
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun parserKeepsMissingChaptersEmpty() {
        val segments = TranscriptParser.parseTranscript("01:00 [Host] Real spoken content.")
        assertEquals(1, segments.size)
        assertEquals("Host", segments.first().speaker)
    }
}

package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.EpisodeEntity
import com.example.data.PodcastDao
import com.example.data.PodcastDatabase
import com.example.data.resetPodcastDatabaseSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentEpisodesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: PodcastDao
    private lateinit var viewModel: PodcastViewModel

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
    fun exposesOnlyTheThreeMostRecentlyPlayed() = runBlocking {
        dao.insertEpisodes(
            listOf(
                episode("vm-recent-1", 9_000_000_000_001L),
                episode("vm-recent-2", 9_000_000_000_004L),
                episode("vm-recent-3", 9_000_000_000_003L),
                episode("vm-recent-4", 9_000_000_000_002L)
            )
        )

        // Room queries resume on its own executor, so wait on real time while draining Main.
        val expected = listOf("vm-recent-2", "vm-recent-3", "vm-recent-4")
        val deadline = System.currentTimeMillis() + 5_000L
        var ids = emptyList<String>()
        while (System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.advanceUntilIdle()
            ids = viewModel.recentEpisodes.value.map { it.id }
            if (ids == expected) break
            delay(20L)
        }

        assertEquals(expected, ids)
    }

    @Test
    fun ignoresUnknownEpisodeIdsFromShortcuts() = runBlocking {
        viewModel.playEpisodeById("vm-missing-episode")
        dispatcher.scheduler.advanceUntilIdle()
        delay(200L)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.currentPlayingEpisode.value)
    }

    private fun episode(id: String, lastPlayedAt: Long) = EpisodeEntity(
        id = id,
        podcastId = "podcast-$id",
        podcastTitle = "Show",
        podcastCoverUrl = "",
        title = "Episode $id",
        description = "",
        durationSeconds = 600,
        publishDate = "2026-01-01",
        audioUrl = "https://example.invalid/$id.mp3",
        lastPlayedAt = lastPlayedAt
    )
}

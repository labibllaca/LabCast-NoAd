package com.example.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastPlayedPersistenceTest {
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        dao = PodcastDatabase.getDatabase(app).podcastDao()
    }

    @Test
    fun stampsAndReadsLastPlayedAt() = runBlocking {
        dao.insertEpisodes(listOf(episode("persist-1")))
        assertEquals(0L, dao.getEpisodeById("persist-1")!!.lastPlayedAt)

        dao.updateLastPlayedAt("persist-1", 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, dao.getEpisodeById("persist-1")!!.lastPlayedAt)
    }

    private fun episode(id: String) = EpisodeEntity(
        id = id, podcastId = "podcast", podcastTitle = "Show", podcastCoverUrl = "",
        title = "Episode $id", description = "", durationSeconds = 600,
        publishDate = "2026-01-01", audioUrl = "https://example.invalid/a.mp3"
    )
}

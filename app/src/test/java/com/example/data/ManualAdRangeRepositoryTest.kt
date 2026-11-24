package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualAdRangeRepositoryTest {
    private lateinit var database: PodcastDatabase
    private lateinit var repository: PodcastRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PodcastDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = PodcastRepository(database.podcastDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savesClampedRangesAndDeduplicatesExactDuplicates() = runBlocking {
        val episode = testEpisode(durationSeconds = 100)
        database.podcastDao().insertEpisodes(listOf(episode))

        repository.addManualAdRange(episode.id, ManualAdRange(80_000L, 120_000L))
        repository.addManualAdRange(episode.id, ManualAdRange(80_000L, 120_000L))

        val stored = database.podcastDao().getEpisodeById(episode.id)
        assertNotNull(stored)
        assertEquals(
            listOf(ManualAdRange(80_000L, 100_000L)),
            ManualAdRangeCodec.decode(stored!!.manualAdRanges)
        )
    }

    @Test
    fun ignoresUnknownEpisodeAndEmptyRange() = runBlocking {
        val episode = testEpisode(durationSeconds = 100)
        database.podcastDao().insertEpisodes(listOf(episode))

        repository.addManualAdRange(episode.id, ManualAdRange(100_000L, 120_000L))
        repository.addManualAdRange("missing", ManualAdRange(10_000L, 20_000L))

        assertEquals(emptyList<ManualAdRange>(), ManualAdRangeCodec.decode(
            database.podcastDao().getEpisodeById(episode.id)!!.manualAdRanges
        ))
    }

    @Test
    fun updateManualAdRangePersistsOnlyTheReplacedRange() = runBlocking {
        val episode = testEpisode(durationSeconds = 100)
        database.podcastDao().insertEpisodes(listOf(episode))
        val target = ManualAdRange(10_000L, 20_000L)
        val sibling = ManualAdRange(40_000L, 50_000L)
        database.podcastDao().updateManualAdRanges(
            episode.id,
            ManualAdRangeCodec.encode(listOf(target, sibling))
        )

        repository.updateManualAdRange(episode.id, target, ManualAdRange(15_000L, 20_000L))

        assertEquals(
            listOf(ManualAdRange(15_000L, 20_000L), sibling),
            ManualAdRangeCodec.decode(database.podcastDao().getEpisodeById(episode.id)!!.manualAdRanges)
        )
    }

    @Test
    fun deleteManualAdRangeRemovesOnlyTheTarget() = runBlocking {
        val episode = testEpisode(durationSeconds = 100)
        database.podcastDao().insertEpisodes(listOf(episode))
        val target = ManualAdRange(15_000L, 20_000L)
        val sibling = ManualAdRange(40_000L, 50_000L)
        database.podcastDao().updateManualAdRanges(
            episode.id,
            ManualAdRangeCodec.encode(listOf(target, sibling))
        )

        repository.deleteManualAdRange(episode.id, target)

        assertEquals(
            listOf(sibling),
            ManualAdRangeCodec.decode(database.podcastDao().getEpisodeById(episode.id)!!.manualAdRanges)
        )
    }

    @Test
    fun invalidAndUnknownEditsAreNoOps() = runBlocking {
        val episode = testEpisode(durationSeconds = 100)
        database.podcastDao().insertEpisodes(listOf(episode))
        val saved = ManualAdRange(10_000L, 20_000L)
        database.podcastDao().updateManualAdRanges(episode.id, ManualAdRangeCodec.encode(listOf(saved)))

        repository.updateManualAdRange("missing", saved, ManualAdRange(15_000L, 20_000L))
        repository.updateManualAdRange(episode.id, saved, ManualAdRange(30_000L, 25_000L))
        repository.deleteManualAdRange("missing", saved)

        assertEquals(
            listOf(saved),
            ManualAdRangeCodec.decode(database.podcastDao().getEpisodeById(episode.id)!!.manualAdRanges)
        )
    }

    private fun testEpisode(durationSeconds: Long) = EpisodeEntity(
        id = "episode-1",
        podcastId = "podcast-1",
        podcastTitle = "Test podcast",
        podcastCoverUrl = "",
        title = "Test episode",
        description = "",
        durationSeconds = durationSeconds,
        publishDate = "2026-01-01",
        audioUrl = "https://example.invalid/audio.mp3"
    )
}

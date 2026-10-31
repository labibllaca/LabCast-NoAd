package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdDaoTest {
    private lateinit var db: PodcastDatabase
    private lateinit var dao: AdDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PodcastDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.adDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `slots and ads round-trip`() = runBlocking {
        dao.upsertSlots(
            listOf(AdSlotEntity("p", 0, 0f, 0, 60_000, 0, 1, 0, 0.5f, "ASR", true, 1))
        )
        dao.upsertAdSegments(
            listOf(
                EpisodeAdSegmentEntity(
                    "e:0:60000", "e", "p", 0, 60_000, "AG1", 0.9f, "ASR", true, 1,
                )
            )
        )
        assertEquals(1, dao.slots("p").size)
        assertEquals(1, dao.adSegments("e").size)
        assertEquals(1, dao.adSegmentsOverlapping("e", 10_000, 20_000).size)
    }

    @Test
    fun `coverage codec round-trips`() {
        val ranges = listOf(0L..120_000L, 300_000L..365_000L)
        assertEquals(ranges, CoverageCodec.decode(CoverageCodec.encode(ranges)))
        assertTrue(CoverageCodec.decode("").isEmpty())
    }
}

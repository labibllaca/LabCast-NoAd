package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AsrWindowStoreTest {
    @Test
    fun saveThenLoadRoundTrips() = runBlocking {
        val dao = FakeAsrWindowDao()
        val store = RoomAsrWindowStore(dao)
        store.save(listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "6.0.0", "123:456")))
        assertEquals(1, store.load("e1").size)
        assertEquals(60_000L, store.load("e1")[0].endMs)
    }
}

private class FakeAsrWindowDao : AsrWindowDao {
    private val rows = mutableListOf<AsrWindowEntity>()

    override suspend fun windows(episodeId: String): List<AsrWindowEntity> =
        rows.filter { it.episodeId == episodeId }

    override suspend fun upsert(windows: List<AsrWindowEntity>) {
        windows.forEach { window ->
            rows.removeAll { it.episodeId == window.episodeId && it.startMs == window.startMs }
            rows.add(window)
        }
    }
}

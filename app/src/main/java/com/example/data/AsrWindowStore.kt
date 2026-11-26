package com.example.data

/** The scan controller's view of the completed-window cache. */
interface AsrWindowStore {
    suspend fun load(episodeId: String): List<AsrWindowEntity>
    suspend fun save(windows: List<AsrWindowEntity>)
}

class RoomAsrWindowStore(private val dao: AsrWindowDao) : AsrWindowStore {
    override suspend fun load(episodeId: String): List<AsrWindowEntity> = dao.windows(episodeId)

    override suspend fun save(windows: List<AsrWindowEntity>) = dao.upsert(windows)
}

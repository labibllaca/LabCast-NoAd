package com.example.data

/** Room-backed implementation of [AdScanStore]. */
class RoomAdScanStore(private val dao: AdDao) : AdScanStore {

    override suspend fun scan(episodeId: String): EpisodeScanEntity? = dao.scan(episodeId)

    override suspend fun saveScan(scan: EpisodeScanEntity) = dao.upsertScan(scan)

    override suspend fun slots(podcastId: String): List<AdSlotEntity> = dao.slots(podcastId)

    override suspend fun saveSlots(podcastId: String, slots: List<AdSlotEntity>) {
        dao.deleteSlots(podcastId)
        if (slots.isNotEmpty()) dao.upsertSlots(slots)
    }

    override suspend fun asrSegments(episodeId: String): List<EpisodeAsrSegmentEntity> =
        dao.asrSegments(episodeId)

    override suspend fun upsertAsrSegments(segments: List<EpisodeAsrSegmentEntity>) {
        if (segments.isNotEmpty()) dao.upsertAsrSegments(segments)
    }

    override suspend fun adSegments(episodeId: String): List<EpisodeAdSegmentEntity> =
        dao.adSegments(episodeId)

    override suspend fun replaceAdSegments(
        episodeId: String,
        segments: List<EpisodeAdSegmentEntity>,
    ) {
        dao.deleteAdSegments(episodeId)
        if (segments.isNotEmpty()) dao.upsertAdSegments(segments)
    }

    override suspend fun saveTranscriptProjection(episodeId: String, text: String) {
        dao.updateTranscript(episodeId, text)
    }

    override suspend fun saveEpisodeDuration(episodeId: String, seconds: Long) {
        dao.updateDuration(episodeId, seconds)
    }
}

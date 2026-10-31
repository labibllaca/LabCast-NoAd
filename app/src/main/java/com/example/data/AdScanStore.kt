package com.example.data

/**
 * Persistence boundary used by the scan controller.
 *
 * Kept as an interface so the controller can be tested on the JVM with an
 * in-memory fake instead of a Room database.
 */
interface AdScanStore {
    suspend fun scan(episodeId: String): EpisodeScanEntity?
    suspend fun saveScan(scan: EpisodeScanEntity)
    suspend fun slots(podcastId: String): List<AdSlotEntity>
    suspend fun saveSlots(podcastId: String, slots: List<AdSlotEntity>)
    suspend fun asrSegments(episodeId: String): List<EpisodeAsrSegmentEntity>
    suspend fun upsertAsrSegments(segments: List<EpisodeAsrSegmentEntity>)
    suspend fun adSegments(episodeId: String): List<EpisodeAdSegmentEntity>
    suspend fun replaceAdSegments(episodeId: String, segments: List<EpisodeAdSegmentEntity>)
    suspend fun saveTranscriptProjection(episodeId: String, text: String)
    suspend fun saveEpisodeDuration(episodeId: String, seconds: Long)
}

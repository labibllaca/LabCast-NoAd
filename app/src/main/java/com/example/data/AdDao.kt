package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AdDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlots(slots: List<AdSlotEntity>)

    @Query("SELECT * FROM ad_slots WHERE podcastId = :podcastId ORDER BY slotIndex")
    suspend fun slots(podcastId: String): List<AdSlotEntity>

    @Query("DELETE FROM ad_slots WHERE podcastId = :podcastId")
    suspend fun deleteSlots(podcastId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdSegments(segments: List<EpisodeAdSegmentEntity>)

    @Query("SELECT * FROM episode_ad_segments WHERE episodeId = :episodeId ORDER BY startMs")
    suspend fun adSegments(episodeId: String): List<EpisodeAdSegmentEntity>

    @Query("SELECT * FROM episode_ad_segments WHERE id = :id")
    suspend fun adSegment(id: String): EpisodeAdSegmentEntity?

    @Query("DELETE FROM episode_ad_segments WHERE id = :id")
    suspend fun deleteAdSegment(id: String)

    @Query(
        "SELECT * FROM episode_ad_segments " +
            "WHERE episodeId = :episodeId AND startMs < :endMs AND endMs > :startMs ORDER BY startMs"
    )
    suspend fun adSegmentsOverlapping(
        episodeId: String,
        startMs: Long,
        endMs: Long,
    ): List<EpisodeAdSegmentEntity>

    @Query("DELETE FROM episode_ad_segments WHERE episodeId = :episodeId")
    suspend fun deleteAdSegments(episodeId: String)

    @Query("DELETE FROM episode_ad_segments WHERE episodeId = :episodeId AND source != 'USER'")
    suspend fun deleteNonUserAdSegments(episodeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsrSegments(segments: List<EpisodeAsrSegmentEntity>)

    @Query("SELECT * FROM episode_asr_segments WHERE episodeId = :episodeId ORDER BY startMs")
    suspend fun asrSegments(episodeId: String): List<EpisodeAsrSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScan(scan: EpisodeScanEntity)

    @Query("SELECT * FROM episode_scans WHERE episodeId = :episodeId")
    suspend fun scan(episodeId: String): EpisodeScanEntity?

    @Query("UPDATE episodes SET transcript = :text WHERE id = :episodeId")
    suspend fun updateTranscript(episodeId: String, text: String)

    @Query("UPDATE episodes SET durationSeconds = :seconds WHERE id = :episodeId")
    suspend fun updateDuration(episodeId: String, seconds: Long)
}

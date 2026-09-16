package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts")
    fun getAllPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getPodcastById(id: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcasts(podcasts: List<PodcastEntity>)

    @Update
    suspend fun updatePodcast(podcast: PodcastEntity)

    @Delete
    suspend fun deletePodcast(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcastById(podcastId: String)

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: String)

    @Query("SELECT * FROM episodes ORDER BY publishTimestamp DESC, publishDate DESC")
    fun getAllEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishTimestamp DESC, publishDate DESC")
    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1 ORDER BY publishTimestamp DESC, publishDate DESC")
    fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1")
    suspend fun getDownloadedEpisodesList(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Query("UPDATE episodes SET playbackPositionMs = :positionMs, isCompleted = :isCompleted WHERE id = :episodeId")
    suspend fun updatePlaybackProgress(episodeId: String, positionMs: Long, isCompleted: Boolean)

    @Query("UPDATE episodes SET playbackPositionMs = 0, isCompleted = 0")
    suspend fun clearAllHistory()

    @Query("UPDATE episodes SET playbackPositionMs = 0, isCompleted = 0 WHERE id = :episodeId")
    suspend fun clearEpisodeHistory(episodeId: String)

    @Query("UPDATE episodes SET isDownloaded = :isDownloaded, downloadLocalPath = :localPath, downloadTimestamp = :downloadTimestamp WHERE id = :episodeId")
    suspend fun updateDownloadStatus(episodeId: String, isDownloaded: Boolean, localPath: String?, downloadTimestamp: Long)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 50")
    fun getSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearSyncLogs()
}

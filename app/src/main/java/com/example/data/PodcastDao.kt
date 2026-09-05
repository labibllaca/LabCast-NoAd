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

    @Query("SELECT * FROM episodes")
    fun getAllEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishDate DESC")
    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1")
    fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>>

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

    @Query("UPDATE episodes SET isDownloaded = :isDownloaded, downloadLocalPath = :localPath WHERE id = :episodeId")
    suspend fun updateDownloadStatus(episodeId: String, isDownloaded: Boolean, localPath: String?)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 50")
    fun getSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearSyncLogs()
}

package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PodcastRepository(private val podcastDao: PodcastDao) {

    val allPodcasts: Flow<List<PodcastEntity>> = podcastDao.getAllPodcasts()
    val allEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getAllEpisodes()
    val downloadedEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getDownloadedEpisodes()
    val syncLogs: Flow<List<SyncLogEntity>> = podcastDao.getSyncLogs()

    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>> {
        return podcastDao.getEpisodesForPodcast(podcastId)
    }

    suspend fun getPodcastById(id: String): PodcastEntity? {
        return podcastDao.getPodcastById(id)
    }

    suspend fun getEpisodeById(id: String): EpisodeEntity? {
        return podcastDao.getEpisodeById(id)
    }

    suspend fun updateEpisodeProgress(episodeId: String, positionMs: Long, isCompleted: Boolean) {
        podcastDao.updatePlaybackProgress(episodeId, positionMs, isCompleted)
    }

    suspend fun updateLastPlayedAt(episodeId: String, timestamp: Long) {
        podcastDao.updateLastPlayedAt(episodeId, timestamp)
    }

    suspend fun clearAllHistory() {
        podcastDao.clearAllHistory()
    }

    suspend fun clearEpisodeHistory(episodeId: String) {
        podcastDao.clearEpisodeHistory(episodeId)
    }

    suspend fun updateDownloadStatus(episodeId: String, isDownloaded: Boolean, localPath: String?, downloadTimestamp: Long = if (isDownloaded) System.currentTimeMillis() else 0L) {
        podcastDao.updateDownloadStatus(episodeId, isDownloaded, localPath, downloadTimestamp)
    }

    suspend fun updateEpisode(episode: EpisodeEntity) {
        val ts = if (episode.publishTimestamp > 0L) episode.publishTimestamp else episode.getEffectiveTimestamp()
        podcastDao.updateEpisode(episode.copy(publishTimestamp = ts))
    }

    suspend fun addManualAdRange(episodeId: String, range: ManualAdRange) {
        val episode = podcastDao.getEpisodeById(episodeId) ?: return
        val durationMs = episode.durationSeconds.coerceAtLeast(0L) * 1000L
        val clamped = ManualAdRangeCodec.clamp(range, durationMs) ?: return
        val existing = ManualAdRangeCodec.decode(episode.manualAdRanges)
        if (clamped in existing) return

        podcastDao.updateManualAdRanges(
            episodeId = episodeId,
            encodedRanges = ManualAdRangeCodec.encode(existing + clamped)
        )
    }

    suspend fun updateManualAdRange(episodeId: String, target: ManualAdRange, replacement: ManualAdRange) {
        val episode = podcastDao.getEpisodeById(episodeId) ?: return
        val durationMs = episode.durationSeconds.coerceAtLeast(0L) * 1000L
        val clamped = ManualAdRangeCodec.clamp(replacement, durationMs) ?: return
        val existing = ManualAdRangeCodec.decode(episode.manualAdRanges)
        if (target !in existing) return
        val encoded = ManualAdRangeCodec.encode(existing.map { if (it == target) clamped else it }.distinct())
        if (encoded == episode.manualAdRanges) return
        podcastDao.updateManualAdRanges(episodeId, encoded)
    }

    suspend fun deleteManualAdRange(episodeId: String, target: ManualAdRange) {
        val episode = podcastDao.getEpisodeById(episodeId) ?: return
        val existing = ManualAdRangeCodec.decode(episode.manualAdRanges)
        if (target !in existing) return
        val encoded = ManualAdRangeCodec.encode(ManualAdRangeEditor.remove(existing, target))
        if (encoded == episode.manualAdRanges) return
        podcastDao.updateManualAdRanges(episodeId, encoded)
    }

    suspend fun updatePodcast(podcast: PodcastEntity) {
        podcastDao.updatePodcast(podcast)
    }

    suspend fun insertPodcast(podcast: PodcastEntity) {
        podcastDao.insertPodcasts(listOf(podcast))
    }

    suspend fun insertEpisodes(episodes: List<EpisodeEntity>) {
        val withTimestamps = episodes.map { ep ->
            val ts = if (ep.publishTimestamp > 0L) ep.publishTimestamp else ep.getEffectiveTimestamp()
            ep.copy(publishTimestamp = ts)
        }.sortedByDescending { it.publishTimestamp }
        podcastDao.insertEpisodes(withTimestamps)
    }

    /**
     * Checks whether a log message represents a genuine error, warning, or operational failure.
     * Routine user actions (play, pause, seek, language switch) are filtered out to keep logs clean.
     */
    fun isIssueOrError(deviceName: String, action: String): Boolean {
        val text = "$deviceName $action".lowercase()
        return text.contains("error") ||
               text.contains("fehler") ||
               text.contains("failed") ||
               text.contains("failure") ||
               text.contains("blocked") ||
               text.contains("exception") ||
               text.contains("timeout") ||
               text.contains("dropped") ||
               text.contains("warn") ||
               text.contains("issue") ||
               text.contains("interrupted") ||
               text.contains("disconnected") ||
               text.contains("corrupt") ||
               text.contains("unreachable") ||
               text.contains("problem")
    }

    suspend fun addSyncLog(deviceName: String, action: String, forceLog: Boolean = false) {
        // Only persist genuine issues and errors so Settings displays interaction issues
        if (forceLog || isIssueOrError(deviceName, action)) {
            podcastDao.insertSyncLog(SyncLogEntity(deviceName = deviceName, action = action))
        }
    }

    suspend fun addErrorLog(component: String, errorMessage: String, details: String? = null) {
        val fullMsg = if (details.isNullOrBlank()) "Fehler: $errorMessage" else "Fehler: $errorMessage [$details]"
        addSyncLog(component, fullMsg, forceLog = true)
    }

    suspend fun removePodcast(podcast: PodcastEntity) {
        podcastDao.deleteEpisodesForPodcast(podcast.id)
        podcastDao.deletePodcast(podcast)
    }

    suspend fun removePodcastById(podcastId: String) {
        podcastDao.deleteEpisodesForPodcast(podcastId)
        podcastDao.deletePodcastById(podcastId)
    }

    suspend fun clearSyncLogs() {
        podcastDao.clearSyncLogs()
    }

    // Population of Initial Rich Data
    /** No demo content: the app starts with only podcasts the user actually added. */
    suspend fun populateInitialDataIfNeeded(context: android.content.Context) {
        healExistingDataIfNeeded()
    }

    /** Keeps real feed data usable; no demo records are recreated or patched. */
    suspend fun healExistingDataIfNeeded() {
        try {
            podcastDao.getAllEpisodes().first().forEach { ep ->
                if (ep.publishTimestamp <= 0L && ep.publishDate.isNotBlank()) {
                    val ts = com.example.util.PodcastDateUtils.parseDateToTimestamp(ep.publishDate)
                    if (ts > 0L) podcastDao.updateEpisode(ep.copy(publishTimestamp = ts))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Data healing error: ${e.message}")
        }
    }

    suspend fun cleanupExpiredDownloads(maxAgeMillis: Long = 2 * 24 * 60 * 60 * 1000L): List<EpisodeEntity> {
        val cleanedList = mutableListOf<EpisodeEntity>()
        try {
            val downloadedEps = podcastDao.getDownloadedEpisodesList()
            val now = System.currentTimeMillis()
            for (ep in downloadedEps) {
                val isExpired = if (ep.downloadTimestamp > 0L) {
                    (now - ep.downloadTimestamp) > maxAgeMillis
                } else {
                    val file = ep.downloadLocalPath?.let { java.io.File(it) }
                    if (file != null && file.exists()) {
                        (now - file.lastModified()) > maxAgeMillis
                    } else false
                }

                if (isExpired) {
                    com.example.util.EpisodeDownloader.deleteDownloadedFile(ep.downloadLocalPath)
                    val updated = ep.copy(
                        isDownloaded = false,
                        downloadLocalPath = null,
                        downloadTimestamp = 0L
                    )
                    podcastDao.updateEpisode(updated)
                    cleanedList.add(ep)
                    addSyncLog(
                        "Smart Download",
                        "Download von '${ep.title}' automatisch gelöscht (Aufbewahrung > 2 Tage überschritten)."
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Failed to cleanup expired downloads: ${e.message}")
        }
        return cleanedList
    }

    suspend fun validateAndCleanupCorruptDownloads(context: android.content.Context) {
        try {
            val allEps = podcastDao.getAllEpisodes().first()
            for (ep in allEps) {
                if (ep.isDownloaded || !ep.downloadLocalPath.isNullOrEmpty()) {
                    val path = ep.downloadLocalPath
                    val file = if (!path.isNullOrEmpty()) java.io.File(path) else null
                    val isValid = file != null && file.exists() && file.length() >= 15000L
                    if (!isValid) {
                        if (file != null && file.exists()) {
                            try { file.delete() } catch (_: Exception) {}
                        }
                        podcastDao.updateEpisode(ep.copy(isDownloaded = false, downloadLocalPath = null))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Failed to validate downloads: ${e.message}")
        }
    }

    private suspend fun cleanUpLegacyDummyData() {
        val allEps = podcastDao.getAllEpisodes().first()
        val dummyUrls = listOf("soundhelix.com", "example.com")
        val replacementUrls = listOf(
            "https://traffic.megaphone.fm/SCIM7156610982.mp3",
            "https://traffic.megaphone.fm/SCIM7393383815.mp3",
            "https://traffic.megaphone.fm/SCIM2465421786.mp3",
            "https://traffic.megaphone.fm/SCIM3386045656.mp3"
        )
        for ((idx, ep) in allEps.withIndex()) {
            if (dummyUrls.any { ep.audioUrl.contains(it, ignoreCase = true) }) {
                val realUrl = replacementUrls[idx % replacementUrls.size]
                val updated = ep.copy(audioUrl = realUrl)
                podcastDao.updateEpisode(updated)
            }
        }
    }
}

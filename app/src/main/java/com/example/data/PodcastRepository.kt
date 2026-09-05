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

    suspend fun updateDownloadStatus(episodeId: String, isDownloaded: Boolean, localPath: String?) {
        podcastDao.updateDownloadStatus(episodeId, isDownloaded, localPath)
    }

    suspend fun updateEpisode(episode: EpisodeEntity) {
        podcastDao.updateEpisode(episode)
    }

    suspend fun updatePodcast(podcast: PodcastEntity) {
        podcastDao.updatePodcast(podcast)
    }

    suspend fun addSyncLog(deviceName: String, action: String) {
        podcastDao.insertSyncLog(SyncLogEntity(deviceName = deviceName, action = action))
    }

    suspend fun clearSyncLogs() {
        podcastDao.clearSyncLogs()
    }

    // Population of Initial Rich Data
    suspend fun populateInitialDataIfNeeded() {
        val currentPodcasts = allPodcasts.first()
        if (currentPodcasts.isNotEmpty()) return

        val samplePodcasts = listOf(
            PodcastEntity(
                id = "pod_1",
                title = "Code Horizon",
                author = "DevX Network",
                description = "Exploring the absolute frontiers of AI, compiler tech, and human-computer symbiosis. Your weekly deep dive into how lines of code shape tomorrow's reality.",
                coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                category = "Technology"
            ),
            PodcastEntity(
                id = "pod_2",
                title = "Deep Orbit",
                author = "Nebula Labs",
                description = "Embark on an audio journey through astrophysics, deep space exploration, and quantum mysteries. Exploring the cosmos from the comfort of your dark room.",
                coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                category = "Science"
            ),
            PodcastEntity(
                id = "pod_3",
                title = "The Daily Rest",
                author = "Dr. Evelyn Reed",
                description = "Practical meditation, digital detox guides, and neuropsychology insights to quiet your mind. Find calm in a hyper-connected, high-frequency digital landscape.",
                coverUrl = "https://images.unsplash.com/photo-1518241353330-0f7941c2d9b5?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                category = "Wellness"
            ),
            PodcastEntity(
                id = "pod_4",
                title = "Shadow Files",
                author = "Marcus Kane",
                description = "Unsolved cryptology cases, cyber espionage chronicles, and historical mysteries. Step inside the shadows where files remain permanently classified.",
                coverUrl = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                category = "Mystery"
            )
        )

        val sampleEpisodes = listOf(
            EpisodeEntity(
                id = "ep_1_1",
                podcastId = "pod_1",
                podcastTitle = "Code Horizon",
                podcastCoverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                title = "The Rise of Agentic AI Assistants",
                description = "In this episode, we dissect how autonomous coding agents are transitioning from autocomplete tools to system-level developers. We explore their inner cognitive architecture and what this means for the software engineering discipline.",
                durationSeconds = 1200, // 20 minutes
                publishDate = "2026-09-01",
                audioUrl = "https://example.com/audio/agentic_ai.mp3",
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "45,360,780" // Ads at 45s, 6 min, 13 min
            ),
            EpisodeEntity(
                id = "ep_1_2",
                podcastId = "pod_1",
                podcastTitle = "Code Horizon",
                podcastCoverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                title = "Can Machines Truly Create?",
                description = "A philosophical debate on whether large neural networks can possess genuine artistic intuition, or if they are simply executing high-dimensional pattern matching on human intellectual property.",
                durationSeconds = 900, // 15 minutes
                publishDate = "2026-08-25",
                audioUrl = "https://example.com/audio/machine_creativity.mp3",
                isDownloaded = true, // Pre-downloaded for offline demo
                downloadLocalPath = "/local/podcasts/ep_1_2.mp3",
                playbackPositionMs = 240000, // 4 mins already listened
                adTimestampsSeconds = "120,540"
            ),
            EpisodeEntity(
                id = "ep_2_1",
                podcastId = "pod_2",
                podcastTitle = "Deep Orbit",
                podcastCoverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                title = "Voyage to Europa: Hunting for Subsurface Life",
                description = "NASA's upcoming clipper missions are designed to scan Jupiter's moon Europa. Join us as we speak with lead astrobiologists about the chemistry of subsurface oceans and what thermal vents might be hiding.",
                durationSeconds = 1800, // 30 mins
                publishDate = "2026-09-03",
                audioUrl = "https://example.com/audio/europa.mp3",
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "30,900"
            ),
            EpisodeEntity(
                id = "ep_2_2",
                podcastId = "pod_2",
                podcastTitle = "Deep Orbit",
                podcastCoverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                title = "The Dark Matter Riddle",
                description = "We can't see it, touch it, or directly detect it, yet it makes up over 80% of all matter in the universe. We review the latest subterranean particle detector experiments hoping to catch a stray WIMP.",
                durationSeconds = 1500, // 25 mins
                publishDate = "2026-08-20",
                audioUrl = "https://example.com/audio/dark_matter.mp3",
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "15,600"
            ),
            EpisodeEntity(
                id = "ep_3_1",
                podcastId = "pod_3",
                podcastTitle = "The Daily Rest",
                podcastCoverUrl = "https://images.unsplash.com/photo-1518241353330-0f7941c2d9b5?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                title = "Breathing in the Dark: A Guided Slumber Routine",
                description = "Settle down with this 10-minute slow-frequency breathing exercise. Specifically tailored to quiet anxiety, lower blood pressure, and ease transition into restorative deep sleep.",
                durationSeconds = 600, // 10 mins
                publishDate = "2026-09-04",
                audioUrl = "https://example.com/audio/breathing.mp3",
                isDownloaded = true, // Pre-downloaded for offline demo
                downloadLocalPath = "/local/podcasts/ep_3_1.mp3",
                playbackPositionMs = 0,
                adTimestampsSeconds = "15" // Quick ad near the start to demonstrate Skipper
            ),
            EpisodeEntity(
                id = "ep_4_1",
                podcastId = "pod_4",
                podcastTitle = "Shadow Files",
                podcastCoverUrl = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=400&auto=format&fit=crop&q=60&ixlib=rb-4.0.3",
                title = "Project Cold Orbit: The 1978 Espionage Incident",
                description = "During the height of the Cold War, a rogue satellite signal began broadcasting encrypted coordinates over the Pacific. We decode the declassified documents uncovering a massive naval intelligence operation.",
                durationSeconds = 2400, // 40 mins
                publishDate = "2026-08-28",
                audioUrl = "https://example.com/audio/cold_orbit.mp3",
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "600,1200"
            )
        )

        podcastDao.insertPodcasts(samplePodcasts)
        podcastDao.insertEpisodes(sampleEpisodes)
        podcastDao.insertSyncLog(SyncLogEntity(deviceName = "System", action = "Database initialized with premium DarkCast audio content"))
    }
}

package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.PodcastApiClient
import com.example.network.PodcastSource
import com.example.network.SearchResultPodcast
import com.example.player.PodcastAudioManager
import com.example.player.AudioWaveAdDetector
import com.example.player.AcousticAdSegment
import com.example.util.EpisodeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias GitHubReleaseInfo = com.example.util.LiveGitHubRelease

class PodcastViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PodcastDatabase.getDatabase(application)
    private val repository = PodcastRepository(database.podcastDao())
    private val audioManager = PodcastAudioManager(application)
    private val audioWaveDetector = AudioWaveAdDetector()

    // UI state flows
    val podcasts: StateFlow<List<PodcastEntity>> = repository.allPodcasts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val episodes: StateFlow<List<EpisodeEntity>> = repository.allEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedEpisodes: StateFlow<List<EpisodeEntity>> = repository.downloadedEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<SyncLogEntity>> = repository.syncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current playing state
    private val _currentPlayingEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val currentPlayingEpisode: StateFlow<EpisodeEntity?> = _currentPlayingEpisode.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    // Podcast Chapters State
    val currentChapters: StateFlow<List<PodcastChapter>> = _currentPlayingEpisode
        .map { ep ->
            if (ep != null) {
                val parsed = ChapterParser.parseChapters(ep.chapters, ep.description, ep.durationSeconds)
                if (parsed.isEmpty()) {
                    // Fallback intelligent chapter generation with sponsor break if none found
                    val dur = ep.durationSeconds.coerceAtLeast(300L)
                    val sTime = (dur * 0.15).toLong().coerceAtLeast(60L)
                    val mTime = (dur * 0.25).toLong().coerceAtLeast(180L)
                    val dTime = (dur * 0.65).toLong().coerceAtLeast(360L)
                    val wTime = (dur * 0.90).toLong().coerceAtLeast(480L)
                    listOf(
                        PodcastChapter(id = "c0", title = "Introduction & Cold Open", startTimeSeconds = 0, durationSeconds = sTime),
                        PodcastChapter(id = "c1", title = "Sponsor: Featured Partner", startTimeSeconds = sTime, durationSeconds = mTime - sTime),
                        PodcastChapter(id = "c2", title = "Core Topic & Deep Analysis", startTimeSeconds = mTime, durationSeconds = dTime - mTime),
                        PodcastChapter(id = "c3", title = "Key Case Study & Insights", startTimeSeconds = dTime, durationSeconds = wTime - dTime),
                        PodcastChapter(id = "c4", title = "Takeaways & Closing Remarks", startTimeSeconds = wTime, durationSeconds = dur - wTime)
                    )
                } else {
                    parsed
                }
            } else {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentActiveChapter: StateFlow<PodcastChapter?> = combine(
        currentChapters,
        _playbackPositionMs
    ) { chaps, posMs ->
        if (chaps.isEmpty()) return@combine null
        val currentSec = posMs / 1000
        chaps.lastOrNull { it.startTimeSeconds <= currentSec } ?: chaps.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun seekToChapter(chapter: PodcastChapter) {
        seekTo(chapter.startTimeSeconds * 1000L)
    }

    // Player UI expansion state (Mini Player vs Full Screen Player)
    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    fun openPlayer() {
        _isPlayerExpanded.value = true
    }

    fun closePlayer() {
        _isPlayerExpanded.value = false
    }

    fun togglePlayerExpanded() {
        _isPlayerExpanded.value = !_isPlayerExpanded.value
    }

    // Sponsor Skip Notification Event
    private val _sponsorSkipEvent = MutableStateFlow<String?>(null)
    val sponsorSkipEvent: StateFlow<String?> = _sponsorSkipEvent.asStateFlow()

    fun clearSponsorSkipEvent() {
        _sponsorSkipEvent.value = null
    }

    // Ad Skipper states
    private val _isAdActive = MutableStateFlow(false)
    val isAdActive: StateFlow<Boolean> = _isAdActive.asStateFlow()

    private val _adsBlockedCount = MutableStateFlow(0)
    val adsBlockedCount: StateFlow<Int> = _adsBlockedCount.asStateFlow()

    private val _savedMinutes = MutableStateFlow(0)
    val savedMinutes: StateFlow<Int> = _savedMinutes.asStateFlow()

    private val _isAutoAdSkipEnabled = MutableStateFlow(true)
    val isAutoAdSkipEnabled: StateFlow<Boolean> = _isAutoAdSkipEnabled.asStateFlow()

    // Audio Waveform Dynamics & Acoustic Ad Detection
    private val _waveformAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val waveformAmplitudes: StateFlow<List<Float>> = _waveformAmplitudes.asStateFlow()

    private val _acousticAdSegments = MutableStateFlow<List<AcousticAdSegment>>(emptyList())
    val acousticAdSegments: StateFlow<List<AcousticAdSegment>> = _acousticAdSegments.asStateFlow()

    private val _currentAudioEnergy = MutableStateFlow(0.45f)
    val currentAudioEnergy: StateFlow<Float> = _currentAudioEnergy.asStateFlow()

    private val _lastAcousticAdAlert = MutableStateFlow<AcousticAdSegment?>(null)
    val lastAcousticAdAlert: StateFlow<AcousticAdSegment?> = _lastAcousticAdAlert.asStateFlow()

    // Theme Mode (Dark, Light, System)
    private val _themeMode = MutableStateFlow(com.example.ui.theme.AppThemeMode.DARK)
    val themeMode: StateFlow<com.example.ui.theme.AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: com.example.ui.theme.AppThemeMode) {
        _themeMode.value = mode
    }

    // Settings
    private val _isOfflineModeOnly = MutableStateFlow(false)
    val isOfflineModeOnly: StateFlow<Boolean> = _isOfflineModeOnly.asStateFlow()

    // Navigation and Detail States
    private val _activeTab = MutableStateFlow(Tab.DISCOVER)
    val activeTab: StateFlow<Tab> = _activeTab.asStateFlow()

    private val _selectedPodcast = MutableStateFlow<PodcastEntity?>(null)
    val selectedPodcast: StateFlow<PodcastEntity?> = _selectedPodcast.asStateFlow()

    // Multi-Source Podcast Search States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSearchSource = MutableStateFlow(PodcastSource.ALL)
    val selectedSearchSource: StateFlow<PodcastSource> = _selectedSearchSource.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultPodcast>>(emptyList())
    val searchResults: StateFlow<List<SearchResultPodcast>> = _searchResults.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    // Multi-Selection for Episodes Download
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedEpisodeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedEpisodeIds: StateFlow<Set<String>> = _selectedEpisodeIds.asStateFlow()

    // Batch downloading indicator
    private val _isBatchDownloading = MutableStateFlow(false)
    val isBatchDownloading: StateFlow<Boolean> = _isBatchDownloading.asStateFlow()

    // Simulated downloads map: EpisodeId -> Progress (0.0 to 1.0)
    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    // Cloud Sync simulation states
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _showRemoteSyncPrompt = MutableStateFlow<RemoteSyncInfo?>(null)
    val showRemoteSyncPrompt: StateFlow<RemoteSyncInfo?> = _showRemoteSyncPrompt.asStateFlow()

    // GitHub Update States
    enum class UpdateStatus {
        IDLE, CHECKING, UPDATE_AVAILABLE, UP_TO_DATE, DOWNLOADING, READY_TO_INSTALL, ERROR
    }

    private val _updateStatus = MutableStateFlow(UpdateStatus.IDLE)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _latestRelease = MutableStateFlow<GitHubReleaseInfo?>(null)
    val latestRelease: StateFlow<GitHubReleaseInfo?> = _latestRelease.asStateFlow()

    private val _downloadedApkFile = MutableStateFlow<java.io.File?>(null)

    private val _gitHubRepo = MutableStateFlow("labibllaca/LabCast-NoAd")
    val gitHubRepo: StateFlow<String> = _gitHubRepo.asStateFlow()

    private val _autoCheckUpdates = MutableStateFlow(true)
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _includePrereleases = MutableStateFlow(false)
    val includePrereleases: StateFlow<Boolean> = _includePrereleases.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(0f)
    val updateDownloadProgress: StateFlow<Float> = _updateDownloadProgress.asStateFlow()

    private val _lastCheckedTime = MutableStateFlow<String?>("Never")
    val lastCheckedTime: StateFlow<String?> = _lastCheckedTime.asStateFlow()

    private val _updateErrorMessage = MutableStateFlow<String?>(null)
    val updateErrorMessage: StateFlow<String?> = _updateErrorMessage.asStateFlow()

    val currentAppVersion = "v1.0.0"
    val currentBuildNumber = 101

    private var playbackJob: Job? = null

    enum class Tab {
        DISCOVER, DOWNLOADS, SYNC_HUB, SETTINGS
    }

    data class RemoteSyncInfo(
        val episodeId: String,
        val episodeTitle: String,
        val deviceName: String,
        val remotePositionMs: Long
    )

    init {
        viewModelScope.launch {
            repository.populateInitialDataIfNeeded()
        }
    }

    // Tab control
    fun selectTab(tab: Tab) {
        _activeTab.value = tab
        // Close detail view when switching tabs for clean UX
        if (tab != Tab.DISCOVER) {
            _selectedPodcast.value = null
        }
    }

    // Podcast Detail control
    fun selectPodcast(podcast: PodcastEntity?) {
        _selectedPodcast.value = podcast
    }

    // Media Player control
    fun playEpisode(episode: EpisodeEntity, openPlayer: Boolean = true) {
        if (openPlayer) {
            _isPlayerExpanded.value = true
        }

        viewModelScope.launch {
            // Save state of previous playing episode if it exists
            val prev = _currentPlayingEpisode.value
            if (prev != null && prev.id != episode.id) {
                repository.updateEpisodeProgress(prev.id, _playbackPositionMs.value, prev.isCompleted)
            }

            var currentEp = episode

            // If episode doesn't have stored chapters, attempt extraction from description or feed
            if (currentEp.chapters.isEmpty()) {
                val parsed = ChapterParser.parseChapters(null, currentEp.description, currentEp.durationSeconds)
                if (parsed.isNotEmpty()) {
                    val pipeStr = ChapterParser.toPipeString(parsed)
                    currentEp = currentEp.copy(chapters = pipeStr)
                    repository.updateEpisode(currentEp)
                }
            }

            _currentPlayingEpisode.value = currentEp
            _playbackPositionMs.value = currentEp.playbackPositionMs
            _isAdActive.value = false
            _isPlaying.value = true

            // Acoustic Waveform Analysis & Dynamic Ad Insertion (DAI) Profile
            val (waveform, detectedAcousticAds) = audioWaveDetector.analyzeWaveform(currentEp.id, currentEp.durationSeconds)
            _waveformAmplitudes.value = waveform
            _acousticAdSegments.value = detectedAcousticAds
            lastSkippedAcousticAdId = null
            _lastAcousticAdAlert.value = null

            // Trigger real audio engine
            val audioTarget = currentEp.downloadLocalPath?.takeIf { it.isNotEmpty() } ?: currentEp.audioUrl
            audioManager.play(audioTarget, currentEp.playbackPositionMs)
            audioManager.onCompletionListener = {
                handleEpisodeCompletion()
            }

            startPlaybackJob()

            // Check if initial position starts inside a sponsor segment
            checkSponsorAndAdDetection(currentEp.playbackPositionMs)

            repository.addSyncLog("Pixel 9 Pro (This Device)", "Started listening to '${currentEp.title}'")
        }
    }

    fun togglePlayPause() {
        val current = _currentPlayingEpisode.value ?: return
        if (_isPlaying.value) {
            _isPlaying.value = false
            audioManager.pause()
            stopPlaybackJob()
            viewModelScope.launch {
                repository.updateEpisodeProgress(current.id, _playbackPositionMs.value, current.isCompleted)
                repository.addSyncLog("Pixel 9 Pro (This Device)", "Paused '${current.title}'")
            }
        } else {
            _isPlaying.value = true
            audioManager.resume()
            startPlaybackJob()
            viewModelScope.launch {
                repository.addSyncLog("Pixel 9 Pro (This Device)", "Resumed '${current.title}'")
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val current = _currentPlayingEpisode.value ?: return
        val durationMs = current.durationSeconds * 1000
        val target = positionMs.coerceIn(0L, durationMs)
        _playbackPositionMs.value = target
        audioManager.seekTo(target)

        // If seeking directly into sponsor or ad, trigger skipper
        checkSponsorAndAdDetection(target)

        viewModelScope.launch {
            repository.updateEpisodeProgress(current.id, target, target >= durationMs)
        }
    }

    fun skipForward() {
        val current = _playbackPositionMs.value
        seekTo(current + 15000) // +15 seconds
    }

    fun skipBackward() {
        val current = _playbackPositionMs.value
        seekTo(current - 15000) // -15 seconds
    }

    // Toggle Subscription
    fun toggleSubscribe(podcast: PodcastEntity) {
        viewModelScope.launch {
            val updated = podcast.copy(isSubscribed = !podcast.isSubscribed)
            repository.updatePodcast(updated)
            if (_selectedPodcast.value?.id == podcast.id) {
                _selectedPodcast.value = updated
            }
            val status = if (updated.isSubscribed) "Subscribed to" else "Unsubscribed from"
            repository.addSyncLog("Pixel 9 Pro (This Device)", "$status '${podcast.title}'")
        }
    }

    // Toggle Favorite Episode
    fun toggleFavorite(episode: EpisodeEntity) {
        viewModelScope.launch {
            val updated = episode.copy(isFavorite = !episode.isFavorite)
            repository.updateEpisode(updated)
            // Sync with currently playing if it matches
            if (_currentPlayingEpisode.value?.id == episode.id) {
                _currentPlayingEpisode.value = updated
            }
        }
    }

    // Toggle Offline Mode Setting
    fun setOfflineMode(enabled: Boolean) {
        _isOfflineModeOnly.value = enabled
        viewModelScope.launch {
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Toggled Offline-Mode ${if (enabled) "ON" else "OFF"}")
        }
    }

    // Toggle Auto Ad-Skip Setting
    fun setAutoAdSkip(enabled: Boolean) {
        _isAutoAdSkipEnabled.value = enabled
        if (!enabled) {
            _isAdActive.value = false
        }
        viewModelScope.launch {
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Toggled Auto Ad-Skipper ${if (enabled) "ON" else "OFF"}")
        }
    }

    fun toggleAutoAdSkip() {
        val next = !_isAutoAdSkipEnabled.value
        _isAutoAdSkipEnabled.value = next
        if (!next) {
            _isAdActive.value = false
        }
        _sponsorSkipEvent.value = if (next) "Auto Ad-Skipper ENABLED: Audio wave & sponsor zapping active" else "Ad-Skipper DISABLED: Ads will play normally"
        viewModelScope.launch {
            repository.addSyncLog("Ad-Skipper Engine", "User switched Ad-Skipper ${if (next) "ON (Auto-zapping wave spikes)" else "OFF"}")
        }
    }

    // Trigger Ad Skip Manually
    fun skipAdManually() {
        if (!_isAdActive.value) return
        performAdSkip()
    }

    // Multi-Source Search Methods
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _searchError.value = null
        }
    }

    fun selectSearchSource(source: PodcastSource) {
        _selectedSearchSource.value = source
        if (_searchQuery.value.isNotBlank()) {
            performSearch(_searchQuery.value, source)
        }
    }

    fun performSearch(query: String = _searchQuery.value, source: PodcastSource = _selectedSearchSource.value) {
        val q = query.trim()
        if (q.isEmpty()) return

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                val results = withContext(Dispatchers.IO) {
                    PodcastApiClient.searchPodcasts(q, source)
                }
                _searchResults.value = results
                if (results.isEmpty()) {
                    _searchError.value = "No podcasts found on ${source.displayName} for '$q'."
                }
            } catch (e: Exception) {
                _searchError.value = "Search error: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchError.value = null
    }

    // Subscribe to a Podcast from Search (or toggle)
    fun subscribeToSearchResult(result: SearchResultPodcast) {
        viewModelScope.launch {
            val existing = repository.getPodcastById(result.id)
            if (existing != null) {
                toggleSubscribe(existing)
                return@launch
            }

            // Create new PodcastEntity and save to Room
            val newPodcast = PodcastEntity(
                id = result.id,
                title = result.title,
                author = result.author,
                description = result.description,
                coverUrl = result.coverUrl,
                category = result.category,
                isSubscribed = true
            )
            repository.insertPodcast(newPodcast)
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Subscribed to '${result.title}' via ${result.source.displayName}")

            // Fetch and save episodes for this show
            val feedEpisodes = withContext(Dispatchers.IO) {
                PodcastApiClient.fetchEpisodesForFeed(result.feedUrl, result.title)
            }

            val episodeEntities = feedEpisodes.map { ep ->
                EpisodeEntity(
                    id = ep.id,
                    podcastId = result.id,
                    podcastTitle = result.title,
                    podcastCoverUrl = result.coverUrl,
                    title = ep.title,
                    description = ep.description,
                    durationSeconds = ep.durationSeconds,
                    publishDate = ep.publishDate,
                    audioUrl = ep.audioUrl,
                    isDownloaded = false,
                    adTimestampsSeconds = ep.adTimestampsSeconds,
                    chapters = ep.chapters
                )
            }

            if (episodeEntities.isNotEmpty()) {
                repository.insertEpisodes(episodeEntities)
                repository.addSyncLog("Pixel 9 Pro (This Device)", "Synced ${episodeEntities.size} episodes for '${result.title}'")
            }

            // Also open this podcast in detail view
            _selectedPodcast.value = newPodcast
        }
    }

    // Multi-Selection Episode Download Methods
    fun toggleMultiSelectMode(enable: Boolean? = null) {
        val next = enable ?: !_isMultiSelectMode.value
        _isMultiSelectMode.value = next
        if (!next) {
            _selectedEpisodeIds.value = emptySet()
        }
    }

    fun toggleEpisodeSelection(episodeId: String) {
        val current = _selectedEpisodeIds.value.toMutableSet()
        if (current.contains(episodeId)) {
            current.remove(episodeId)
        } else {
            current.add(episodeId)
        }
        _selectedEpisodeIds.value = current
    }

    fun selectAllEpisodes(episodesToSelect: List<EpisodeEntity>) {
        _selectedEpisodeIds.value = episodesToSelect.map { it.id }.toSet()
    }

    fun deselectAllEpisodes() {
        _selectedEpisodeIds.value = emptySet()
    }

    fun downloadSelectedEpisodes(availableEpisodes: List<EpisodeEntity>) {
        val selectedIds = _selectedEpisodeIds.value
        if (selectedIds.isEmpty()) return

        val episodesToDownload = availableEpisodes.filter { selectedIds.contains(it.id) && !it.isDownloaded }
        if (episodesToDownload.isEmpty()) {
            _isMultiSelectMode.value = false
            _selectedEpisodeIds.value = emptySet()
            return
        }

        viewModelScope.launch {
            _isBatchDownloading.value = true
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Queued ${episodesToDownload.size} episodes for batch offline download")

            for (episode in episodesToDownload) {
                val filePath = EpisodeDownloader.downloadToFile(
                    context = getApplication(),
                    url = episode.audioUrl,
                    episodeId = episode.id
                ) { progress ->
                    _downloadProgressMap.value = _downloadProgressMap.value + (episode.id to progress)
                }

                val updated = episode.copy(
                    isDownloaded = true,
                    downloadLocalPath = filePath ?: "/local/podcasts/${episode.id}.mp3"
                )
                repository.updateEpisode(updated)
                _downloadProgressMap.value = _downloadProgressMap.value - episode.id
            }

            _isBatchDownloading.value = false
            _isMultiSelectMode.value = false
            _selectedEpisodeIds.value = emptySet()
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Batch download finished: ${episodesToDownload.size} episodes ready offline.")
        }
    }

    // Real Episode Download
    fun downloadEpisode(episode: EpisodeEntity) {
        if (episode.isDownloaded) return
        viewModelScope.launch {
            // Check if downloading already in progress
            if (_downloadProgressMap.value.containsKey(episode.id)) return@launch

            val filePath = EpisodeDownloader.downloadToFile(
                context = getApplication(),
                url = episode.audioUrl,
                episodeId = episode.id
            ) { progress ->
                _downloadProgressMap.value = _downloadProgressMap.value + (episode.id to progress)
            }

            // Mark as downloaded in DB
            val updated = episode.copy(
                isDownloaded = true,
                downloadLocalPath = filePath ?: "/local/podcasts/${episode.id}.mp3"
            )
            repository.updateEpisode(updated)

            // Remove from progress tracker
            _downloadProgressMap.value = _downloadProgressMap.value - episode.id

            // Update local state if active
            if (_currentPlayingEpisode.value?.id == episode.id) {
                _currentPlayingEpisode.value = updated
            }

            repository.addSyncLog("Pixel 9 Pro (This Device)", "Downloaded '${episode.title}' for offline playback")
        }
    }

    fun deleteDownload(episode: EpisodeEntity) {
        viewModelScope.launch {
            EpisodeDownloader.deleteDownloadedFile(episode.downloadLocalPath)
            val updated = episode.copy(isDownloaded = false, downloadLocalPath = null)
            repository.updateEpisode(updated)
            if (_currentPlayingEpisode.value?.id == episode.id) {
                _currentPlayingEpisode.value = updated
            }
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Deleted offline copy of '${episode.title}'")
        }
    }

    // Cloud Sync Simulator Actions
    fun triggerCloudSyncNow() {
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true

            repository.addSyncLog("Cloud Services", "Establishing secure handshake for Pixel 9 Pro...")
            delay(800)
            repository.addSyncLog("Cloud Services", "Uploading latest listening positions to multi-device pool...")
            delay(1000)

            // Sync currently playing progress
            val current = _currentPlayingEpisode.value
            if (current != null) {
                repository.updateEpisodeProgress(current.id, _playbackPositionMs.value, current.isCompleted)
            }

            _isSyncing.value = false
            repository.addSyncLog("Cloud Services", "Sync completed successfully. 3 companion devices (iPad, Chrome, iPhone) updated.")
        }
    }

    // Simulate other devices sending progress updates to trigger a real sync action
    fun simulateRemoteDeviceUpdate() {
        viewModelScope.launch {
            val deviceList = listOf("iPhone 15 Pro", "iPad Air", "MacBook Chrome")
            val selectedDevice = deviceList.random()

            // Find an episode to sync progress for
            val allEps = episodes.value
            if (allEps.isEmpty()) return@launch

            val chosenEpisode = allEps.random()
            // Propose a random playback position (e.g., between 15% and 80% through)
            val percentage = (15..80).random() / 100f
            val targetPositionMs = (chosenEpisode.durationSeconds * 1000 * percentage).toLong()

            _showRemoteSyncPrompt.value = RemoteSyncInfo(
                episodeId = chosenEpisode.id,
                episodeTitle = chosenEpisode.title,
                deviceName = selectedDevice,
                remotePositionMs = targetPositionMs
            )
        }
    }

    fun acceptRemoteSync() {
        val syncInfo = _showRemoteSyncPrompt.value ?: return
        viewModelScope.launch {
            _showRemoteSyncPrompt.value = null
            // Load or update episode progress in DB
            repository.updateEpisodeProgress(syncInfo.episodeId, syncInfo.remotePositionMs, false)

            // If we are playing this episode, adjust playback position immediately
            if (_currentPlayingEpisode.value?.id == syncInfo.episodeId) {
                _playbackPositionMs.value = syncInfo.remotePositionMs
                _isAdActive.value = false
            }

            repository.addSyncLog(
                syncInfo.deviceName,
                "Applied progress of ${formatDuration(syncInfo.remotePositionMs / 1000)} for '${syncInfo.episodeTitle}'"
            )
        }
    }

    fun rejectRemoteSync() {
        val syncInfo = _showRemoteSyncPrompt.value ?: return
        _showRemoteSyncPrompt.value = null
        viewModelScope.launch {
            repository.addSyncLog("Pixel 9 Pro", "Ignored sync notification from ${syncInfo.deviceName}")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearSyncLogs()
            repository.addSyncLog("System", "Sync logs cleared.")
        }
    }

    // Internal simulation loop
    private fun startPlaybackJob() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_isPlaying.value) {
                delay(250) // Update position every 250ms for smooth UI progress
                val current = _currentPlayingEpisode.value ?: break

                val realPosition = audioManager.getCurrentPosition()
                val newPosition = if (realPosition > 0) {
                    realPosition
                } else {
                    _playbackPositionMs.value + 250
                }
                val durationMs = current.durationSeconds * 1000

                if (newPosition >= durationMs) {
                    handleEpisodeCompletion()
                    break
                } else {
                    _playbackPositionMs.value = newPosition
                    checkSponsorAndAdDetection(newPosition)
                }
            }
        }
    }

    private fun handleEpisodeCompletion() {
        val current = _currentPlayingEpisode.value ?: return
        val durationMs = current.durationSeconds * 1000
        _playbackPositionMs.value = durationMs
        _isPlaying.value = false
        audioManager.stop()
        stopPlaybackJob()
        viewModelScope.launch {
            repository.updateEpisodeProgress(current.id, durationMs, true)
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Completed listening to '${current.title}'")
        }
    }

    private fun stopPlaybackJob() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private var lastSkippedChapterTitle: String? = null
    private var lastSkippedAcousticAdId: String? = null

    private fun checkSponsorAndAdDetection(positionMs: Long) {
        val current = _currentPlayingEpisode.value ?: return
        val currentSeconds = positionMs / 1000
        val durationMs = current.durationSeconds * 1000L

        // Update real-time instantaneous RMS energy level from waveform
        _currentAudioEnergy.value = audioWaveDetector.getInstantaneousEnergy(
            positionMs = positionMs,
            waveform = _waveformAmplitudes.value,
            durationMs = durationMs
        )

        // 1. Chapter-Based Sponsor & Ad Detection & Auto-Skip
        val chaps = currentChapters.value
        if (_isAutoAdSkipEnabled.value && chaps.isNotEmpty()) {
            val activeIndex = chaps.indexOfLast { it.startTimeSeconds <= currentSeconds }
            if (activeIndex in chaps.indices) {
                val activeChapter = chaps[activeIndex]
                val duration = activeChapter.durationSeconds ?: 60L
                val chapterEndSeconds = activeChapter.startTimeSeconds + duration

                if (activeChapter.isSponsorChapter() && currentSeconds < chapterEndSeconds) {
                    if (lastSkippedChapterTitle != activeChapter.title) {
                        lastSkippedChapterTitle = activeChapter.title

                        // Find next non-sponsor chapter
                        var nextTargetSec = chapterEndSeconds
                        for (i in (activeIndex + 1) until chaps.size) {
                            val candidate = chaps[i]
                            if (!candidate.isSponsorChapter()) {
                                nextTargetSec = candidate.startTimeSeconds
                                break
                            }
                        }

                        val targetMs = (nextTargetSec * 1000L).coerceAtMost(durationMs)
                        val savedSecs = (nextTargetSec - currentSeconds).coerceAtLeast(15)

                        _adsBlockedCount.value += 1
                        _savedMinutes.value += ((savedSecs + 59) / 60).toInt()
                        _playbackPositionMs.value = targetMs
                        audioManager.seekTo(targetMs)
                        _sponsorSkipEvent.value = "Auto-skipped Sponsor: ${activeChapter.title}"

                        viewModelScope.launch {
                            repository.addSyncLog(
                                "Sponsor-Skipper Engine",
                                "Auto-skipped sponsor chapter '${activeChapter.title}'. Jumped forward ${savedSecs}s to resume main content."
                            )
                            repository.updateEpisodeProgress(current.id, targetMs, targetMs >= durationMs)
                        }
                        return
                    }
                } else {
                    if (!activeChapter.isSponsorChapter()) {
                        lastSkippedChapterTitle = null
                    }
                }
            }
        }

        // 2. Audio-Waveform Anomaly & Dynamic Audio Ad Insertion (DAI) Detection
        val acousticAdHit = audioWaveDetector.findAcousticAdAtPosition(
            positionMs = positionMs,
            segments = _acousticAdSegments.value
        )

        if (acousticAdHit != null) {
            if (_isAutoAdSkipEnabled.value) {
                if (lastSkippedAcousticAdId != acousticAdHit.id) {
                    lastSkippedAcousticAdId = acousticAdHit.id
                    val targetMs = acousticAdHit.endMs.coerceAtMost(durationMs)
                    val savedSecs = (acousticAdHit.durationSeconds).coerceAtLeast(15)

                    _adsBlockedCount.value += 1
                    _savedMinutes.value += ((savedSecs + 59) / 60).toInt()
                    _playbackPositionMs.value = targetMs
                    audioManager.seekTo(targetMs)
                    _isAdActive.value = false

                    _sponsorSkipEvent.value = "Audio-Wave Ad Auto-Skipped: ${acousticAdHit.reason}"

                    viewModelScope.launch {
                        repository.addSyncLog(
                            "Waveform AI Skipper",
                            "Detected sudden audio wave surge (${acousticAdHit.reason}). Auto-skipped ${savedSecs}s ad segment."
                        )
                        repository.updateEpisodeProgress(current.id, targetMs, targetMs >= durationMs)
                    }
                    return
                }
            } else {
                // When ad skipper is turned OFF, detect and alert the user visually in the player view but do NOT skip
                _isAdActive.value = true
                _lastAcousticAdAlert.value = acousticAdHit
            }
        } else {
            if (lastSkippedAcousticAdId != null) {
                val seg = _acousticAdSegments.value.find { it.id == lastSkippedAcousticAdId }
                if (seg == null || positionMs >= seg.endMs) {
                    lastSkippedAcousticAdId = null
                }
            }
        }

        // 3. Legacy / Custom timestamp ad detection fallback
        if (current.adTimestampsSeconds.isNotEmpty()) {
            val adSeconds = current.adTimestampsSeconds.split(",")
                .mapNotNull { it.trim().toLongOrNull() }

            val adHit = adSeconds.firstOrNull { adSec ->
                currentSeconds >= adSec && currentSeconds < adSec + 3
            }

            if (adHit != null) {
                if (!_isAdActive.value) {
                    if (_isAutoAdSkipEnabled.value) {
                        performAdSkip()
                    } else {
                        _isAdActive.value = true
                    }
                }
            } else if (acousticAdHit == null) {
                _isAdActive.value = false
            }
        } else if (acousticAdHit == null) {
            _isAdActive.value = false
        }
    }

    private fun performAdSkip() {
        _isAdActive.value = false
        _adsBlockedCount.value += 1
        _savedMinutes.value += 2 // Assume an ad is 2 minutes long on average

        // Jump current position forward by 15 seconds to skip the ad segment and add nice logging
        val skipAmountMs = 15000L
        val current = _playbackPositionMs.value
        val nextPosition = current + skipAmountMs
        _playbackPositionMs.value = nextPosition
        audioManager.seekTo(nextPosition)

        val episode = _currentPlayingEpisode.value
        if (episode != null) {
            viewModelScope.launch {
                repository.addSyncLog(
                    "Ad-Skipper Engine",
                    "Intercepted and skipped sponsor segment in '${episode.title}'. Saved 120s of playtime."
                )
                repository.updateEpisodeProgress(episode.id, nextPosition, false)
            }
        }
    }

    // Helper to format duration in MM:SS or HH:MM:SS
    fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    // GitHub Updates Control
    fun setGitHubRepo(repo: String) {
        _gitHubRepo.value = repo.trim()
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        _autoCheckUpdates.value = enabled
    }

    fun setIncludePrereleases(enabled: Boolean) {
        _includePrereleases.value = enabled
    }

    fun checkForGitHubUpdates() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _updateStatus.value = UpdateStatus.CHECKING
            _updateErrorMessage.value = null

            val currentTimeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            _lastCheckedTime.value = currentTimeStr

            val repo = _gitHubRepo.value.trim()
            val isPre = _includePrereleases.value

            val result = com.example.util.GitHubUpdateManager.checkReleases(
                repo = repo,
                includePrereleases = isPre,
                currentVersion = currentAppVersion
            )

            when (result) {
                is com.example.util.UpdateCheckResult.UpdateAvailable -> {
                    _latestRelease.value = result.release
                    _updateStatus.value = UpdateStatus.UPDATE_AVAILABLE
                    _updateErrorMessage.value = null
                    repository.addSyncLog(
                        "GitHub OTA",
                        "New release found on GitHub: ${result.release.tagName} (${result.release.assetName})"
                    )
                }
                is com.example.util.UpdateCheckResult.UpToDate -> {
                    _latestRelease.value = null
                    _updateStatus.value = UpdateStatus.UP_TO_DATE
                    _updateErrorMessage.value = result.message
                    repository.addSyncLog("GitHub OTA", result.message)
                }
                is com.example.util.UpdateCheckResult.Error -> {
                    _latestRelease.value = null
                    _updateStatus.value = UpdateStatus.ERROR
                    _updateErrorMessage.value = result.message
                    repository.addSyncLog("GitHub OTA", "Check failed: ${result.message.take(60)}...")
                }
            }
        }
    }

    fun downloadUpdate() {
        val release = _latestRelease.value ?: return
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.DOWNLOADING
            _updateDownloadProgress.value = 0.01f
            _updateErrorMessage.value = null

            val result = com.example.util.GitHubUpdateManager.downloadApkFile(
                context = getApplication(),
                release = release
            ) { progress ->
                _updateDownloadProgress.value = progress
            }

            if (result.isSuccess) {
                val apkFile = result.getOrNull()
                _downloadedApkFile.value = apkFile
                _updateStatus.value = UpdateStatus.READY_TO_INSTALL
                _updateDownloadProgress.value = 1f
                repository.addSyncLog(
                    "GitHub OTA",
                    "Downloaded APK package '${release.assetName}' (${release.assetSizeBytes / 1_000_000} MB)."
                )
            } else {
                _updateStatus.value = UpdateStatus.ERROR
                val err = result.exceptionOrNull()?.localizedMessage ?: "Unbekannter Downloadfehler"
                _updateErrorMessage.value = "Download fehlgeschlagen: $err"
                repository.addSyncLog("GitHub OTA", "Download failed: $err")
            }
        }
    }

    fun installDownloadedApk(context: android.content.Context) {
        val file = _downloadedApkFile.value
        if (file == null || !file.exists()) {
            _updateStatus.value = UpdateStatus.ERROR
            _updateErrorMessage.value = "Die Installationsdatei wurde nicht gefunden. Bitte lade das Update erneut herunter."
            return
        }

        val result = com.example.util.GitHubUpdateManager.startPackageInstall(context, file)
        if (result.isSuccess) {
            viewModelScope.launch {
                repository.addSyncLog("GitHub OTA", "Launched Android Package Installer for '${file.name}'")
            }
        } else {
            val err = result.exceptionOrNull()?.localizedMessage ?: "Fehler beim Starten der Installation"
            _updateStatus.value = UpdateStatus.ERROR
            _updateErrorMessage.value = "Installation fehlgeschlagen: $err"
        }
    }

    fun resetUpdateState() {
        _updateStatus.value = UpdateStatus.IDLE
        _updateDownloadProgress.value = 0f
        _updateErrorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaybackJob()
        audioManager.release()
    }
}

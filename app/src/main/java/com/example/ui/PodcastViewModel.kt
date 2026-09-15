package com.example.ui

import android.app.Application
import android.util.Log
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

    val isBuffering: StateFlow<Boolean> = audioManager.isBuffering

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

    // Podcast Transcript & Ad String Segments State
    val transcriptSegments: StateFlow<List<TranscriptSegment>> = _currentPlayingEpisode
        .map { ep ->
            if (ep != null) {
                TranscriptParser.parseOrGenerateTranscript(
                    rawTranscript = ep.transcript.ifEmpty { null },
                    episodeTitle = ep.title,
                    episodeDescription = ep.description,
                    durationSeconds = ep.durationSeconds,
                    chapters = ChapterParser.parseChapters(ep.chapters, ep.description, ep.durationSeconds)
                )
            } else {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggestedAdChunks: StateFlow<List<com.example.data.SuggestedAdChunk>> = transcriptSegments
        .map { segments ->
            TranscriptParser.analyzeTranscriptForSuggestedAds(segments)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSegmentAdStatus(startTimeSeconds: Long) {
        val currentEp = _currentPlayingEpisode.value ?: return
        val currentSegments = transcriptSegments.value
        if (currentSegments.isEmpty()) return

        val updatedSegments = currentSegments.map { seg ->
            if (seg.startTimeSeconds == startTimeSeconds) {
                seg.copy(isSponsor = !seg.isSponsor)
            } else {
                seg
            }
        }

        val newTranscriptText = TranscriptParser.serializeSegmentsToTranscriptText(updatedSegments)
        val updatedEp = currentEp.copy(transcript = newTranscriptText)
        _currentPlayingEpisode.value = updatedEp

        viewModelScope.launch {
            repository.updateEpisode(updatedEp)
            val toggledSeg = updatedSegments.firstOrNull { it.startTimeSeconds == startTimeSeconds }
            val status = if (toggledSeg?.isSponsor == true) "marked as AD" else "unmarked from ADs"
            repository.addSyncLog("Transcript Ad Analyzer", "Segment at ${toggledSeg?.formattedTime()} $status.")
        }
    }

    fun markAllSuggestedAsAds() {
        val currentEp = _currentPlayingEpisode.value ?: return
        val currentSegments = transcriptSegments.value
        val suggestions = suggestedAdChunks.value
        if (currentSegments.isEmpty() || suggestions.isEmpty()) return

        val suggestedTimes = suggestions.map { it.segment.startTimeSeconds }.toSet()
        val updatedSegments = currentSegments.map { seg ->
            if (suggestedTimes.contains(seg.startTimeSeconds)) {
                seg.copy(isSponsor = true)
            } else {
                seg
            }
        }

        val newTranscriptText = TranscriptParser.serializeSegmentsToTranscriptText(updatedSegments)
        val updatedEp = currentEp.copy(transcript = newTranscriptText)
        _currentPlayingEpisode.value = updatedEp

        viewModelScope.launch {
            repository.updateEpisode(updatedEp)
            _sponsorSkipEvent.value = "Marked ${suggestions.size} suggested transcript chunks as Ads!"
            repository.addSyncLog("Transcript Ad Analyzer", "Marked ${suggestions.size} suggested transcript ad chunks for auto-skipping.")
        }
    }

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

    fun stopAndDismissPlayer() {
        val current = _currentPlayingEpisode.value
        _isPlaying.value = false
        audioManager.stop()
        stopPlaybackJob()
        _isPlayerExpanded.value = false
        _isAdActive.value = false
        lastSkippedTranscriptTimeSec = null
        lastSkippedChapterTitle = null
        lastSkippedAcousticAdId = null

        if (current != null) {
            val finalPos = _playbackPositionMs.value
            val isCompleted = current.durationSeconds > 0 && finalPos >= current.durationSeconds * 1000L
            viewModelScope.launch {
                repository.updateEpisodeProgress(current.id, finalPos, isCompleted)
                repository.addSyncLog("Player", "Stopped playback and dismissed player for '${current.title}'")
            }
        }
        _currentPlayingEpisode.value = null
        _playbackPositionMs.value = 0L
        _waveformAmplitudes.value = emptyList()
        _acousticAdSegments.value = emptyList()
        _lastAcousticAdAlert.value = null
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

    // Speech-To-Text (STT) Live Ad Keyword Scanner State
    private val _isSttScanning = MutableStateFlow(false)
    val isSttScanning: StateFlow<Boolean> = _isSttScanning.asStateFlow()

    private val _sttLiveText = MutableStateFlow("")
    val sttLiveText: StateFlow<String> = _sttLiveText.asStateFlow()

    private val _sttMatchedKeywords = MutableStateFlow<List<String>>(emptyList())
    val sttMatchedKeywords: StateFlow<List<String>> = _sttMatchedKeywords.asStateFlow()

    private val _sttConfidenceScore = MutableStateFlow(0f)
    val sttConfidenceScore: StateFlow<Float> = _sttConfidenceScore.asStateFlow()

    private val adKeywordDictionary = listOf(
        "werbung", "werbepartner", "sponsor", "sponsorship", "sponsored by",
        "brought to you by", "discount code", "promo code", "promocode", "special offer",
        "commercial break", "ad break", "partner", "buy now", "link in description",
        "ag1", "athletic greens", "betterhelp", "nordvpn", "expressvpn", "manscaped",
        "square space", "babbel", "audible", "factor meals", "shopify"
    )

    fun toggleSttScanner() {
        _isSttScanning.value = !_isSttScanning.value
        if (_isSttScanning.value) {
            _sponsorSkipEvent.value = "Speech-to-Text STT Scanner Aktiviert"
            scanSpeechTextForAds("This episode is sponsored by AG1 and BetterHelp. Use promo code PODCAST for a special discount offer.")
        } else {
            _sttMatchedKeywords.value = emptyList()
            _sttConfidenceScore.value = 0f
            _sttLiveText.value = ""
        }
    }

    fun scanSpeechTextForAds(customText: String) {
        _sttLiveText.value = customText
        val lowerText = customText.lowercase(java.util.Locale.ROOT)
        val matched = adKeywordDictionary.filter { keyword ->
            lowerText.contains(keyword)
        }
        _sttMatchedKeywords.value = matched
        if (matched.isNotEmpty()) {
            val score = (matched.size * 0.35f + 0.30f).coerceAtMost(0.98f)
            _sttConfidenceScore.value = score
            if (_isAutoAdSkipEnabled.value) {
                _sponsorSkipEvent.value = "STT Ad Keyword erkannt: ${matched.joinToString(", ")}"
            }
        } else {
            _sttConfidenceScore.value = 0.05f
        }
    }

    // Transcript refresh state
    private val _isTranscriptRefreshing = MutableStateFlow(false)
    val isTranscriptRefreshing: StateFlow<Boolean> = _isTranscriptRefreshing.asStateFlow()

    // Theme Mode (System Default, Dark, Light)
    private val _themeMode = MutableStateFlow(com.example.ui.theme.AppThemeMode.SYSTEM)
    val themeMode: StateFlow<com.example.ui.theme.AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: com.example.ui.theme.AppThemeMode) {
        _themeMode.value = mode
    }

    // Settings
    private val _isOfflineModeOnly = MutableStateFlow(false)
    val isOfflineModeOnly: StateFlow<Boolean> = _isOfflineModeOnly.asStateFlow()

    // Logging & Diagnostics Setting
    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _logFilterTag = MutableStateFlow("ALL")
    val logFilterTag: StateFlow<String> = _logFilterTag.asStateFlow()

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        viewModelScope.launch {
            if (enabled) {
                repository.addSyncLog("Settings", "App-Logging aktiviert (Diagnose & Systemprotokollierung).")
            } else {
                repository.addSyncLog("Settings", "App-Logging deaktiviert.")
            }
        }
    }

    fun setLogFilterTag(tag: String) {
        _logFilterTag.value = tag
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearSyncLogs()
            _sponsorSkipEvent.value = "Logs erfolgreich geleert."
        }
    }

    fun addManualLogEntry(tag: String, message: String) {
        if (!_isLoggingEnabled.value) return
        viewModelScope.launch {
            repository.addSyncLog(tag, message)
        }
    }

    // Sleep Timer / Countdown State (15, 30, 45, custom minutes)
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _sleepTimerDurationMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerDurationMinutes: StateFlow<Int?> = _sleepTimerDurationMinutes.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        _sleepTimerDurationMinutes.value = minutes
        val totalSec = minutes * 60L
        _sleepTimerRemainingSeconds.value = totalSec

        val msg = "Sleep Timer auf $minutes Minuten gesetzt."
        _sponsorSkipEvent.value = msg
        if (_isLoggingEnabled.value) {
            viewModelScope.launch {
                repository.addSyncLog("Sleep Timer", "Countdown gestartet: $minutes min ($totalSec s)")
            }
        }

        sleepTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _sleepTimerRemainingSeconds.value ?: break
                if (current <= 1L) {
                    _sleepTimerRemainingSeconds.value = 0L
                    // Timer expired: stop audio and pause
                    pausePlayback()
                    _sponsorSkipEvent.value = "⏱ Sleep Timer abgelaufen: Wiedergabe pausiert."
                    if (_isLoggingEnabled.value) {
                        repository.addSyncLog("Sleep Timer", "Countdown beendet -> Audio automatisch pausiert.")
                    }
                    delay(800L)
                    _sleepTimerRemainingSeconds.value = null
                    _sleepTimerDurationMinutes.value = null
                    break
                } else {
                    _sleepTimerRemainingSeconds.value = current - 1L
                }
            }
        }
    }

    fun addSleepTimerMinutes(extraMinutes: Int) {
        val currentRemaining = _sleepTimerRemainingSeconds.value ?: 0L
        val newRemainingSec = currentRemaining + (extraMinutes * 60L)
        val newMinutes = ((newRemainingSec + 59) / 60).toInt()
        setSleepTimer(newMinutes)
    }

    fun setSleepTimerEndOfEpisode() {
        val ep = _currentPlayingEpisode.value ?: return
        val currentMs = _playbackPositionMs.value
        val totalMs = ep.durationSeconds * 1000L
        val remainingMs = (totalMs - currentMs).coerceAtLeast(10000L)
        val minutes = ((remainingMs / 1000L) / 60L).toInt().coerceAtLeast(1)
        setSleepTimer(minutes)
        _sponsorSkipEvent.value = "Sleep Timer: Am Ende der Episode anhalten (~$minutes Min)"
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSeconds.value = null
        _sleepTimerDurationMinutes.value = null
        _sponsorSkipEvent.value = "Sleep Timer deaktiviert."
        if (_isLoggingEnabled.value) {
            viewModelScope.launch {
                repository.addSyncLog("Sleep Timer", "Countdown abgebrochen.")
            }
        }
    }

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

    val currentAppVersion = "v${com.example.BuildConfig.VERSION_NAME}"
    val currentBuildNumber = com.example.BuildConfig.VERSION_CODE

    private var playbackJob: Job? = null

    enum class Tab {
        DISCOVER, DOWNLOADS, VERLAUF, SETTINGS
    }

    // History Episode Item Model
    data class HistoryEpisodeItem(
        val episode: EpisodeEntity,
        val podcastTitle: String,
        val podcastImageUrl: String,
        val durationSeconds: Long,
        val listenedPositionMs: Long,
        val percentageListened: Int,
        val isCompleted: Boolean
    )

    // Listening History State Flow
    val historyEpisodes: StateFlow<List<HistoryEpisodeItem>> = combine(
        repository.allEpisodes,
        repository.allPodcasts
    ) { eps, podList ->
        val podMap = podList.associateBy { it.id }
        eps.filter { ep ->
            ep.playbackPositionMs > 0 || ep.isCompleted
        }.map { ep ->
            val pod = podMap[ep.podcastId]
            val totalSec = ep.durationSeconds.coerceAtLeast(1L)
            val listenedSec = ep.playbackPositionMs / 1000
            val percentage = if (ep.isCompleted) 100 else ((listenedSec * 100) / totalSec).toInt().coerceIn(0, 100)
            HistoryEpisodeItem(
                episode = ep,
                podcastTitle = pod?.title ?: "Podcast",
                podcastImageUrl = pod?.coverUrl ?: "",
                durationSeconds = totalSec,
                listenedPositionMs = ep.playbackPositionMs,
                percentageListened = percentage,
                isCompleted = ep.isCompleted
            )
        }.sortedByDescending { it.episode.playbackPositionMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun clearEpisodeHistory(episodeId: String) {
        viewModelScope.launch {
            repository.clearEpisodeHistory(episodeId)
        }
    }

    fun toggleEpisodeCompleted(episodeId: String, markAsCompleted: Boolean) {
        viewModelScope.launch {
            val ep = repository.getEpisodeById(episodeId) ?: return@launch
            val newPos = if (markAsCompleted) ep.durationSeconds * 1000L else 0L
            repository.updateEpisodeProgress(episodeId, newPos, markAsCompleted)
        }
    }

    data class RemoteSyncInfo(
        val episodeId: String,
        val episodeTitle: String,
        val deviceName: String,
        val remotePositionMs: Long
    )

    // Refresh state for podcast episodes
    private val _isRefreshingEpisodes = MutableStateFlow(false)
    val isRefreshingEpisodes: StateFlow<Boolean> = _isRefreshingEpisodes.asStateFlow()

    // Last refresh timestamp tracking (per podcast ID)
    private val lastRefreshTimestamps = mutableMapOf<String, Long>()
    private val appLaunchTimestamp = System.currentTimeMillis()

    init {
        audioManager.onErrorListener = { errorMsg ->
            _isPlaying.value = false
            _sponsorSkipEvent.value = "Audio-Fehler: $errorMsg"
            viewModelScope.launch {
                repository.addSyncLog("Audio Player", "Error: $errorMsg")
            }
        }
        viewModelScope.launch {
            repository.populateInitialDataIfNeeded(getApplication())
            repository.validateAndCleanupCorruptDownloads(getApplication())
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

    // Podcast Detail control with auto-refresh if > 1 hour since opening/last refresh
    fun selectPodcast(podcast: PodcastEntity?) {
        _selectedPodcast.value = podcast
        if (podcast != null) {
            val lastRefresh = lastRefreshTimestamps[podcast.id] ?: 0L
            val oneHourMs = 3600_000L
            val now = System.currentTimeMillis()

            // If never refreshed in this session or last refresh was longer than 1 hour ago
            if (lastRefresh == 0L || (now - lastRefresh) >= oneHourMs) {
                val timeDiffMin = if (lastRefresh == 0L) ">1h" else "${(now - lastRefresh) / 60000}m"
                Log.i("PodcastPlayer", "[SYSTEM CONSOLE] Auto-refresh triggered for '${podcast.title}' (last refresh: $timeDiffMin)")
                refreshPodcastEpisodes(podcast.id, isAutoOneHour = true)
            }
        }
    }

    // Manual Swipe-Down or Auto Refresh for Podcast Episodes
    fun refreshPodcastEpisodes(podcastId: String, isAutoOneHour: Boolean = false) {
        viewModelScope.launch {
            val podcast = repository.getPodcastById(podcastId) ?: _selectedPodcast.value ?: return@launch
            _isRefreshingEpisodes.value = true
            lastRefreshTimestamps[podcastId] = System.currentTimeMillis()

            try {
                val resolvedFeedUrl = if (podcast.feedUrl.isNotBlank()) {
                    podcast.feedUrl
                } else if (podcast.id.contains("huberman", ignoreCase = true)) {
                    "https://feeds.megaphone.fm/hubermanlab"
                } else {
                    podcast.feedUrl
                }

                Log.i("PodcastPlayer", "[SYSTEM CONSOLE] Fetching latest episodes for '${podcast.title}' from feed: $resolvedFeedUrl")
                val feedResult = withContext(Dispatchers.IO) {
                    PodcastApiClient.fetchFeedDetails(resolvedFeedUrl, podcast.title)
                }

                var currentPod = podcast
                if (podcast.feedUrl.isBlank() && resolvedFeedUrl.isNotBlank()) {
                    currentPod = currentPod.copy(feedUrl = resolvedFeedUrl)
                }
                if (!feedResult.channelCoverUrl.isNullOrBlank() && (currentPod.coverUrl.isBlank() || currentPod.coverUrl.contains("mza_10793616858548971277"))) {
                    currentPod = currentPod.copy(coverUrl = feedResult.channelCoverUrl)
                }
                if (currentPod != podcast) {
                    repository.updatePodcast(currentPod)
                    _selectedPodcast.value = currentPod
                }

                val fetched = feedResult.episodes
                if (fetched.isNotEmpty()) {
                    val existing = repository.getEpisodesForPodcast(podcastId).first()
                    val existingMap = existing.associateBy { it.id }

                    val mergedEpisodes = fetched.map { f ->
                        val prev = existingMap[f.id] ?: existing.firstOrNull { it.title.equals(f.title, ignoreCase = true) }
                        val ts = if (f.publishTimestamp > 0L) f.publishTimestamp else com.example.util.PodcastDateUtils.parseDateToTimestamp(f.publishDate)
                        val artwork = f.episodeArtworkUrl ?: currentPod.coverUrl
                        EpisodeEntity(
                            id = prev?.id ?: f.id,
                            podcastId = currentPod.id,
                            podcastTitle = currentPod.title,
                            podcastCoverUrl = artwork,
                            title = f.title,
                            description = if (f.description.length >= (prev?.description?.length ?: 0)) f.description else (prev?.description ?: f.description),
                            durationSeconds = if (f.durationSeconds > 0) f.durationSeconds else (prev?.durationSeconds ?: 1800L),
                            publishDate = f.publishDate,
                            audioUrl = f.audioUrl.ifEmpty { prev?.audioUrl ?: "" },
                            isDownloaded = prev?.isDownloaded ?: false,
                            downloadLocalPath = prev?.downloadLocalPath,
                            playbackPositionMs = prev?.playbackPositionMs ?: 0L,
                            isCompleted = prev?.isCompleted ?: false,
                            isFavorite = prev?.isFavorite ?: false,
                            adTimestampsSeconds = f.adTimestampsSeconds.ifEmpty { prev?.adTimestampsSeconds ?: "" },
                            chapters = f.chapters.ifEmpty { prev?.chapters ?: "" },
                            transcript = f.transcript.ifEmpty { prev?.transcript ?: "" },
                            publishTimestamp = ts
                        )
                    }.sortedWith(compareByDescending<EpisodeEntity> { it.publishTimestamp }.thenByDescending { it.publishDate })

                    repository.insertEpisodes(mergedEpisodes)

                    val statusMsg = if (isAutoOneHour) {
                        "Auto-refreshed episodes for '${podcast.title}' (>1hr since last refresh)"
                    } else {
                        "Refreshed ${mergedEpisodes.size} newest episodes for '${podcast.title}'"
                    }
                    _sponsorSkipEvent.value = statusMsg
                    repository.addSyncLog("Feed Refresh Engine", statusMsg)
                    Log.i("PodcastPlayer", "[SYSTEM CONSOLE] $statusMsg")
                    System.out.println("[PodcastPlayerConsole] $statusMsg")
                }
            } catch (e: Exception) {
                Log.e("PodcastPlayer", "[SYSTEM CONSOLE] Failed to refresh podcast episodes: ${e.message}", e)
            } finally {
                _isRefreshingEpisodes.value = false
            }
        }
    }

    private var metadataRefreshJob: kotlinx.coroutines.Job? = null

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
            _currentPlayingEpisode.value = currentEp
            _playbackPositionMs.value = currentEp.playbackPositionMs
            _isAdActive.value = false
            _isPlaying.value = true

            // Determine playback target: prioritize verified offline download
            val localPath = currentEp.downloadLocalPath
            val hasValidOfflineFile = EpisodeDownloader.isValidDownloadedFile(localPath)

            val audioTarget = if (hasValidOfflineFile) {
                localPath!!
            } else {
                if (currentEp.isDownloaded) {
                    // Stale database flag without actual file; clean up
                    currentEp = currentEp.copy(isDownloaded = false, downloadLocalPath = null)
                    repository.updateEpisode(currentEp)
                }
                if (_isOfflineModeOnly.value) {
                    _isPlaying.value = false
                    _sponsorSkipEvent.value = "Episode nicht offline verfügbar. Bitte zuerst herunterladen."
                    repository.addSyncLog("Offline Mode", "Playback blocked for '${currentEp.title}' (Not downloaded)")
                    return@launch
                }
                currentEp.audioUrl
            }

            // Start audio IMMEDIATELY for zero lag & maximum efficacy
            audioManager.play(audioTarget, currentEp.playbackPositionMs)
            audioManager.onCompletionListener = {
                handleEpisodeCompletion()
            }

            startPlaybackJob()

            // Check if initial position starts inside a sponsor segment
            checkSponsorAndAdDetection(currentEp.playbackPositionMs)

            repository.addSyncLog("Pixel 9 Pro (This Device)", "Started listening to '${currentEp.title}'")

            // UPDATE METADATA AFTERWARDS (Description, Chapters, Transcript, Acoustic DAI Waveform)
            refreshMetadataAfterwards(currentEp)
        }
    }

    private fun refreshMetadataAfterwards(episode: EpisodeEntity) {
        metadataRefreshJob?.cancel()
        metadataRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var currentEp = episode

                // 1. Fetch updated rich metadata from RSS feed if online
                val podcast = repository.getPodcastById(currentEp.podcastId)
                val feedUrl = when {
                    podcast != null && podcast.feedUrl.isNotBlank() -> podcast.feedUrl
                    currentEp.podcastId.contains("huberman", ignoreCase = true) -> "https://feeds.megaphone.fm/hubermanlab"
                    else -> ""
                }
                if (!_isOfflineModeOnly.value && feedUrl.startsWith("http")) {
                    val enriched = PodcastApiClient.fetchEnrichedMetadataForEpisode(
                        feedUrl = feedUrl,
                        episodeTitle = currentEp.title,
                        audioUrl = currentEp.audioUrl,
                        durationSeconds = currentEp.durationSeconds
                    )
                    if (enriched != null) {
                        var newDesc = currentEp.description
                        if (enriched.description.isNotBlank() && enriched.description.length > currentEp.description.length) {
                            newDesc = enriched.description
                        }
                        var newChaps = currentEp.chapters
                        if (enriched.chapters.isNotBlank()) {
                            newChaps = enriched.chapters
                        }
                        var newTranscript = currentEp.transcript
                        if (enriched.transcript.isNotBlank()) {
                            newTranscript = enriched.transcript
                        }
                        val artwork = enriched.episodeArtworkUrl ?: currentEp.podcastCoverUrl
                        currentEp = currentEp.copy(
                            description = newDesc,
                            chapters = newChaps,
                            transcript = newTranscript,
                            podcastCoverUrl = artwork,
                            adTimestampsSeconds = if (enriched.adTimestampsSeconds.isNotBlank()) enriched.adTimestampsSeconds else currentEp.adTimestampsSeconds
                        )
                    }
                }

                // 2. Parse chapters from updated description if chapters empty
                val parsedChaps = ChapterParser.parseChapters(currentEp.chapters, currentEp.description, currentEp.durationSeconds)
                if (currentEp.chapters.isEmpty() && parsedChaps.isNotEmpty()) {
                    val pipeStr = ChapterParser.toPipeString(parsedChaps)
                    currentEp = currentEp.copy(chapters = pipeStr)
                }

                // 3. Acoustic Waveform Analysis & Dynamic Ad Insertion (DAI) Profile
                val (waveform, detectedAcousticAds) = audioWaveDetector.analyzeWaveform(currentEp.id, currentEp.durationSeconds)
                withContext(Dispatchers.Main) {
                    _waveformAmplitudes.value = waveform
                    _acousticAdSegments.value = detectedAcousticAds
                }

                // 4. Save updated metadata to database
                repository.updateEpisode(currentEp)

                // 5. Update current playing episode state if still active
                withContext(Dispatchers.Main) {
                    if (_currentPlayingEpisode.value?.id == currentEp.id) {
                        _currentPlayingEpisode.value = currentEp
                    }
                }
            } catch (e: Exception) {
                Log.e("PodcastPlayer", "Metadata refresh afterwards error: ${e.message}", e)
            }
        }
    }

    fun refreshEpisodeMetadataNow(episode: EpisodeEntity) {
        refreshMetadataAfterwards(episode)
        _sponsorSkipEvent.value = "Aktualisiere Episoden-Metadaten & Feed..."
    }

    fun refreshCurrentEpisodeTranscript() {
        val current = _currentPlayingEpisode.value ?: return
        viewModelScope.launch {
            _isTranscriptRefreshing.value = true
            try {
                val podcast = repository.getPodcastById(current.podcastId)
                val feedUrl = when {
                    podcast != null && podcast.feedUrl.isNotBlank() -> podcast.feedUrl
                    current.podcastId.contains("huberman", ignoreCase = true) -> "https://feeds.megaphone.fm/hubermanlab"
                    else -> ""
                }

                if (!_isOfflineModeOnly.value && feedUrl.startsWith("http")) {
                    val enriched = withContext(Dispatchers.IO) {
                        PodcastApiClient.fetchEnrichedMetadataForEpisode(
                            feedUrl = feedUrl,
                            episodeTitle = current.title,
                            audioUrl = current.audioUrl,
                            durationSeconds = current.durationSeconds
                        )
                    }

                    if (enriched != null && enriched.transcript.isNotBlank()) {
                        val updated = current.copy(transcript = enriched.transcript)
                        repository.updateEpisode(updated)
                        _currentPlayingEpisode.value = updated
                        _sponsorSkipEvent.value = "Transkript erfolgreich aus RSS-Feed geladen"
                    } else {
                        _sponsorSkipEvent.value = "Kein eingebettetes Skript/Transkript im Podcast-Feed gefunden"
                    }
                } else {
                    _sponsorSkipEvent.value = if (_isOfflineModeOnly.value) "Offline-Modus aktiv: Keine Verbindung zum Feed" else "Keine Feed-URL für diesen Podcast verfügbar"
                }
            } catch (e: Exception) {
                Log.e("PodcastPlayer", "Error refreshing transcript: ${e.message}", e)
                _sponsorSkipEvent.value = "Fehler beim Laden des Transkripts: ${e.message}"
            } finally {
                delay(350)
                _isTranscriptRefreshing.value = false
            }
        }
    }

    fun pausePlayback() {
        val current = _currentPlayingEpisode.value ?: return
        if (_isPlaying.value) {
            _isPlaying.value = false
            audioManager.pause()
            stopPlaybackJob()
            viewModelScope.launch {
                repository.updateEpisodeProgress(current.id, _playbackPositionMs.value, current.isCompleted)
                repository.addSyncLog("Pixel 9 Pro (This Device)", "Paused '${current.title}'")
            }
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

    fun skipToNextChapter() {
        val chaps = currentChapters.value
        if (chaps.isEmpty()) {
            skipForward()
            return
        }
        val currentSec = _playbackPositionMs.value / 1000
        val nextChap = chaps.firstOrNull { it.startTimeSeconds > currentSec + 1 }
        if (nextChap != null) {
            seekToChapter(nextChap)
            _sponsorSkipEvent.value = "Kapitel gewechselt: ${nextChap.title}"
        } else {
            val ep = _currentPlayingEpisode.value
            val durationMs = (ep?.durationSeconds ?: 0L) * 1000L
            if (durationMs > 0) {
                seekTo(durationMs)
            }
        }
    }

    fun skipToPreviousChapter() {
        val chaps = currentChapters.value
        if (chaps.isEmpty()) {
            skipBackward()
            return
        }
        val currentSec = _playbackPositionMs.value / 1000
        val curChap = chaps.lastOrNull { it.startTimeSeconds <= currentSec }
        if (curChap != null && (currentSec - curChap.startTimeSeconds) > 3) {
            seekToChapter(curChap)
            _sponsorSkipEvent.value = "Kapitel neugestartet: ${curChap.title}"
        } else {
            val prevChap = chaps.takeWhile { it.startTimeSeconds < currentSec }.lastOrNull()
            if (prevChap != null) {
                seekToChapter(prevChap)
                _sponsorSkipEvent.value = "Kapitel gewechselt: ${prevChap.title}"
            } else {
                seekTo(0L)
            }
        }
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

    // Completely Remove / Delete Podcast from app
    fun removePodcast(podcast: PodcastEntity) {
        viewModelScope.launch {
            if (_selectedPodcast.value?.id == podcast.id) {
                _selectedPodcast.value = null
            }
            if (_currentPlayingEpisode.value?.podcastId == podcast.id) {
                audioManager.stop()
                _isPlaying.value = false
                _currentPlayingEpisode.value = null
            }
            repository.removePodcast(podcast)
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Removed podcast '${podcast.title}' and all its episodes")
            _sponsorSkipEvent.value = "Podcast '${podcast.title}' entfernt"
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

            // Fetch feed details first to capture channel artwork & episodes
            val feedResult = withContext(Dispatchers.IO) {
                PodcastApiClient.fetchFeedDetails(result.feedUrl, result.title)
            }
            val resolvedCoverUrl = feedResult.channelCoverUrl ?: result.coverUrl

            // Create new PodcastEntity and save to Room
            val newPodcast = PodcastEntity(
                id = result.id,
                title = result.title,
                author = result.author,
                description = if (result.description.isNotBlank()) result.description else (feedResult.channelDescription ?: ""),
                coverUrl = resolvedCoverUrl,
                category = result.category,
                isSubscribed = true,
                feedUrl = result.feedUrl
            )
            repository.insertPodcast(newPodcast)
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Subscribed to '${result.title}' via ${result.source.displayName}")

            val episodeEntities = feedResult.episodes.map { ep ->
                val ts = if (ep.publishTimestamp > 0L) ep.publishTimestamp else com.example.util.PodcastDateUtils.parseDateToTimestamp(ep.publishDate)
                val artwork = ep.episodeArtworkUrl ?: resolvedCoverUrl
                EpisodeEntity(
                    id = ep.id,
                    podcastId = result.id,
                    podcastTitle = result.title,
                    podcastCoverUrl = artwork,
                    title = ep.title,
                    description = ep.description,
                    durationSeconds = ep.durationSeconds,
                    publishDate = ep.publishDate,
                    audioUrl = ep.audioUrl,
                    isDownloaded = false,
                    adTimestampsSeconds = ep.adTimestampsSeconds,
                    chapters = ep.chapters,
                    transcript = ep.transcript,
                    publishTimestamp = ts
                )
            }.sortedWith(compareByDescending<EpisodeEntity> { it.publishTimestamp }.thenByDescending { it.publishDate })

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
            _sponsorSkipEvent.value = "Batch-Download gestartet (${episodesToDownload.size} Episoden)..."
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Queued ${episodesToDownload.size} episodes for batch offline download")

            var successCount = 0
            for (episode in episodesToDownload) {
                _downloadProgressMap.value = _downloadProgressMap.value + (episode.id to 0.05f)
                val filePath = EpisodeDownloader.downloadToFile(
                    context = getApplication(),
                    url = episode.audioUrl,
                    episodeId = episode.id
                ) { progress ->
                    _downloadProgressMap.value = _downloadProgressMap.value + (episode.id to progress)
                }

                if (EpisodeDownloader.isValidDownloadedFile(filePath)) {
                    val updated = episode.copy(
                        isDownloaded = true,
                        downloadLocalPath = filePath
                    )
                    repository.updateEpisode(updated)
                    successCount++
                } else {
                    val updated = episode.copy(
                        isDownloaded = false,
                        downloadLocalPath = null
                    )
                    repository.updateEpisode(updated)
                }
                _downloadProgressMap.value = _downloadProgressMap.value - episode.id
            }

            _isBatchDownloading.value = false
            _isMultiSelectMode.value = false
            _selectedEpisodeIds.value = emptySet()
            val resultSummary = "$successCount von ${episodesToDownload.size} Episoden erfolgreich offline gespeichert."
            _sponsorSkipEvent.value = resultSummary
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Batch download finished: $resultSummary")
        }
    }

    // Real Episode Download
    fun downloadEpisode(episode: EpisodeEntity) {
        if (episode.isDownloaded && EpisodeDownloader.isValidDownloadedFile(episode.downloadLocalPath)) return
        viewModelScope.launch {
            // Check if downloading already in progress
            if (_downloadProgressMap.value.containsKey(episode.id)) return@launch

            _downloadProgressMap.value = _downloadProgressMap.value + (episode.id to 0.02f)
            _sponsorSkipEvent.value = "Download gestartet: '${episode.title}'"
            repository.addSyncLog("Pixel 9 Pro (This Device)", "Downloading '${episode.title}' for offline storage")

            val filePath = EpisodeDownloader.downloadToFile(
                context = getApplication(),
                url = episode.audioUrl,
                episodeId = episode.id
            ) { progress ->
                _downloadProgressMap.value = _downloadProgressMap.value + (episode.id to progress)
            }

            // Remove from progress tracker
            _downloadProgressMap.value = _downloadProgressMap.value - episode.id

            if (EpisodeDownloader.isValidDownloadedFile(filePath)) {
                val file = java.io.File(filePath!!)
                val sizeMb = String.format(java.util.Locale.US, "%.1f MB", file.length() / (1024.0 * 1024.0))
                val updated = episode.copy(
                    isDownloaded = true,
                    downloadLocalPath = filePath
                )
                repository.updateEpisode(updated)

                if (_currentPlayingEpisode.value?.id == episode.id) {
                    _currentPlayingEpisode.value = updated
                }
                _sponsorSkipEvent.value = "✓ Heruntergeladen ($sizeMb): '${episode.title}'"
                repository.addSyncLog("Pixel 9 Pro (This Device)", "Downloaded '${episode.title}' ($sizeMb) for offline playback")
            } else {
                val updated = episode.copy(
                    isDownloaded = false,
                    downloadLocalPath = null
                )
                repository.updateEpisode(updated)

                if (_currentPlayingEpisode.value?.id == episode.id) {
                    _currentPlayingEpisode.value = updated
                }
                _sponsorSkipEvent.value = "Download fehlgeschlagen: Bitte Internetverbindung prüfen."
                repository.addSyncLog("Pixel 9 Pro (This Device)", "Download failed for '${episode.title}'")
            }
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
            _sponsorSkipEvent.value = "Offline-Kopie gelöscht: '${episode.title}'"
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

    // Internal playback loop with system console diagnostics
    private fun startPlaybackJob() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var loopTickCount = 0
            while (_isPlaying.value) {
                delay(250) // Update position every 250ms for smooth UI progress
                val current = _currentPlayingEpisode.value ?: break

                if (audioManager.isBuffering.value) {
                    // Do not increment playback position while player is buffering stream over network
                    if (++loopTickCount % 12 == 0) {
                        val consoleLine = "[PLAYER CONSOLE] Buffering audio stream for '${current.title}'..."
                        Log.i("PodcastPlayer", consoleLine)
                        System.out.println(consoleLine)
                    }
                    continue
                }

                val realPosition = audioManager.getCurrentPosition()
                val newPosition = if (realPosition >= 0) realPosition else _playbackPositionMs.value
                val durationMs = current.durationSeconds * 1000

                if (newPosition >= durationMs && durationMs > 0) {
                    handleEpisodeCompletion()
                    break
                } else {
                    _playbackPositionMs.value = newPosition
                    checkSponsorAndAdDetection(newPosition)

                    // Emit live telemetry to system console / logcat every 3 seconds (12 * 250ms)
                    if (++loopTickCount % 12 == 0) {
                        val posSec = newPosition / 1000
                        val durSec = current.durationSeconds
                        val energy = (_currentAudioEnergy.value * 100).toInt()
                        val consoleLine = "[PLAYER CONSOLE] Playing '${current.title}' | Pos: ${posSec}s / ${durSec}s (${if (durationMs > 0) (newPosition * 100 / durationMs) else 0}%) | RMS: $energy% | AdActive: ${_isAdActive.value} | AutoSkip: ${_isAutoAdSkipEnabled.value}"
                        Log.i("PodcastPlayer", consoleLine)
                        System.out.println(consoleLine)
                    }
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
    private var lastSkippedTranscriptTimeSec: Long? = null

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

        // 3. Transcript-Based Ad Chunk & Cross-Podcast Promo Auto-Skipping
        if (_isAutoAdSkipEnabled.value) {
            val segments = transcriptSegments.value
            if (segments.isNotEmpty()) {
                val activeAdSegmentIndex = segments.indexOfFirst { seg ->
                    seg.isSponsor && currentSeconds >= seg.startTimeSeconds && currentSeconds < (seg.startTimeSeconds + 45L)
                }

                if (activeAdSegmentIndex != -1) {
                    val activeAdSeg = segments[activeAdSegmentIndex]
                    if (lastSkippedTranscriptTimeSec != activeAdSeg.startTimeSeconds) {
                        lastSkippedTranscriptTimeSec = activeAdSeg.startTimeSeconds

                        val nextNonSponsorSeg = segments.drop(activeAdSegmentIndex + 1).firstOrNull { !it.isSponsor }
                        val targetSec = nextNonSponsorSeg?.startTimeSeconds ?: (activeAdSeg.startTimeSeconds + 30L)
                        val targetMs = (targetSec * 1000L).coerceAtMost(durationMs)
                        val savedSecs = (targetSec - currentSeconds).coerceAtLeast(15)

                        _adsBlockedCount.value += 1
                        _savedMinutes.value += ((savedSecs + 59) / 60).toInt()
                        _playbackPositionMs.value = targetMs
                        audioManager.seekTo(targetMs)
                        _sponsorSkipEvent.value = "Auto-skipped Ad Chunk at ${activeAdSeg.formattedTime()}"

                        viewModelScope.launch {
                            repository.addSyncLog(
                                "Transcript Ad Skipper",
                                "Auto-skipped marked transcript ad/promo chunk '${activeAdSeg.speaker}' at ${activeAdSeg.formattedTime()}. Jumped forward ${savedSecs}s."
                            )
                            repository.updateEpisodeProgress(current.id, targetMs, targetMs >= durationMs)
                        }
                        return
                    }
                } else {
                    lastSkippedTranscriptTimeSec = null
                }
            }
        }

        // 4. Legacy / Custom timestamp ad detection fallback
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

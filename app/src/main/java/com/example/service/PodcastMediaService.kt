package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import coil.Coil
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.R
import com.example.data.EpisodeEntity
import com.example.player.PodcastAudioManager
import com.example.player.PodcastPlayerHub
import kotlinx.coroutines.*

/**
 * Robust Android Foreground Service integrating with the Android System Control Layer:
 * - Registers an active MediaSession so playback appears in Android Quick Settings,
 *   Lock Screen, Bluetooth headsets, WearOS, and Android Auto.
 * - Prevents the OS from freezing, throttling, or killing audio playback after a specific time
 *   via FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, PARTIAL_WAKE_LOCK, and high-performance WifiLock.
 * - Displays a live MediaStyle notification with scrubbing, skip +/-15s, play/pause, and stop.
 */
class PodcastMediaService : Service() {

    companion object {
        const val TAG = "PodcastMediaService"
        const val CHANNEL_ID = "labcast_media_playback"
        const val NOTIFICATION_ID = 2026

        const val ACTION_START_PLAYBACK = "com.example.service.action.START_PLAYBACK"
        const val ACTION_PLAY = "com.example.service.action.PLAY"
        const val ACTION_PAUSE = "com.example.service.action.PAUSE"
        const val ACTION_RESUME = "com.example.service.action.RESUME"
        const val ACTION_SEEK_TO = "com.example.service.action.SEEK_TO"
        const val ACTION_FORWARD = "com.example.service.action.FORWARD"
        const val ACTION_REWIND = "com.example.service.action.REWIND"
        const val ACTION_STOP = "com.example.service.action.STOP"

        const val EXTRA_EPISODE_ID = "extra_episode_id"
        const val EXTRA_PODCAST_ID = "extra_podcast_id"
        const val EXTRA_EPISODE_TITLE = "extra_episode_title"
        const val EXTRA_PODCAST_TITLE = "extra_podcast_title"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_AUDIO_TARGET = "extra_audio_target"
        const val EXTRA_START_POSITION_MS = "extra_start_position_ms"
        const val EXTRA_SEEK_POSITION = "extra_seek_position"

        fun startPlayback(
            context: Context,
            episode: EpisodeEntity,
            audioTarget: String,
            startPositionMs: Long
        ) {
            val intent = Intent(context, PodcastMediaService::class.java).apply {
                action = ACTION_START_PLAYBACK
                putExtra(EXTRA_EPISODE_ID, episode.id)
                putExtra(EXTRA_PODCAST_ID, episode.podcastId)
                putExtra(EXTRA_EPISODE_TITLE, episode.title)
                putExtra(EXTRA_PODCAST_TITLE, episode.podcastTitle)
                putExtra(EXTRA_COVER_URL, episode.podcastCoverUrl)
                putExtra(EXTRA_DURATION_SECONDS, episode.durationSeconds)
                putExtra(EXTRA_AUDIO_TARGET, audioTarget)
                putExtra(EXTRA_START_POSITION_MS, startPositionMs)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            }
        }

        fun play(context: Context) {
            val intent = Intent(context, PodcastMediaService::class.java).apply {
                action = ACTION_PLAY
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun pause(context: Context) {
            val intent = Intent(context, PodcastMediaService::class.java).apply {
                action = ACTION_PAUSE
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun resume(context: Context) {
            play(context)
        }

        fun seekTo(context: Context, positionMs: Long) {
            val intent = Intent(context, PodcastMediaService::class.java).apply {
                action = ACTION_SEEK_TO
                putExtra(EXTRA_SEEK_POSITION, positionMs)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun skipForward(context: Context, amountMs: Long = 15_000L) {
            val intent = Intent(context, PodcastMediaService::class.java).apply {
                action = ACTION_FORWARD
                putExtra(EXTRA_SEEK_POSITION, amountMs)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun skipBackward(context: Context, amountMs: Long = 15_000L) {
            val intent = Intent(context, PodcastMediaService::class.java).apply {
                action = ACTION_REWIND
                putExtra(EXTRA_SEEK_POSITION, amountMs)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, PodcastMediaService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }
    }

    private lateinit var audioManagerEngine: PodcastAudioManager
    private var mediaSession: MediaSession? = null
    private var audioSystemManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null

    private var activeEpisode: EpisodeEntity? = null
    private var currentArtworkBitmap: Bitmap? = null
    private var currentAudioTarget: String? = null
    private var isServiceForeground = false
    private var wasPlayingBeforeTransientLoss = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "[SYSTEM CONTROL LAYER] Initializing PodcastMediaService")

        audioManagerEngine = PodcastAudioManager(applicationContext)
        setupWakeLocks()
        setupNotificationChannel()
        setupMediaSession()
        setupEngineCallbacks()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        Log.i(TAG, "[SYSTEM CONTROL LAYER] onStartCommand action=$action")

        when (action) {
            ACTION_START_PLAYBACK -> {
                val epId = intent.getStringExtra(EXTRA_EPISODE_ID) ?: ""
                val podId = intent.getStringExtra(EXTRA_PODCAST_ID) ?: ""
                val epTitle = intent.getStringExtra(EXTRA_EPISODE_TITLE) ?: "Podcast Episode"
                val podTitle = intent.getStringExtra(EXTRA_PODCAST_TITLE) ?: "LabCast"
                val coverUrl = intent.getStringExtra(EXTRA_COVER_URL) ?: ""
                val durationSec = intent.getLongExtra(EXTRA_DURATION_SECONDS, 1800L)
                val audioTarget = intent.getStringExtra(EXTRA_AUDIO_TARGET) ?: ""
                val startPos = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)

                val episode = EpisodeEntity(
                    id = epId,
                    podcastId = podId,
                    podcastTitle = podTitle,
                    podcastCoverUrl = coverUrl,
                    title = epTitle,
                    description = "",
                    durationSeconds = durationSec,
                    publishDate = "",
                    audioUrl = audioTarget,
                    playbackPositionMs = startPos
                )
                startPlaybackInternal(episode, audioTarget, startPos)
            }
            ACTION_PLAY, ACTION_RESUME -> {
                resumePlaybackInternal()
            }
            ACTION_PAUSE -> {
                pausePlaybackInternal()
            }
            ACTION_SEEK_TO -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, -1L)
                if (pos >= 0) {
                    seekToInternal(pos)
                }
            }
            ACTION_FORWARD -> {
                val amount = intent.getLongExtra(EXTRA_SEEK_POSITION, 15_000L)
                val cur = audioManagerEngine.getCurrentPosition()
                seekToInternal(cur + amount)
            }
            ACTION_REWIND -> {
                val amount = intent.getLongExtra(EXTRA_SEEK_POSITION, 15_000L)
                val cur = audioManagerEngine.getCurrentPosition()
                seekToInternal((cur - amount).coerceAtLeast(0L))
            }
            ACTION_STOP -> {
                stopPlaybackInternal()
            }
        }

        return START_NOT_STICKY
    }

    private fun setupWakeLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LabCast:MediaWakeLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create WakeLock: ${e.message}")
        }

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LabCast:MediaWifiLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create WifiLock: ${e.message}")
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 3600 * 1000L) // Safe 3-hour limit
                Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK for audio stability")
            }
        } catch (_: Exception) {}

        try {
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
                Log.d(TAG, "Acquired WIFI_MODE_FULL_HIGH_PERF lock")
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released PARTIAL_WAKE_LOCK")
            }
        } catch (_: Exception) {}

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d(TAG, "Released WifiLock")
            }
        } catch (_: Exception) {}
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Podcast Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live controls and status for system podcast playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "LabCastMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    Log.i(TAG, "[SYSTEM-CONTROL] onPlay triggered from MediaSession / Headset")
                    resumePlaybackInternal()
                }

                override fun onPause() {
                    Log.i(TAG, "[SYSTEM-CONTROL] onPause triggered from MediaSession / Headset")
                    pausePlaybackInternal()
                }

                override fun onSkipToNext() {
                    Log.i(TAG, "[SYSTEM-CONTROL] onSkipToNext (+15s)")
                    val cur = audioManagerEngine.getCurrentPosition()
                    seekToInternal(cur + 15_000L)
                }

                override fun onSkipToPrevious() {
                    Log.i(TAG, "[SYSTEM-CONTROL] onSkipToPrevious (-15s)")
                    val cur = audioManagerEngine.getCurrentPosition()
                    seekToInternal((cur - 15_000L).coerceAtLeast(0L))
                }

                override fun onFastForward() {
                    Log.i(TAG, "[SYSTEM-CONTROL] onFastForward (+15s)")
                    val cur = audioManagerEngine.getCurrentPosition()
                    seekToInternal(cur + 15_000L)
                }

                override fun onRewind() {
                    Log.i(TAG, "[SYSTEM-CONTROL] onRewind (-15s)")
                    val cur = audioManagerEngine.getCurrentPosition()
                    seekToInternal((cur - 15_000L).coerceAtLeast(0L))
                }

                override fun onSeekTo(pos: Long) {
                    Log.i(TAG, "[SYSTEM-CONTROL] onSeekTo pos=${pos}ms from system seeker bar")
                    seekToInternal(pos)
                }

                override fun onStop() {
                    Log.i(TAG, "[SYSTEM-CONTROL] onStop triggered from system control layer")
                    stopPlaybackInternal()
                }
            })
            isActive = true
        }
    }

    private fun setupEngineCallbacks() {
        audioManagerEngine.onCompletionListener = {
            Log.i(TAG, "[SYSTEM CONTROL LAYER] Track completed")
            releaseLocks()
            PodcastPlayerHub.isPlaying.value = false
            updatePlaybackState(PlaybackState.STATE_PAUSED, audioManagerEngine.getCurrentPosition(), 0.0f)
            updateNotification(isPlaying = false)
            PodcastPlayerHub.onEpisodeCompleted?.invoke()
        }

        audioManagerEngine.onErrorListener = { err ->
            Log.e(TAG, "[SYSTEM CONTROL LAYER] Audio Error: $err")
            releaseLocks()
            PodcastPlayerHub.isPlaying.value = false
            PodcastPlayerHub.isBuffering.value = false
            updatePlaybackState(PlaybackState.STATE_ERROR, 0L, 0.0f)
            updateNotification(isPlaying = false)
            PodcastPlayerHub.onError?.invoke(err)
        }

        serviceScope.launch {
            audioManagerEngine.isBuffering.collect { buffering ->
                PodcastPlayerHub.isBuffering.value = buffering
                if (buffering) {
                    updatePlaybackState(PlaybackState.STATE_BUFFERING, audioManagerEngine.getCurrentPosition(), 0.0f)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioSystemManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioSystemManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioSystemManager?.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioSystemManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioSystemManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeTransientLoss = false
                pausePlaybackInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeTransientLoss = PodcastPlayerHub.isPlaying.value
                pausePlaybackInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Keep playing at low volume or pause
                wasPlayingBeforeTransientLoss = PodcastPlayerHub.isPlaying.value
                pausePlaybackInternal()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeTransientLoss) {
                    wasPlayingBeforeTransientLoss = false
                    resumePlaybackInternal()
                }
            }
        }
    }

    private fun startPlaybackInternal(episode: EpisodeEntity, audioTarget: String, startPos: Long) {
        activeEpisode = episode
        currentAudioTarget = audioTarget
        currentArtworkBitmap = null

        PodcastPlayerHub.currentEpisode.value = episode
        PodcastPlayerHub.currentPositionMs.value = startPos
        PodcastPlayerHub.durationMs.value = episode.durationSeconds * 1000L
        PodcastPlayerHub.isPlaying.value = true

        requestAudioFocus()
        acquireLocks()

        // 1. Immediately launch into Foreground Service mode with MediaStyle notification
        val initialNotification = buildNotification(episode, isPlaying = true, artwork = null)
        startServiceInForeground(initialNotification)

        // 2. Update MediaSession metadata
        updateMediaMetadata(episode, artworkBitmap = null)
        updatePlaybackState(PlaybackState.STATE_BUFFERING, startPos, 0.0f)

        // 3. Start audio stream / local file
        audioManagerEngine.play(audioTarget, startPos)
        updatePlaybackState(PlaybackState.STATE_PLAYING, startPos, 1.0f)

        // 4. Asynchronously fetch rich cover art for notification & system lockscreen
        loadArtworkBitmap(episode.podcastCoverUrl)

        // 5. Start live ticker loop for position sync
        startTickerLoop()
    }

    private fun resumePlaybackInternal() {
        requestAudioFocus()
        acquireLocks()
        audioManagerEngine.resume()
        PodcastPlayerHub.isPlaying.value = true

        val currentPos = audioManagerEngine.getCurrentPosition()
        updatePlaybackState(PlaybackState.STATE_PLAYING, currentPos, 1.0f)
        updateNotification(isPlaying = true)
        startTickerLoop()
    }

    private fun pausePlaybackInternal() {
        releaseLocks()
        audioManagerEngine.pause()
        PodcastPlayerHub.isPlaying.value = false

        val currentPos = audioManagerEngine.getCurrentPosition()
        updatePlaybackState(PlaybackState.STATE_PAUSED, currentPos, 0.0f)
        updateNotification(isPlaying = false)
        stopTickerLoop()
    }

    private fun seekToInternal(positionMs: Long) {
        audioManagerEngine.seekTo(positionMs)
        PodcastPlayerHub.currentPositionMs.value = positionMs
        val isPlaying = PodcastPlayerHub.isPlaying.value
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val speed = if (isPlaying) 1.0f else 0.0f
        updatePlaybackState(state, positionMs, speed)
        PodcastPlayerHub.onSeekBySystem?.invoke(positionMs)
    }

    private fun stopPlaybackInternal() {
        stopTickerLoop()
        releaseLocks()
        abandonAudioFocus()
        audioManagerEngine.stop()

        PodcastPlayerHub.isPlaying.value = false
        PodcastPlayerHub.isBuffering.value = false
        updatePlaybackState(PlaybackState.STATE_STOPPED, 0L, 0.0f)

        if (isServiceForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isServiceForeground = false
        }
        stopSelf()
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch(Dispatchers.Main) {
            while (isActive && PodcastPlayerHub.isPlaying.value) {
                val realPos = audioManagerEngine.getCurrentPosition()
                if (realPos >= 0) {
                    PodcastPlayerHub.currentPositionMs.value = realPos
                    val duration = audioManagerEngine.getDuration()
                    if (duration > 0) {
                        PodcastPlayerHub.durationMs.value = duration
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopTickerLoop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updatePlaybackState(state: Int, positionMs: Long, speed: Float) {
        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, positionMs, speed)
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMediaMetadata(episode: EpisodeEntity?, artworkBitmap: Bitmap? = null) {
        if (episode == null) return
        val durationMs = (episode.durationSeconds * 1000L).coerceAtLeast(0L)
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, episode.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, episode.podcastTitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, episode.podcastTitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)

        if (artworkBitmap != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artworkBitmap)
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, artworkBitmap)
        }

        mediaSession?.setMetadata(builder.build())
    }

    private fun loadArtworkBitmap(coverUrl: String) {
        if (coverUrl.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(this@PodcastMediaService)
                    .data(coverUrl)
                    .size(512, 512)
                    .allowHardware(false) // Must be software bitmap for Notification & MediaSession
                    .build()
                val result = Coil.imageLoader(this@PodcastMediaService).execute(request)
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    currentArtworkBitmap = drawable.bitmap
                    withContext(Dispatchers.Main) {
                        updateMediaMetadata(activeEpisode, currentArtworkBitmap)
                        updateNotification(isPlaying = PodcastPlayerHub.isPlaying.value)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load cover artwork for system control notification: ${e.message}")
            }
        }
    }

    private fun buildNotification(
        episode: EpisodeEntity?,
        isPlaying: Boolean,
        artwork: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        builder.setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_podcast_notification)
            .setContentTitle(episode?.title ?: "Podcast Episode")
            .setContentText(episode?.podcastTitle ?: "LabCast")
            .setContentIntent(contentPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        // Action 0: Rewind 15 seconds
        val rewindIntent = createServicePendingIntent(ACTION_REWIND, 1)
        val rewindAction = Notification.Action.Builder(
            R.drawable.ic_replay_10,
            "-15s",
            rewindIntent
        ).build()
        builder.addAction(rewindAction)

        // Action 1: Play / Pause toggle
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseActionIntent = if (isPlaying) {
            createServicePendingIntent(ACTION_PAUSE, 2)
        } else {
            createServicePendingIntent(ACTION_PLAY, 2)
        }
        val playPauseAction = Notification.Action.Builder(
            playPauseIcon,
            playPauseTitle,
            playPauseActionIntent
        ).build()
        builder.addAction(playPauseAction)

        // Action 2: Forward 15 seconds
        val forwardIntent = createServicePendingIntent(ACTION_FORWARD, 3)
        val forwardAction = Notification.Action.Builder(
            R.drawable.ic_forward_30,
            "+15s",
            forwardIntent
        ).build()
        builder.addAction(forwardAction)

        // Action 3: Stop / Dismiss
        val stopIntent = createServicePendingIntent(ACTION_STOP, 4)
        val stopAction = Notification.Action.Builder(
            R.drawable.ic_stop,
            "Stop",
            stopIntent
        ).build()
        builder.addAction(stopAction)

        return builder.build()
    }

    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PodcastMediaService::class.java).apply {
            this.action = action
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun startServiceInForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isServiceForeground = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }
    }

    private fun updateNotification(isPlaying: Boolean = PodcastPlayerHub.isPlaying.value) {
        if (!isServiceForeground && !isPlaying) return
        val notif = buildNotification(activeEpisode, isPlaying, currentArtworkBitmap)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "[SYSTEM CONTROL LAYER] Destroying PodcastMediaService")
        stopTickerLoop()
        releaseLocks()
        abandonAudioFocus()
        audioManagerEngine.release()

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        serviceScope.cancel()
    }
}

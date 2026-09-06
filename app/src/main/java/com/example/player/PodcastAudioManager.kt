package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Robust audio player manager:
 * - Plays real podcast audio streams over HTTP or cached local files using android.media.MediaPlayer.
 * - Tracks buffering state explicitly so the UI timer remains held at 00:00 or resume point while buffering.
 * - Never plays random songs or synthetic fallbacks.
 */
class PodcastAudioManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared.asStateFlow()

    private var isPlaybackRequested = false
    private var currentUrlOrPath: String? = null
    private var requestedStartPositionMs: Long = 0L

    var onCompletionListener: (() -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    @Synchronized
    fun play(urlOrPath: String, startPositionMs: Long = 0) {
        stop()
        isPlaybackRequested = true
        currentUrlOrPath = urlOrPath
        requestedStartPositionMs = startPositionMs
        _isBuffering.value = true
        _isPrepared.value = false

        val msg = "[SYSTEM-CONSOLE-AUDIO] PLAYING target=$urlOrPath startPos=${startPositionMs}ms"
        Log.i("PodcastAudioManager", msg)
        System.out.println(msg)

        if (isLocalFile(urlOrPath)) {
            playLocalFile(urlOrPath, startPositionMs)
        } else if (isValidHttpUrl(urlOrPath)) {
            playHttpStream(urlOrPath, startPositionMs)
        } else {
            val errMsg = "[SYSTEM-CONSOLE-AUDIO] ERROR Invalid audio URL: $urlOrPath"
            Log.e("PodcastAudioManager", errMsg)
            System.err.println(errMsg)
            _isBuffering.value = false
            onErrorListener?.invoke("Invalid audio stream URL")
        }
    }

    private fun isLocalFile(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidHttpUrl(url: String): Boolean {
        return (url.startsWith("http://") || url.startsWith("https://")) &&
                !url.contains("example.com")
    }

    private fun playLocalFile(path: String, startPositionMs: Long) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(path)
                setOnPreparedListener { mp ->
                    _isPrepared.value = true
                    _isBuffering.value = false
                    if (isPlaybackRequested) {
                        if (startPositionMs > 0 && startPositionMs < mp.duration) {
                            mp.seekTo(startPositionMs.toInt())
                        }
                        mp.start()
                    }
                }
                setOnCompletionListener {
                    _isBuffering.value = false
                    onCompletionListener?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    _isBuffering.value = false
                    _isPrepared.value = false
                    Log.e("PodcastAudioManager", "Local MediaPlayer error: what=$what extra=$extra")
                    onErrorListener?.invoke("Local file playback error ($what)")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _isBuffering.value = false
            _isPrepared.value = false
            Log.e("PodcastAudioManager", "Error playing local file: ${e.message}", e)
            onErrorListener?.invoke("File player error: ${e.message}")
        }
    }

    private fun playHttpStream(url: String, startPositionMs: Long) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.parse(url))

                setOnPreparedListener { mp ->
                    _isPrepared.value = true
                    _isBuffering.value = false
                    Log.i("PodcastAudioManager", "Stream prepared successfully. Duration: ${mp.duration}ms")
                    if (isPlaybackRequested) {
                        if (startPositionMs > 0 && startPositionMs < mp.duration) {
                            mp.seekTo(startPositionMs.toInt())
                        }
                        mp.start()
                    }
                }

                setOnInfoListener { _, what, _ ->
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                            _isBuffering.value = true
                            true
                        }
                        MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                            _isBuffering.value = false
                            true
                        }
                        else -> false
                    }
                }

                setOnCompletionListener {
                    _isBuffering.value = false
                    onCompletionListener?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    _isBuffering.value = false
                    _isPrepared.value = false
                    Log.e("PodcastAudioManager", "Stream MediaPlayer error: what=$what extra=$extra")
                    onErrorListener?.invoke("Podcast stream error ($what)")
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            _isBuffering.value = false
            _isPrepared.value = false
            Log.e("PodcastAudioManager", "Error streaming audio: ${e.message}", e)
            onErrorListener?.invoke("Audio stream failed: ${e.message}")
        }
    }

    @Synchronized
    fun pause() {
        isPlaybackRequested = false
        val msg = "[SYSTEM-CONSOLE-AUDIO] PAUSED at ${getCurrentPosition()}ms"
        Log.i("PodcastAudioManager", msg)
        System.out.println(msg)
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun resume() {
        isPlaybackRequested = true
        val msg = "[SYSTEM-CONSOLE-AUDIO] RESUMED at ${getCurrentPosition()}ms"
        Log.i("PodcastAudioManager", msg)
        System.out.println(msg)
        try {
            if (mediaPlayer != null && _isPrepared.value) {
                mediaPlayer?.start()
            } else if (currentUrlOrPath != null) {
                play(currentUrlOrPath!!, requestedStartPositionMs)
            }
        } catch (e: Exception) {
            Log.e("PodcastAudioManager", "Error resuming audio: ${e.message}")
        }
    }

    @Synchronized
    fun seekTo(positionMs: Long) {
        requestedStartPositionMs = positionMs
        val msg = "[SYSTEM-CONSOLE-AUDIO] SEEK TO ${positionMs}ms"
        Log.i("PodcastAudioManager", msg)
        System.out.println(msg)
        try {
            if (_isPrepared.value && mediaPlayer != null) {
                mediaPlayer?.seekTo(positionMs.toInt())
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        isPlaybackRequested = false
        _isBuffering.value = false
        _isPrepared.value = false
        val msg = "[SYSTEM-CONSOLE-AUDIO] STOPPED"
        Log.i("PodcastAudioManager", msg)
        System.out.println(msg)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    fun getCurrentPosition(): Long {
        return try {
            if (mediaPlayer != null && _isPrepared.value) {
                mediaPlayer!!.currentPosition.toLong()
            } else {
                requestedStartPositionMs
            }
        } catch (_: Exception) {
            requestedStartPositionMs
        }
    }

    fun getDuration(): Long {
        return try {
            if (mediaPlayer != null && _isPrepared.value) {
                mediaPlayer!!.duration.toLong()
            } else {
                -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}

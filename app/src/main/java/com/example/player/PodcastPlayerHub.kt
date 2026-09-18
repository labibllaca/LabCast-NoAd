package com.example.player

import com.example.data.EpisodeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton state hub shared between the UI (PodcastViewModel)
 * and the Android System Control Layer (PodcastMediaService).
 *
 * Exposes live playback state, current episode, positions, and callbacks
 * so that controls from the Android Notification Shade, Lock Screen,
 * Quick Settings, Bluetooth devices, and the in-app UI stay 100% in sync.
 */
object PodcastPlayerHub {
    val currentEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val isPlaying = MutableStateFlow(false)
    val isBuffering = MutableStateFlow(false)
    val currentPositionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)

    // Callbacks for ViewModel to react to events initiated from system media controls
    var onEpisodeCompleted: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onSeekBySystem: ((Long) -> Unit)? = null
}

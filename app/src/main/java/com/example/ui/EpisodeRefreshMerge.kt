package com.example.ui

import com.example.data.EpisodeEntity
import com.example.data.ManualAdRangeCodec

/**
 * Rebuilds an episode from feed metadata while keeping the state that only the listener (or a
 * download) can produce. `repository.insertEpisodes` uses `INSERT OR REPLACE`, so any field left
 * out here is written back as its default and silently lost on the next feed refresh.
 */
internal fun EpisodeEntity.withPreservedStateFrom(prev: EpisodeEntity?): EpisodeEntity =
    copy(
        id = prev?.id ?: id,
        isDownloaded = prev?.isDownloaded ?: false,
        downloadLocalPath = prev?.downloadLocalPath,
        downloadTimestamp = prev?.downloadTimestamp ?: 0L,
        playbackPositionMs = prev?.playbackPositionMs ?: 0L,
        isCompleted = prev?.isCompleted ?: false,
        isFavorite = prev?.isFavorite ?: false,
        adTimestampsSeconds = adTimestampsSeconds.ifEmpty { prev?.adTimestampsSeconds.orEmpty() },
        chapters = chapters.ifEmpty { prev?.chapters.orEmpty() },
        transcript = transcript.ifEmpty { prev?.transcript.orEmpty() },
        manualAdRanges = ManualAdRangeCodec.preserveExisting(
            existing = prev?.manualAdRanges.orEmpty(),
            refreshed = manualAdRanges
        ),
        lastPlayedAt = prev?.lastPlayedAt ?: 0L
    )

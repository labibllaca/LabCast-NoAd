package com.example.asr

import com.example.data.EpisodeAdSegmentEntity

/**
 * Which stored ad segments may still drive playback.
 *
 * With ASR off, only the user's own marks are kept: switching detection off
 * stops detection, it does not invalidate a manual correction.
 */
object AdSelection {

    fun visible(
        all: List<EpisodeAdSegmentEntity>,
        asrEnabled: Boolean,
    ): List<EpisodeAdSegmentEntity> = all.filter { ad ->
        ad.confirmed && (asrEnabled || ad.source == AdSource.USER.name)
    }
}

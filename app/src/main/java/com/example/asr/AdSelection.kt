package com.example.asr

import com.example.data.EpisodeAdSegmentEntity

/**
 * Which stored ad segments may still drive playback.
 *
 * With ASR off, only the user's own marks are kept: switching detection off
 * stops detection, it does not invalidate a manual correction. A user mark
 * also hides any detected ad that overlaps it, so a correction cannot be
 * skipped past by the old detection.
 */
object AdSelection {

    fun visible(
        all: List<EpisodeAdSegmentEntity>,
        asrEnabled: Boolean,
    ): List<EpisodeAdSegmentEntity> {
        val entries = all.filter { it.confirmed }
        val users = entries.filter { it.source == AdSource.USER.name }
        return entries.filter { ad ->
            (asrEnabled || ad.source == AdSource.USER.name) &&
                (ad.source == AdSource.USER.name ||
                    users.none { user -> overlaps(ad, user) })
        }
    }

    /**
     * Composes what the player should see for one episode.
     *
     * Stored rows are authoritative for user marks. The controller's list is
     * only used for detected rows, so a mark deleted while a scan is running
     * cannot come back through the controller's in-memory state.
     */
    fun playback(
        stored: List<EpisodeAdSegmentEntity>,
        controllerAds: List<EpisodeAdSegmentEntity>,
        asrEnabled: Boolean,
        episodeId: String,
    ): List<EpisodeAdSegmentEntity> {
        val storedForEpisode = stored.filter { it.episodeId == episodeId }
        val detectedForEpisode = controllerAds.filter {
            it.episodeId == episodeId && it.source != AdSource.USER.name
        }
        return visible(
            all = (storedForEpisode + detectedForEpisode).distinctBy { it.id },
            asrEnabled = asrEnabled,
        )
    }

    private fun overlaps(a: EpisodeAdSegmentEntity, b: EpisodeAdSegmentEntity): Boolean =
        a.startMs < b.endMs && b.startMs < a.endMs
}

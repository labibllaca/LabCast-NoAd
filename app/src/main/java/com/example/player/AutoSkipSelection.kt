package com.example.player

/** The auto-skip rule: only an eligible interval at the playhead may be skipped. */
object AutoSkipSelection {
    fun select(ads: List<AdSkipInterval>, positionMs: Long): AdSkipInterval? =
        ads.firstOrNull { ad ->
            ad.isAutoSkipEligible && positionMs >= ad.startMs && positionMs < ad.endMs
        }
}

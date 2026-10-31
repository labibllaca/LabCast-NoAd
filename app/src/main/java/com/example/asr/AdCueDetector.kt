package com.example.asr

import java.util.Locale

/**
 * Deterministic English ad-boundary detection over timestamped ASR segments.
 *
 * A start cue opens a candidate ad. A resume cue closes it and marks it
 * confirmed. A candidate without a resume cue is capped at [DEFAULT_MAX_AD_MS]
 * and stays unconfirmed, so it can never trigger an automatic skip.
 */
class AdCueDetector(
    private val startPhrases: List<String> = DEFAULT_START_PHRASES,
    private val endPhrases: List<String> = DEFAULT_END_PHRASES,
    private val brands: List<String> = DEFAULT_BRANDS,
    private val promoPhrases: List<String> = DEFAULT_PROMO_PHRASES,
) {

    fun adsFromSegments(
        segments: List<AsrSegment>,
        maxAdMs: Long = DEFAULT_MAX_AD_MS,
    ): List<DetectedAd> {
        if (segments.isEmpty()) return emptyList()

        val ads = mutableListOf<DetectedAd>()
        var adStartMs: Long? = null
        var adStartCue: String? = null
        var adLabel: String = "Sponsored segment"

        segments.forEachIndexed { index, segment ->
            val previous = segments.getOrNull(index - 1)
            val lower = segment.text.lowercase(Locale.ROOT)
            val combined = buildString {
                if (previous != null) {
                    append(previous.text.lowercase(Locale.ROOT))
                    append(' ')
                }
                append(lower)
            }.trim()

            if (adStartMs == null) {
                val startCue = startPhrases.firstOrNull { combined.contains(it) }
                val brand = brands.firstOrNull { combined.contains(it.lowercase(Locale.ROOT)) }
                val promotional = promoPhrases.any { combined.contains(it) }

                if (startCue != null || (brand != null && promotional)) {
                    // If the phrase is split, the previous segment carries its start.
                    adStartMs = if (previous != null && startCue != null && !lower.contains(startCue)) {
                        previous.startMs
                    } else {
                        segment.startMs
                    }
                    adStartCue = startCue ?: "brand + promotional language"
                    adLabel = brand ?: "Sponsored segment"
                }
            } else {
                val endCue = endPhrases.firstOrNull { combined.contains(it) }
                if (endCue != null) {
                    val start = adStartMs!!
                    ads += DetectedAd(
                        startMs = start,
                        endMs = segment.endMs,
                        label = adLabel,
                        confidence = 0.95f,
                        startCue = adStartCue,
                        endCue = endCue,
                        confirmed = true,
                        source = AdSource.ASR,
                    )
                    adStartMs = null
                    adStartCue = null
                } else if (segment.endMs - adStartMs!! >= maxAdMs) {
                    val start = adStartMs!!
                    ads += DetectedAd(
                        startMs = start,
                        endMs = start + maxAdMs,
                        label = adLabel,
                        confidence = 0.8f,
                        startCue = adStartCue,
                        endCue = null,
                        confirmed = false,
                        source = AdSource.ASR,
                    )
                    adStartMs = null
                    adStartCue = null
                }
            }
        }

        val openStart = adStartMs
        if (openStart != null) {
            val lastEnd = segments.last().endMs
            ads += DetectedAd(
                startMs = openStart,
                endMs = minOf(openStart + maxAdMs, lastEnd),
                label = adLabel,
                confidence = 0.8f,
                startCue = adStartCue,
                endCue = null,
                confirmed = false,
                source = AdSource.ASR,
            )
        }

        return ads.sortedBy { it.startMs }
    }

    companion object {
        const val DEFAULT_MAX_AD_MS = 180_000L

        val DEFAULT_START_PHRASES = listOf(
            "sponsored by",
            "brought to you by",
            "support for this podcast",
            "a word from our sponsor",
            "our sponsor today",
            "thanks to our sponsor",
            "this episode is sponsored by",
            "promo code",
            "use code",
            "free trial",
            "ad break",
            "commercial break",
        )

        val DEFAULT_END_PHRASES = listOf(
            "back to the show",
            "back to our conversation",
            "back to the podcast",
            "now back to",
            "let's get back to",
            "welcome back",
        )

        val DEFAULT_PROMO_PHRASES = listOf(
            "promo code",
            "use code",
            "free trial",
            "discount",
            "percent off",
            "% off",
        )

        val DEFAULT_BRANDS = listOf(
            "AG1", "Athletic Greens", "BetterHelp", "NordVPN", "ExpressVPN", "Shopify",
            "Squarespace", "Factor", "Factor Meals", "InsideTracker", "LMNT", "Eight Sleep",
            "SimpliSafe", "Babbel", "Audible", "Manscaped", "DraftKings", "ZipRecruiter",
            "HelloFresh", "SeatGeek", "MeUndies", "Casper", "Warby Parker", "DoorDash",
            "Policygenius", "Uncommon Goods", "Huckberry", "DailyWire+", "DC Universe",
        )
    }
}

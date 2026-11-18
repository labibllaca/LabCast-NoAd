package com.example.asr


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
    private val resumePhrases: List<String> = DEFAULT_RESUME_PHRASES,
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
            val normalized = TextMatch.normalize(segment.text)
            val combined = buildString {
                if (previous != null) {
                    append(TextMatch.normalize(previous.text))
                    append(' ')
                }
                append(normalized)
            }.trim()

            if (adStartMs == null) {
                val startCue = startPhrases.firstOrNull { matches(combined, it) }
                val fuzzyOnlyStart = startCue != null && !containsPhrase(combined, startCue)
                val usableStartCue = if (fuzzyOnlyStart && segment.endMs <= COLD_START_GUARD_MS) {
                    null
                } else {
                    startCue
                }
                val brand = brands.firstOrNull { TextMatch.brandMatches(combined, it) }
                val promotional = promoPhrases.any { matches(combined, it) }

                if (usableStartCue != null || (brand != null && promotional)) {
                    // If the phrase is split, the previous segment carries its start.
                    adStartMs = if (previous != null && usableStartCue != null &&
                        !matches(normalized, usableStartCue)
                    ) {
                        previous.startMs
                    } else {
                        segment.startMs
                    }
                    adStartCue = usableStartCue ?: "brand + promotional language"
                    adLabel = brand ?: "Sponsored segment"
                }
            } else {
                val elapsed = segment.endMs - adStartMs!!
                val endCue = endPhrases.firstOrNull { matches(combined, it) }
                    ?: resumePhrases.firstOrNull { elapsed >= RESUME_MIN_ELAPSED_MS && matches(combined, it) }
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

    private fun containsPhrase(haystack: String, phrase: String): Boolean {
        val hay = TextMatch.normalize(haystack).split(' ').filter { it.isNotEmpty() }
        val target = TextMatch.normalize(phrase).split(' ').filter { it.isNotEmpty() }
        if (target.isEmpty() || target.size > hay.size) return false
        return (0..(hay.size - target.size)).any { start ->
            hay.subList(start, start + target.size) == target
        }
    }

    private fun matches(haystack: String, phrase: String): Boolean =
        containsPhrase(haystack, phrase) ||
            (TextMatch.tokens(phrase).size >= MIN_FUZZY_CUE_TOKENS &&
                TextMatch.fuzzyContains(haystack, phrase, FUZZY_THRESHOLD))

    companion object {
        const val DEFAULT_MAX_AD_MS = 180_000L
        const val COLD_START_GUARD_MS = 8_000L
        const val RESUME_MIN_ELAPSED_MS = 20_000L
        private const val FUZZY_THRESHOLD = 0.84
        // Short cues ("ad break", "use code") are too easy to hit in ordinary
        // speech, so only longer phrases get the fuzzy allowance.
        private const val MIN_FUZZY_CUE_TOKENS = 3

        val DEFAULT_START_PHRASES = listOf(
            "sponsored by",
            "brought to you by",
            "support for this podcast",
            "a word from our sponsor",
            "a word from our sponsors",
            "our sponsor today",
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

        /** Only consulted after [RESUME_MIN_ELAPSED_MS] of an open ad. */
        val DEFAULT_RESUME_PHRASES = listOf(
            "so anyway",
            "where were we",
            "anyway where were we",
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

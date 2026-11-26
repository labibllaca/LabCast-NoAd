package com.example.player

import java.util.Locale

/** A word from the recognizer with its time already offset to the episode timeline. */
data class TimedWord(val text: String, val startMs: Long, val endMs: Long)

/** Turns actually-heard sponsor cues into skip ranges. A brand on its own is not a cue. */
object VozCueDetector {
    val START_PHRASES: List<String> = listOf(
        "sponsored by",
        "brought to you by",
        "a word from our sponsor",
        "thanks to our sponsor",
        "support for this podcast",
        "use promo code",
        "use discount code",
        "use code",
        "special offer",
        "get 20% off",
        "get 15% off",
        "free trial",
        "exclusive discount",
        "pre-roll sponsor",
        "ad break",
        "commercial break"
    )

    val BRANDS: List<String> = listOf(
        "AG1", "Athletic Greens", "BetterHelp", "NordVPN", "ExpressVPN", "Shopify",
        "Squarespace", "Factor", "Factor Meals", "InsideTracker", "LMNT", "Eight Sleep",
        "SimpliSafe", "Babbel", "Audible", "ManScaped", "DraftKings", "ZipRecruiter",
        "HelloFresh", "SeatGeek", "MeUndies", "Casper", "Warby Parker", "DoorDash",
        "Policygenius", "Uncommon Goods"
    )

    private val PROMO_WORDS = listOf("promo", "discount", "offer", "free trial", "coupon")

    fun detect(words: List<TimedWord>): List<SkipRange> {
        val ranges = mutableListOf<SkipRange>()
        var openStartMs: Long? = null
        var openLabel = "Sponsored Segment"

        words.forEach { word ->
            val lower = word.text.lowercase(Locale.ROOT)
            val brand = BRANDS.firstOrNull { lower.contains(it.lowercase(Locale.ROOT)) }
            val isPromotional = START_PHRASES.any { lower.contains(it) } ||
                PROMO_WORDS.any { lower.contains(it) }
            val isResume = PodcastKeywordSpotter.SPONSOR_RESUME_PATTERNS.any { lower.contains(it) }

            if (openStartMs == null && isPromotional) {
                openStartMs = word.startMs
                openLabel = brand ?: "Sponsored Segment"
            } else if (openStartMs != null && isResume) {
                val start = openStartMs
                ranges.add(
                    SkipRange(
                        id = "voz_cue_${start}_${word.startMs}",
                        startMs = start,
                        endMs = word.startMs,
                        label = openLabel,
                        confidence = 0.95f,
                        isAutoSkipEligible = true
                    )
                )
                openStartMs = null
            }
        }

        val start = openStartMs
        if (start != null && words.isNotEmpty()) {
            val end = words.last().endMs
            ranges.add(
                SkipRange(
                    id = "voz_marker_${start}_$end",
                    startMs = start,
                    endMs = end,
                    label = openLabel,
                    confidence = 0.95f,
                    isAutoSkipEligible = false
                )
            )
        }
        return ranges
    }
}

package com.example.data

import java.util.Locale

data class TranscriptSegment(
    val startTimeSeconds: Long,
    val speaker: String,
    val text: String,
    val isSponsor: Boolean = false,
    val sponsorBrand: String? = null
) {
    fun formattedTime(): String {
        val hours = startTimeSeconds / 3600
        val minutes = (startTimeSeconds % 3600) / 60
        val seconds = startTimeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }
}

data class SuggestedAdChunk(
    val segment: TranscriptSegment,
    val reason: String,
    val confidenceScore: Float = 0.90f
)

object TranscriptParser {

    private val sponsorBrands = listOf(
        "Athletic Greens", "AG1", "LMNT", "Eight Sleep", "BetterHelp", "InsideTracker",
        "Shopify", "SimpliSafe", "ExpressVPN", "Huckberry", "Factor Meals", "NordVPN",
        "SquareSpace", "Babbel", "Audible", "ManScaped", "DraftKings", "ZipRecruiter",
        "HelloFresh", "SeatGeek", "MeUndies", "Casper", "Warby Parker", "DoorDash",
        "Boll & Branch", "Policygenius", "Uncommon Goods", "DailyWire+", "DC Universe"
    )

    private val sponsorKeywords = listOf(
        "sponsor", "sponsorship", "sponsored by", "brought to you by", "presenting sponsor",
        "werbung", "werbepartner", "promocode", "promo code", "discount code", "coupon code",
        "special offer", "partner", "commercial break", "ad break", "reklama", "advertisement",
        "advertiser", "use code", "visit ", "check out ", "discount", "off your first",
        "free trial", "risk-free", "money-back guarantee", "link in description"
    )

    private val crossPodcastPromoKeywords = listOf(
        "check out our other podcast", "listen to", "available wherever you get your podcasts",
        "subscribe to", "on apple podcasts", "on spotify", "new episode of", "podcast network",
        "from the creators of", "hosted by", "show notes", "follow us on instagram", "follow us on twitter"
    )

    fun isSponsorText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return sponsorKeywords.any { lower.contains(it) } ||
                sponsorBrands.any { lower.contains(it.lowercase(Locale.ROOT)) } ||
                crossPodcastPromoKeywords.any { lower.contains(it) }
    }

    fun detectSponsorBrand(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return sponsorBrands.firstOrNull { lower.contains(it.lowercase(Locale.ROOT)) }
    }

    fun analyzeTranscriptForSuggestedAds(segments: List<TranscriptSegment>): List<SuggestedAdChunk> {
        val suggestions = mutableListOf<SuggestedAdChunk>()
        for (seg in segments) {
            val textLower = seg.text.lowercase(Locale.ROOT)
            val speakerLower = seg.speaker.lowercase(Locale.ROOT)

            val brand = detectSponsorBrand(seg.text) ?: detectSponsorBrand(seg.speaker)
            if (brand != null) {
                suggestions.add(SuggestedAdChunk(seg, "Sponsor Brand: $brand", 0.95f))
                continue
            }

            val keywordMatch = sponsorKeywords.firstOrNull { textLower.contains(it) || speakerLower.contains(it) }
            if (keywordMatch != null) {
                suggestions.add(SuggestedAdChunk(seg, "Advertiser Keyword ($keywordMatch)", 0.90f))
                continue
            }

            val promoMatch = crossPodcastPromoKeywords.firstOrNull { textLower.contains(it) || speakerLower.contains(it) }
            if (promoMatch != null) {
                suggestions.add(SuggestedAdChunk(seg, "Cross-Podcast Promo ($promoMatch)", 0.85f))
                continue
            }

            if (Regex("""\b(code|promo|discount)\b.*\b[A-Z0-9]{3,10}\b""", RegexOption.IGNORE_CASE).containsMatchIn(seg.text) ||
                Regex("""\bhttps?://|\b\w+\.(com|org|net|co|de|io)/""", RegexOption.IGNORE_CASE).containsMatchIn(seg.text)) {
                suggestions.add(SuggestedAdChunk(seg, "Promo Code / Website URL", 0.88f))
            }
        }
        return suggestions
    }

    fun serializeSegmentsToTranscriptText(segments: List<TranscriptSegment>): String {
        return segments.joinToString("\n") { seg ->
            val timeFormatted = seg.formattedTime()
            val sponsorTag = if (seg.isSponsor) " [Sponsor Break]" else ""
            "$timeFormatted [${seg.speaker}$sponsorTag] ${seg.text}"
        }
    }

    fun parseTimeToSeconds(timeStr: String): Long {
        return try {
            val cleaned = timeStr.trim()
                .removePrefix("[").removeSuffix("]")
                .removePrefix("(").removeSuffix(")")
                .trim()

            if (cleaned.contains(":")) {
                val parts = cleaned.split(":")
                if (parts.size == 3) {
                    val h = parts[0].trim().toLongOrNull() ?: 0L
                    val m = parts[1].trim().toLongOrNull() ?: 0L
                    val sSec = parts[2].trim().replace(",", ".")
                    val s = sSec.toDoubleOrNull()?.toLong() ?: 0L
                    h * 3600 + m * 60 + s
                } else if (parts.size == 2) {
                    val m = parts[0].trim().toLongOrNull() ?: 0L
                    val sSec = parts[1].trim().replace(",", ".")
                    val s = sSec.toDoubleOrNull()?.toLong() ?: 0L
                    m * 60 + s
                } else {
                    0L
                }
            } else {
                cleaned.replace(",", ".").toDoubleOrNull()?.toLong() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun parseTranscript(
        rawTranscript: String?,
        safeDuration: Long = 1800L
    ): List<TranscriptSegment> {
        if (rawTranscript.isNullOrBlank()) {
            return emptyList()
        }

        val parsedSegments = parseRawTranscriptLines(rawTranscript, safeDuration)
        if (parsedSegments.isEmpty()) {
            return emptyList()
        }

        val allZero = parsedSegments.size > 1 && parsedSegments.all { it.startTimeSeconds == 0L }
        return if (allZero) {
            val step = (safeDuration - 30L).coerceAtLeast(10L) / parsedSegments.size.coerceAtLeast(1)
            parsedSegments.mapIndexed { index, seg ->
                seg.copy(startTimeSeconds = index * step)
            }
        } else {
            parsedSegments.sortedBy { it.startTimeSeconds }
        }
    }

    fun parseOrGenerateTranscript(
        rawTranscript: String?,
        episodeTitle: String = "",
        episodeDescription: String = "",
        durationSeconds: Long = 1800L,
        chapters: List<PodcastChapter> = emptyList()
    ): List<TranscriptSegment> {
        val safeDuration = if (durationSeconds > 0L) durationSeconds else 1800L
        return parseTranscript(rawTranscript, safeDuration)
    }

    private fun parseRawTranscriptLines(rawTranscript: String, durationSeconds: Long): List<TranscriptSegment> {
        val segments = mutableListOf<TranscriptSegment>()
        val lines = rawTranscript.split("\n")

        var pendingCueTime: Long? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.matches(Regex("""^\d+$"""))) {
                continue
            }

            // WebVTT / SRT cue timing line: "00:01:23.456 --> 00:01:28.123"
            val cueMatch = Regex("""^(\d{1,2}:\d{2}(?::\d{2})?(?:[.,]\d+)?)\s*-->""").find(line)
            if (cueMatch != null) {
                pendingCueTime = parseTimeToSeconds(cueMatch.groupValues[1])
                continue
            }

            // Regex 1: Matches "[01:30] Host: Text" or "(01:30) Host: Text" or "[01:30.500] [Host] Text"
            val bracketedMatch = Regex("""^[\[\(](\d{1,2}:\d{2}(?::\d{2})?(?:[.,]\d+)?)[\]\)]\s*(?:\[(.*?)\]|([^:]+):)?\s*(.*)""").find(line)
            if (bracketedMatch != null) {
                val timeSec = parseTimeToSeconds(bracketedMatch.groupValues[1])
                val speaker = bracketedMatch.groupValues[2].ifEmpty { bracketedMatch.groupValues[3] }.trim().ifEmpty { "Host" }
                val text = bracketedMatch.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    val isSponsor = isSponsorText(text) || isSponsorText(speaker)
                    val brand = detectSponsorBrand(text)
                    segments.add(TranscriptSegment(timeSec, speaker, text, isSponsor, brand))
                    pendingCueTime = null
                    continue
                }
            }

            // Regex 2: Matches "01:30 [Host] Text" or "01:30 Host: Text" or "01:30 - Text"
            val unbracketedMatch = Regex("""^(\d{1,2}:\d{2}(?::\d{2})?(?:[.,]\d+)?)\s*(?:\[(.*?)\]|([a-zA-Z0-9\s_]+):|-)?\s*(.*)""").find(line)
            if (unbracketedMatch != null) {
                val timeSec = parseTimeToSeconds(unbracketedMatch.groupValues[1])
                val speaker = unbracketedMatch.groupValues[2].ifEmpty { unbracketedMatch.groupValues[3] }.trim().ifEmpty { "Host" }
                val text = unbracketedMatch.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    val isSponsor = isSponsorText(text) || isSponsorText(speaker)
                    val brand = detectSponsorBrand(text)
                    segments.add(TranscriptSegment(timeSec, speaker, text, isSponsor, brand))
                    pendingCueTime = null
                    continue
                }
            }

            // If preceded by a WebVTT/SRT cue
            if (pendingCueTime != null) {
                val speakerMatch = Regex("""^<v\s+([^>]+)>|^([a-zA-Z0-9\s_]+):\s*(.*)""").find(line)
                val (speaker, text) = if (speakerMatch != null) {
                    val s = speakerMatch.groupValues[1].ifEmpty { speakerMatch.groupValues[2] }.trim()
                    val t = line.replace(Regex("""^<v\s+[^>]+>|^[a-zA-Z0-9\s_]+:\s*"""), "").trim()
                    Pair(s.ifEmpty { "Speaker" }, t)
                } else {
                    Pair("Speaker", line)
                }

                val cleanText = text.replace(Regex("<.*?>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    val isSponsor = isSponsorText(cleanText) || isSponsorText(speaker)
                    val brand = detectSponsorBrand(cleanText)
                    segments.add(TranscriptSegment(pendingCueTime, speaker, cleanText, isSponsor, brand))
                    pendingCueTime = null
                    continue
                }
            }

            // Plain text line without explicit timestamp
            val isSponsor = isSponsorText(line)
            val brand = detectSponsorBrand(line)
            val speakerMatch = Regex("""^([a-zA-Z0-9\s_]{2,20}):\s*(.*)""").find(line)
            val speaker = speakerMatch?.groupValues?.get(1)?.trim() ?: "Host"
            val text = speakerMatch?.groupValues?.get(2)?.trim() ?: line
            segments.add(TranscriptSegment(0L, speaker, text, isSponsor, brand))
        }

        return segments
    }
}


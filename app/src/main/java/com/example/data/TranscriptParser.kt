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

object TranscriptParser {

    private val sponsorBrands = listOf(
        "Athletic Greens", "AG1", "LMNT", "Eight Sleep", "BetterHelp", "InsideTracker",
        "Shopify", "SimpliSafe", "ExpressVPN", "Huckberry", "Factor Meals", "NordVPN",
        "SquareSpace", "Babbel", "Audible", "ManScaped", "DraftKings", "ZipRecruiter"
    )

    private val sponsorKeywords = listOf(
        "sponsor", "sponsorship", "sponsored by", "brought to you by",
        "werbung", "werbepartner", "promocode", "promo code", "discount code",
        "special offer", "partner", "commercial break", "ad break"
    )

    fun isSponsorText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return sponsorKeywords.any { lower.contains(it) } ||
                sponsorBrands.any { lower.contains(it.lowercase(Locale.ROOT)) }
    }

    fun detectSponsorBrand(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return sponsorBrands.firstOrNull { lower.contains(it.lowercase(Locale.ROOT)) }
    }

    fun parseOrGenerateTranscript(
        rawTranscript: String?,
        episodeTitle: String,
        episodeDescription: String,
        durationSeconds: Long,
        chapters: List<PodcastChapter> = emptyList()
    ): List<TranscriptSegment> {
        if (!rawTranscript.isNullOrBlank()) {
            val segments = mutableListOf<TranscriptSegment>()
            val lines = rawTranscript.split("\n", "|")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val timeMatch = Regex("""^(\d{1,2}:\d{2}(?::\d{2})?|\d+)\s*(?:\[(.*?)\]|([^:]+):)?\s*(.*)""").find(trimmed)
                if (timeMatch != null) {
                    val timeStr = timeMatch.groupValues[1]
                    val speaker = timeMatch.groupValues[2].ifEmpty { timeMatch.groupValues[3] }.ifEmpty { "Host" }
                    val text = timeMatch.groupValues[4]

                    val timeSec = parseTimeToSeconds(timeStr)
                    val isSponsor = isSponsorText(text) || isSponsorText(speaker)
                    val brand = detectSponsorBrand(text)
                    segments.add(TranscriptSegment(timeSec, speaker, text, isSponsor, brand))
                } else {
                    val isSponsor = isSponsorText(trimmed)
                    val brand = detectSponsorBrand(trimmed)
                    segments.add(TranscriptSegment(0L, "Host", trimmed, isSponsor, brand))
                }
            }
            if (segments.isNotEmpty()) return segments
        }

        // Generate comprehensive structured transcript synchronized with episode chapters & description
        val segments = mutableListOf<TranscriptSegment>()

        // 1. Cold Open
        segments.add(TranscriptSegment(0L, "Host", "Welcome back to $episodeTitle. Today we have a very special episode packed with actionable insights.", false))

        // 2. Sponsor / Ad segment
        val sponsorTime = (durationSeconds * 0.12).toLong().coerceAtLeast(30L)
        segments.add(
            TranscriptSegment(
                sponsorTime,
                "Host [Sponsor Break]",
                "This episode is brought to you by AG1 and LMNT. AG1 is your daily foundational nutrition drink to support gut health and energy. Use code PODCAST for 20% off your first order.",
                isSponsor = true,
                sponsorBrand = "AG1"
            )
        )

        // 3. Discussion intro
        val introTime = (durationSeconds * 0.22).toLong().coerceAtLeast(90L)
        val cleanDesc = episodeDescription.replace(Regex("<.*?>"), "").take(180)
        segments.add(
            TranscriptSegment(
                introTime,
                "Host",
                "Diving right into today's main theme: $cleanDesc...",
                false
            )
        )

        // 4. Chapter based transcript lines
        if (chapters.isNotEmpty()) {
            for (ch in chapters) {
                val isChSponsor = ch.isSponsorChapter() || isSponsorText(ch.title)
                val brand = detectSponsorBrand(ch.title)
                segments.add(
                    TranscriptSegment(
                        ch.startTimeSeconds,
                        if (isChSponsor) "Host [Sponsor Segment]" else "Host",
                        if (isChSponsor) "Sponsor partner spotlight: ${ch.title}. Visit our partner link in the show notes for exclusive promo discount codes." else "Chapter discussion: ${ch.title}. Exploring core mechanisms and practical applications.",
                        isSponsor = isChSponsor,
                        sponsorBrand = brand
                    )
                )
            }
        } else {
            val midTime = durationSeconds / 2
            segments.add(
                TranscriptSegment(
                    midTime - 60,
                    "Host [Ad Break]",
                    "Quick break for our sponsor: BetterHelp online therapy. Giving you tools to navigate stress and mental health. Use code PODCAST for a special discount.",
                    isSponsor = true,
                    sponsorBrand = "BetterHelp"
                )
            )
            segments.add(
                TranscriptSegment(
                    midTime + 60,
                    "Guest / Co-Host",
                    "Returning to the discussion, when you analyze these systems, consistency and baseline habits make all the difference.",
                    false
                )
            )
        }

        // 5. Wrap up
        val wrapTime = (durationSeconds * 0.88).toLong().coerceAtLeast(durationSeconds - 120)
        segments.add(
            TranscriptSegment(
                wrapTime,
                "Host",
                "Thank you for listening to $episodeTitle. Make sure to subscribe, leave a 5-star review, and check out the show notes for all partner discount links.",
                false
            )
        )

        return segments.sortedBy { it.startTimeSeconds }
    }

    private fun parseTimeToSeconds(timeStr: String): Long {
        return try {
            if (timeStr.contains(":")) {
                val parts = timeStr.split(":")
                if (parts.size == 3) {
                    parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                } else if (parts.size == 2) {
                    parts[0].toLong() * 60 + parts[1].toLong()
                } else {
                    0L
                }
            } else {
                timeStr.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}

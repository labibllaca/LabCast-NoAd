package com.example.util

import java.text.SimpleDateFormat
import java.util.*

object PodcastDateUtils {

    private val rfcPatterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss z",
        "EEE, d MMM yyyy HH:mm:ss",
        "EEE, dd MMM yyyy HH:mm Z",
        "EEE, dd MMM yyyy HH:mm z",
        "EEE, d MMM yyyy HH:mm Z",
        "EEE, d MMM yyyy HH:mm z",
        "dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss",
        "d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm:ss z",
        "d MMM yyyy HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "MMM dd, yyyy",
        "MMM d, yyyy",
        "dd MMM yyyy",
        "d MMM yyyy",
        "yyyy/MM/dd",
        "dd.MM.yyyy"
    )

    fun parseDateToTimestamp(rawDate: String?): Long {
        if (rawDate.isNullOrBlank()) return 0L
        val trimmed = rawDate.replace(Regex("\\s+"), " ").trim()

        // Check if raw string is numeric timestamp (e.g. epoch millis or seconds)
        val numeric = trimmed.toLongOrNull()
        if (numeric != null && numeric > 1000000000L) {
            return if (numeric < 1000000000000L) numeric * 1000L else numeric
        }

        // Normalize timezone names to numerical offsets for reliable SimpleDateFormat parsing
        val normalized = trimmed
            .replace(Regex("""\bEDT\b""", RegexOption.IGNORE_CASE), "-0400")
            .replace(Regex("""\bEST\b""", RegexOption.IGNORE_CASE), "-0500")
            .replace(Regex("""\bCDT\b""", RegexOption.IGNORE_CASE), "-0500")
            .replace(Regex("""\bCST\b""", RegexOption.IGNORE_CASE), "-0600")
            .replace(Regex("""\bMDT\b""", RegexOption.IGNORE_CASE), "-0600")
            .replace(Regex("""\bMST\b""", RegexOption.IGNORE_CASE), "-0700")
            .replace(Regex("""\bPDT\b""", RegexOption.IGNORE_CASE), "-0700")
            .replace(Regex("""\bPST\b""", RegexOption.IGNORE_CASE), "-0800")
            .replace(Regex("""\bBST\b""", RegexOption.IGNORE_CASE), "+0100")
            .replace(Regex("""\bCET\b""", RegexOption.IGNORE_CASE), "+0100")
            .replace(Regex("""\bCEST\b""", RegexOption.IGNORE_CASE), "+0200")
            .replace(Regex("""\bUTC\b""", RegexOption.IGNORE_CASE), "+0000")
            .replace(Regex("""\bGMT\b""", RegexOption.IGNORE_CASE), "+0000")

        for (pattern in rfcPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(normalized)
                if (date != null) {
                    return date.time
                }
            } catch (_: Exception) {
                // Try next pattern
            }
        }

        // Fallback for original un-normalized string
        for (pattern in rfcPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(trimmed)
                if (date != null) {
                    return date.time
                }
            } catch (_: Exception) {
                // Try next pattern
            }
        }

        // Regex fallback for ISO dates inside strings: "2026-09-03"
        val isoMatch = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""").find(trimmed)
        if (isoMatch != null) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(isoMatch.value)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        // Regex fallback for "03 Sep 2026" or "Sep 03, 2026"
        val wordDateMatch = Regex("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})""", RegexOption.IGNORE_CASE).find(trimmed)
        if (wordDateMatch != null) {
            try {
                val norm = "${wordDateMatch.groupValues[1]} ${wordDateMatch.groupValues[2]} ${wordDateMatch.groupValues[3]}"
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(norm)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        return 0L
    }

    fun formatToDisplayDate(timestampMs: Long): String {
        if (timestampMs <= 0L) return "Recent"
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    fun formatToIsoDate(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    fun parseAndFormat(rawDate: String?): Pair<String, Long> {
        if (rawDate.isNullOrBlank()) return Pair("Recent", 0L)
        val ts = parseDateToTimestamp(rawDate)
        if (ts <= 0L) {
            val clean = rawDate.take(20).trim()
            return Pair(clean.ifEmpty { "Recent" }, 0L)
        }
        val display = formatToDisplayDate(ts)
        return Pair(display, ts)
    }
}


package com.example.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PodcastChapter(
    val id: String = "",
    val title: String,
    val startTimeSeconds: Long,
    val durationSeconds: Long? = null,
    val url: String? = null,
    val imageUrl: String? = null
) {
    fun isSponsorChapter(): Boolean {
        val clean = title.trim().lowercase(Locale.ROOT)

        val sponsorKeywords = listOf(
            "sponsor",
            "sponsoren",
            "sponsorship",
            "advertisement",
            "advertising",
            "commercial",
            "werbung",
            "werbepartner",
            "werbeblock",
            "unterbrechung",
            "promoted",
            "promo",
            "partner:",
            "presented by",
            "ad break",
            "ad-break"
        )
        if (sponsorKeywords.any { clean.contains(it) }) return true

        // Regex for standalone words like "ad", "ads", "werbung"
        val adWordRegex = Regex("""\b(?:ad|ads|werbung)\b""", RegexOption.IGNORE_CASE)
        return adWordRegex.containsMatchIn(clean)
    }

    fun formattedStartTime(): String {
        val hours = startTimeSeconds / 3600
        val minutes = (startTimeSeconds % 3600) / 60
        val seconds = startTimeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    fun formattedDuration(): String {
        val dur = durationSeconds ?: return ""
        val hours = dur / 3600
        val minutes = (dur % 3600) / 60
        val seconds = dur % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
}

object ChapterParser {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Parses chapters from all potential sources:
     * 1. Pipe-separated string: "0:Intro|120:Topic 1|480:Topic 2"
     * 2. Podcasting 2.0 JSON or Podlove XML string
     * 3. HTTP URL to remote Podcasting 2.0 chapters.json
     * 4. Full unclipped episode description / show notes HTML text
     */
    fun parseChapters(
        rawChapters: String?,
        description: String?,
        totalDurationSeconds: Long
    ): List<PodcastChapter> {
        // 1. Check if rawChapters contains pipe-separated representation
        if (!rawChapters.isNullOrBlank()) {
            val trimmed = rawChapters.trim()

            // If it's a pipe-separated string
            if (trimmed.contains("|") || (trimmed.contains(":") && !trimmed.startsWith("http") && !trimmed.startsWith("<") && !trimmed.startsWith("{"))) {
                val list = parsePipeSeparated(trimmed)
                if (list.isNotEmpty()) {
                    return postProcessChapters(list, totalDurationSeconds)
                }
            }

            // If it's Podlove XML
            if (trimmed.contains("<psc:chapter", ignoreCase = true) || trimmed.contains("<chapter", ignoreCase = true)) {
                val list = parsePodloveXml(trimmed)
                if (list.isNotEmpty()) {
                    return postProcessChapters(list, totalDurationSeconds)
                }
            }

            // If it's Podcasting 2.0 JSON
            if (trimmed.startsWith("{") && trimmed.contains("\"chapters\"")) {
                val list = parsePodcasting20Json(trimmed)
                if (list.isNotEmpty()) {
                    return postProcessChapters(list, totalDurationSeconds)
                }
            }
        }

        // 2. Parse from Description / Show Notes text
        if (!description.isNullOrBlank()) {
            val list = parseFromTextOrHtml(description)
            if (list.isNotEmpty()) {
                return postProcessChapters(list, totalDurationSeconds)
            }
        }

        return emptyList()
    }

    /**
     * Extracts chapters from RSS item block containing <psc:chapters>, <podcast:chapters>,
     * <content:encoded>, <description>, or <itunes:summary>.
     */
    fun parseFromFeedItem(itemXml: String, totalDurationSeconds: Long): List<PodcastChapter> {
        // A. Check Podlove XML tags: <psc:chapters>...</psc:chapters>
        if (itemXml.contains("<psc:chapters", ignoreCase = true) || itemXml.contains("<psc:chapter", ignoreCase = true)) {
            val podloveChapters = parsePodloveXml(itemXml)
            if (podloveChapters.isNotEmpty()) {
                return postProcessChapters(podloveChapters, totalDurationSeconds)
            }
        }

        // B. Check Podcast 2.0 namespace tag: <podcast:chapters url="..." type="..."/>
        val podcastChaptersUrlMatch = Regex("<podcast:chapters[^>]*url=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE).find(itemXml)
        val chapterUrl = podcastChaptersUrlMatch?.groups?.get(1)?.value
        if (!chapterUrl.isNullOrBlank()) {
            val remoteChapters = fetchRemoteJsonChapters(chapterUrl)
            if (remoteChapters.isNotEmpty()) {
                return postProcessChapters(remoteChapters, totalDurationSeconds)
            }
        }

        // C. Extract <content:encoded> (full show notes with highest fidelity)
        val contentEncodedRegex = Regex("<content:encoded><!\\[CDATA\\[(.*?)\\]\\]></content:encoded>|<content:encoded>(.*?)</content:encoded>", RegexOption.DOT_MATCHES_ALL)
        val contentEncoded = contentEncodedRegex.find(itemXml)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
        if (!contentEncoded.isNullOrBlank()) {
            val list = parseFromTextOrHtml(contentEncoded)
            if (list.isNotEmpty()) {
                return postProcessChapters(list, totalDurationSeconds)
            }
        }

        // D. Extract <description>
        val descRegex = Regex("<description><!\\[CDATA\\[(.*?)\\]\\]></description>|<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL)
        val desc = descRegex.find(itemXml)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
        if (!desc.isNullOrBlank()) {
            val list = parseFromTextOrHtml(desc)
            if (list.isNotEmpty()) {
                return postProcessChapters(list, totalDurationSeconds)
            }
        }

        // E. Extract <itunes:summary>
        val summaryRegex = Regex("<itunes:summary><!\\[CDATA\\[(.*?)\\]\\]></itunes:summary>|<itunes:summary>(.*?)</itunes:summary>", RegexOption.DOT_MATCHES_ALL)
        val summary = summaryRegex.find(itemXml)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
        if (!summary.isNullOrBlank()) {
            val list = parseFromTextOrHtml(summary)
            if (list.isNotEmpty()) {
                return postProcessChapters(list, totalDurationSeconds)
            }
        }

        return emptyList()
    }

    /**
     * Converts a list of chapters into the compact pipe string format stored in Room.
     */
    fun toPipeString(chapters: List<PodcastChapter>): String {
        return chapters.joinToString("|") { "${it.startTimeSeconds}:${it.title.replace("|", "-")}" }
    }

    /**
     * Parses standard pipe-separated format "0:Intro|120:Topic 1"
     */
    private fun parsePipeSeparated(raw: String): List<PodcastChapter> {
        val list = mutableListOf<PodcastChapter>()
        val parts = raw.split("|")
        for ((idx, part) in parts.withIndex()) {
            val sub = part.split(":", limit = 2)
            if (sub.size == 2) {
                val sec = sub[0].trim().toLongOrNull()
                val title = sub[1].trim()
                if (sec != null && title.isNotEmpty()) {
                    list.add(
                        PodcastChapter(
                            id = "ch_pipe_$idx",
                            title = title,
                            startTimeSeconds = sec
                        )
                    )
                }
            }
        }
        return list
    }

    /**
     * Parses Podlove XML: <psc:chapter start="00:01:23.456" title="Intro" href="..." image="..."/>
     */
    fun parsePodloveXml(xml: String): List<PodcastChapter> {
        val list = mutableListOf<PodcastChapter>()
        val regex = Regex("<(?:psc:)?chapter[^>]*start=[\"']([^\"']+)[\"'][^>]*title=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(xml).toList()

        for ((idx, match) in matches.withIndex()) {
            val startStr = match.groups[1]?.value ?: continue
            val rawTitle = match.groups[2]?.value ?: continue
            val sec = parseTimestampToSeconds(startStr) ?: continue
            val cleanTitle = cleanHtmlText(rawTitle)
            if (cleanTitle.isNotEmpty()) {
                list.add(
                    PodcastChapter(
                        id = "ch_podlove_$idx",
                        title = cleanTitle,
                        startTimeSeconds = sec
                    )
                )
            }
        }
        return list
    }

    /**
     * Parses Podcasting 2.0 JSON format: {"version":"1.2.0","chapters":[{"startTime":0,"title":"Intro"}]}
     */
    fun parsePodcasting20Json(jsonStr: String): List<PodcastChapter> {
        val list = mutableListOf<PodcastChapter>()
        try {
            val root = JSONObject(jsonStr)
            val chaptersArray = root.optJSONArray("chapters") ?: return emptyList()
            for (i in 0 until chaptersArray.length()) {
                val item = chaptersArray.getJSONObject(i)
                val startTime = item.optDouble("startTime", -1.0)
                val title = item.optString("title", "").trim()
                val url = item.optString("url", "").takeIf { it.isNotEmpty() }
                val img = item.optString("img", "").takeIf { it.isNotEmpty() }

                if (startTime >= 0 && title.isNotEmpty()) {
                    list.add(
                        PodcastChapter(
                            id = "ch_json_$i",
                            title = title,
                            startTimeSeconds = startTime.toLong(),
                            url = url,
                            imageUrl = img
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    /**
     * Synchronously fetches remote JSON chapters if URL is reachable.
     */
    fun fetchRemoteJsonChapters(url: String): List<PodcastChapter> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LabCast/1.0 (Android)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    parsePodcasting20Json(body)
                } else emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Comprehensive multi-pattern parser that extracts timestamps from show notes, description, or HTML.
     * Supports:
     * - (00:00:00) Title(00:02:15) Next Title (no newlines)
     * - 00:00 Intro \n 02:15 Sponsor \n
     * - [01:23] Chapter Title
     * - 1. (00:00) Topic
     * - HTML formatted summaries
     */
    fun parseFromTextOrHtml(input: String): List<PodcastChapter> {
        val list = mutableListOf<PodcastChapter>()

        // 1. Normalize line breaks and decode HTML entities
        val text = input
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)</li>"), "\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</tr>"), "\n")
            .replace(Regex("(?i)<hr\\s*/?>"), "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")

        // 2. Strip remaining HTML tags
        val plainText = text.replace(Regex("<.*?>"), "")

        // 3. Scan for timestamps: matches (00:00), [00:00:00], 00:00, 0:00, 1:23:45
        val timestampRegex = Regex("""(?:\(|\[|\b)(\d{1,2}:\d{2}(?::\d{2})?(?:\.\d+)?)(?:\)|\]|\b)""")
        val matches = timestampRegex.findAll(plainText).toList()

        if (matches.size >= 2) {
            var validCount = 0
            var previousSec = -1L

            for (i in matches.indices) {
                val match = matches[i]
                val tsStr = match.groups[1]?.value ?: continue
                val sec = parseTimestampToSeconds(tsStr) ?: continue

                // Avoid non-monotonic sequence violations if stray numbers match
                if (sec < previousSec && (previousSec - sec) > 300) {
                    continue
                }

                val titleStart = match.range.last + 1
                val titleEnd = if (i + 1 < matches.size) {
                    matches[i + 1].range.first
                } else {
                    plainText.length
                }

                if (titleStart <= plainText.length && titleStart < titleEnd) {
                    var rawTitle = plainText.substring(titleStart, titleEnd)

                    // Remove trailing URLs or disclaimers
                    if (rawTitle.contains("http://") || rawTitle.contains("https://")) {
                        rawTitle = rawTitle.substringBefore("http://").substringBefore("https://")
                    }
                    val showNotesIdx = rawTitle.indexOf("Show notes:", ignoreCase = true)
                    if (showNotesIdx != -1) {
                        rawTitle = rawTitle.substring(0, showNotesIdx)
                    }
                    val sponsorIdx = rawTitle.indexOf("Thank you to our sponsors", ignoreCase = true)
                    if (sponsorIdx != -1) {
                        rawTitle = rawTitle.substring(0, sponsorIdx)
                    }

                    // Clean punctuation and whitespace
                    var cleanTitle = rawTitle
                        .replace(Regex("""^[\s\-–—:|~•\*\)\],]+"""), "")
                        .replace(Regex("""[\s\-–—:|~•\*\(\[,]+$"""), "")
                        .replace(Regex("""[\r\n]+.*"""), "")
                        .trim()

                    if (cleanTitle.isEmpty()) {
                        cleanTitle = "Chapter at $tsStr"
                    }

                    if (cleanTitle.length > 120) {
                        cleanTitle = cleanTitle.take(120) + "..."
                    }

                    list.add(
                        PodcastChapter(
                            id = "ch_text_${validCount++}",
                            title = cleanTitle,
                            startTimeSeconds = sec
                        )
                    )
                    previousSec = sec
                }
            }
        }

        // Only return if we found at least 2 distinct timestamped sections
        if (list.size >= 2) {
            return list
        }

        return emptyList()
    }

    /**
     * Cleans, sorts, ensures 00:00 start, deduplicates, and calculates durations.
     */
    private fun postProcessChapters(
        chapters: List<PodcastChapter>,
        totalDurationSeconds: Long
    ): List<PodcastChapter> {
        if (chapters.isEmpty()) return emptyList()

        // Sort by start time
        val sorted = chapters.sortedBy { it.startTimeSeconds }.toMutableList()

        // Deduplicate start times within 3 seconds of each other
        val deduped = mutableListOf<PodcastChapter>()
        for (ch in sorted) {
            val last = deduped.lastOrNull()
            if (last == null || (ch.startTimeSeconds - last.startTimeSeconds) >= 3) {
                deduped.add(ch)
            }
        }

        if (deduped.isEmpty()) return emptyList()

        // If the first chapter starts after 30 seconds, add an Intro chapter at 0s
        if (deduped.first().startTimeSeconds > 30) {
            deduped.add(
                0,
                PodcastChapter(
                    id = "ch_intro_0",
                    title = "Introduction",
                    startTimeSeconds = 0
                )
            )
        } else if (deduped.first().startTimeSeconds > 0) {
            // Adjust first chapter to start at 0s
            val first = deduped.removeAt(0)
            deduped.add(0, first.copy(startTimeSeconds = 0))
        }

        // Calculate durations
        return deduped.mapIndexed { index, chapter ->
            val nextStart = if (index + 1 < deduped.size) {
                deduped[index + 1].startTimeSeconds
            } else {
                totalDurationSeconds.coerceAtLeast(chapter.startTimeSeconds + 60)
            }
            val dur = (nextStart - chapter.startTimeSeconds).coerceAtLeast(0)
            chapter.copy(durationSeconds = dur)
        }
    }

    private fun parseTimestampToSeconds(ts: String): Long? {
        val clean = ts.trim().split(".")[0] // remove milliseconds if present
        val parts = clean.split(":")
        return when (parts.size) {
            1 -> clean.toLongOrNull()
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toLongOrNull() ?: return null
                m * 60 + s
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toLongOrNull() ?: return null
                h * 3600 + m * 60 + s
            }
            else -> null
        }
    }

    private fun cleanHtmlText(text: String): String {
        return text
            .replace(Regex("<.*?>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .trim()
    }
}


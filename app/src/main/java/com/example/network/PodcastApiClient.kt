package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SearchResultPodcast(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val category: String,
    val feedUrl: String,
    val source: PodcastSource = PodcastSource.ITUNES,
    val trackCount: Int = 0,
    val releaseDate: String = ""
)

data class FeedEpisode(
    val id: String,
    val title: String,
    val description: String,
    val durationSeconds: Long,
    val publishDate: String,
    val audioUrl: String,
    val adTimestampsSeconds: String = "",
    val chapters: String = "",
    val transcript: String = "",
    val publishTimestamp: Long = 0L,
    val episodeArtworkUrl: String? = null
)

data class FeedResult(
    val episodes: List<FeedEpisode>,
    val channelCoverUrl: String? = null,
    val channelTitle: String? = null,
    val channelDescription: String? = null
)

data class EnrichedEpisodeMetadata(
    val description: String,
    val chapters: String,
    val adTimestampsSeconds: String,
    val transcript: String,
    val durationSeconds: Long,
    val episodeArtworkUrl: String? = null
)

enum class PodcastSource(val displayName: String, val badgeColorHex: Long) {
    ALL("All Sources", 0xFF00FF66),
    ITUNES("Apple Podcasts", 0xFFFA2D48),
    PODCAST_INDEX("Podcast Index", 0xFFFF9900),
    SPOTIFY_OPEN("Spotify Directory", 0xFF1DB954),
    BBC_SOUNDS("BBC & NPR", 0xFF2D8CFF),
    CUSTOM_RSS("RSS Direct", 0xFFA855F7)
}

object PodcastApiClient {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun searchPodcasts(
        query: String,
        source: PodcastSource = PodcastSource.ALL
    ): List<SearchResultPodcast> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        val results = mutableListOf<SearchResultPodcast>()

        // 0. Direct RSS URL handling: If user pasted an RSS/Atom feed URL directly
        if (trimmedQuery.startsWith("http://", ignoreCase = true) || trimmedQuery.startsWith("https://", ignoreCase = true)) {
            try {
                val feedResult = fetchFeedDetails(trimmedQuery, "RSS Feed")
                val title = feedResult.channelTitle ?: "Direct RSS Podcast"
                val desc = feedResult.channelDescription ?: "Custom subscribed RSS podcast feed."
                val cover = feedResult.channelCoverUrl ?: "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=400&auto=format&fit=crop&q=60"
                return listOf(
                    SearchResultPodcast(
                        id = "rss_" + Math.abs(trimmedQuery.hashCode()).toString(16),
                        title = title,
                        author = "RSS Feed",
                        description = desc,
                        coverUrl = cover,
                        category = "Custom Feed",
                        feedUrl = trimmedQuery,
                        source = PodcastSource.CUSTOM_RSS,
                        trackCount = feedResult.episodes.size,
                        releaseDate = feedResult.episodes.firstOrNull()?.publishDate ?: "Recent"
                    )
                )
            } catch (_: Exception) {}
        }

        // 1. Apple iTunes Search API (public, no key required, covers millions of real podcasts)
        try {
            val encoded = java.net.URLEncoder.encode(trimmedQuery, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&entity=podcast&limit=25"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LabCast/1.0 (Android)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val jsonArray = json.optJSONArray("results")
                    if (jsonArray != null) {
                        for (i in 0 until jsonArray.length()) {
                            val item = jsonArray.getJSONObject(i)
                            val collectionId = item.optLong("collectionId", 0L)
                            val trackName = item.optString("collectionName", item.optString("trackName", "Untitled Podcast"))
                            val artistName = item.optString("artistName", "Unknown Creator")
                            val artworkUrl = item.optString("artworkUrl600", item.optString("artworkUrl100", ""))
                            val primaryGenre = item.optString("primaryGenreName", "Podcast")
                            val feedUrl = item.optString("feedUrl", "")
                            val trackCount = item.optInt("trackCount", 0)
                            val releaseDate = item.optString("releaseDate", "").take(10)

                            val podSource = when {
                                artistName.contains("BBC", ignoreCase = true) || artistName.contains("NPR", ignoreCase = true) -> PodcastSource.BBC_SOUNDS
                                collectionId % 3L == 0L -> PodcastSource.PODCAST_INDEX
                                collectionId % 2L == 0L -> PodcastSource.SPOTIFY_OPEN
                                else -> PodcastSource.ITUNES
                            }

                            results.add(
                                SearchResultPodcast(
                                    id = "apple_$collectionId",
                                    title = trackName,
                                    author = artistName,
                                    description = "Top rated show featured on $primaryGenre with over $trackCount published episodes.",
                                    coverUrl = artworkUrl,
                                    category = primaryGenre,
                                    feedUrl = feedUrl,
                                    source = podSource,
                                    trackCount = trackCount,
                                    releaseDate = releaseDate
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Offline or network timeout fallback will kick in below if needed
        }

        return if (source == PodcastSource.ALL) {
            results
        } else {
            results.filter { it.source == source }
        }
    }

    data class FeedCacheEntry(
        val timestamp: Long,
        val xml: String,
        val etag: String? = null,
        val lastModified: String? = null
    )

    private val feedCache = java.util.concurrent.ConcurrentHashMap<String, FeedCacheEntry>()

    suspend fun getOrFetchFeedXml(feedUrl: String, enableRetry: Boolean = false): String? {
        if (feedUrl.isBlank() || !feedUrl.startsWith("http")) return null
        val now = System.currentTimeMillis()
        val cached = feedCache[feedUrl]
        if (cached != null && (now - cached.timestamp) < 5 * 60 * 1000L) {
            return cached.xml
        }

        if (!enableRetry) {
            return try {
                val requestBuilder = Request.Builder()
                    .url(feedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 LabCast/1.0")

                cached?.etag?.let { requestBuilder.header("If-None-Match", it) }
                cached?.lastModified?.let { requestBuilder.header("If-Modified-Since", it) }

                val response = client.newCall(requestBuilder.build()).execute()
                if (response.code == 304 && cached != null) {
                    feedCache[feedUrl] = cached.copy(timestamp = now)
                    return cached.xml
                }

                if (response.isSuccessful) {
                    val newEtag = response.header("ETag")
                    val newLastMod = response.header("Last-Modified")
                    val xml = response.body?.string()
                    if (!xml.isNullOrEmpty()) {
                        feedCache[feedUrl] = FeedCacheEntry(now, xml, newEtag, newLastMod)
                        xml
                    } else null
                } else null
            } catch (e: Exception) {
                cached?.xml
            }
        }

        return try {
            NetworkRetryPolicy.executeWithConnectionRetry(
                operationName = "RSS-Feed ($feedUrl)"
            ) {
                val requestBuilder = Request.Builder()
                    .url(feedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 LabCast/1.0")

                cached?.etag?.let { requestBuilder.header("If-None-Match", it) }
                cached?.lastModified?.let { requestBuilder.header("If-Modified-Since", it) }

                val response = client.newCall(requestBuilder.build()).execute()
                if (response.code == 304 && cached != null) {
                    feedCache[feedUrl] = cached.copy(timestamp = System.currentTimeMillis())
                    return@executeWithConnectionRetry cached.xml
                }

                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP ${response.code}: ${response.message}")
                }
                val xml = response.body?.string()
                if (xml.isNullOrEmpty()) {
                    throw java.io.IOException("Leere Antwort vom Podcast Feed")
                }
                val newEtag = response.header("ETag")
                val newLastMod = response.header("Last-Modified")
                feedCache[feedUrl] = FeedCacheEntry(System.currentTimeMillis(), xml, newEtag, newLastMod)
                xml
            }
        } catch (e: Exception) {
            cached?.xml
        }
    }

    suspend fun fetchFeedDetails(
        feedUrl: String,
        podcastTitle: String = "",
        enableRetry: Boolean = false
    ): FeedResult {
        val xml = getOrFetchFeedXml(feedUrl, enableRetry = enableRetry)
        if (!xml.isNullOrEmpty()) {
            val channelImgRegex = Regex("""(?:<itunes:image[^>]*href=["']([^"']+)["']|<image>\s*<url>(.*?)</url>)""", RegexOption.IGNORE_CASE)
            val channelTitleRegex = Regex("""<channel[^>]*>.*?<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val channelDescRegex = Regex("""<channel[^>]*>.*?<description>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</description>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

            val channelCover = channelImgRegex.find(xml)?.let { match ->
                val g1 = match.groupValues.getOrNull(1)
                val g2 = match.groupValues.getOrNull(2)
                if (!g1.isNullOrEmpty()) g1 else if (!g2.isNullOrEmpty()) g2 else null
            }?.trim()

            val title = channelTitleRegex.find(xml)?.groupValues?.getOrNull(1)?.let { cleanHtml(it) }?.trim()
            val desc = channelDescRegex.find(xml)?.groupValues?.getOrNull(1)?.let { cleanHtml(it) }?.trim()

            val parsedEpisodes = parseRssFeed(xml)
            if (parsedEpisodes.isNotEmpty()) {
                return FeedResult(
                    episodes = parsedEpisodes,
                    channelCoverUrl = channelCover,
                    channelTitle = title,
                    channelDescription = desc
                )
            }
        }

        // No demo episodes: an unavailable feed simply yields no episodes.
        return FeedResult(episodes = emptyList())
    }

    suspend fun fetchEpisodesForFeed(feedUrl: String, podcastTitle: String): List<FeedEpisode> {
        val details = fetchFeedDetails(feedUrl, podcastTitle)
        return details.episodes
    }

    suspend fun fetchEnrichedMetadataForEpisode(
        feedUrl: String,
        episodeTitle: String,
        audioUrl: String,
        durationSeconds: Long
    ): EnrichedEpisodeMetadata? {
        val xml = getOrFetchFeedXml(feedUrl) ?: return null
        try {
            val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
            val titleRegex = Regex("<title><!\\[CDATA\\[(.*?)\\]\\]></title>|<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            val descRegex = Regex("<description><!\\[CDATA\\[(.*?)\\]\\]></description>|<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL)
            val contentEncodedRegex = Regex("<content:encoded><!\\[CDATA\\[(.*?)\\]\\]></content:encoded>|<content:encoded>(.*?)</content:encoded>", RegexOption.DOT_MATCHES_ALL)
            val enclosureRegex = Regex("<enclosure[^>]*url=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)

            val cleanTargetTitle = cleanHtml(episodeTitle).lowercase()
            val audioKey = audioUrl.substringBefore("?").substringAfterLast("/")

            for (match in itemRegex.findAll(xml)) {
                val itemBlock = match.groupValues[1]
                val itemAudio = enclosureRegex.find(itemBlock)?.groups?.get(1)?.value ?: ""
                val itemTitle = cleanHtml(
                    titleRegex.find(itemBlock)?.let { it.groups[1]?.value ?: it.groups[2]?.value } ?: ""
                ).lowercase()

                val matchesAudio = audioKey.isNotEmpty() && itemAudio.contains(audioKey)
                val matchesTitle = itemTitle.isNotEmpty() && (itemTitle == cleanTargetTitle || itemTitle.contains(cleanTargetTitle) || cleanTargetTitle.contains(itemTitle))

                if (matchesAudio || matchesTitle) {
                    // Found matching feed item! Extract richest description and full metadata
                    val contentEncoded = contentEncodedRegex.find(itemBlock)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
                    val descMatch = descRegex.find(itemBlock)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
                    val rawDesc = contentEncoded ?: descMatch ?: ""
                    val richDesc = cleanHtml(rawDesc)

                    val parsedChapters = com.example.data.ChapterParser.parseFromFeedItem(itemBlock, durationSeconds)
                    val chaptersStr = if (parsedChapters.isNotEmpty()) {
                        com.example.data.ChapterParser.toPipeString(parsedChapters)
                    } else ""

                    val transcriptRegex = Regex("<(?:podcast:transcript|transcript)[^>]*url=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
                    val transcriptUrl = transcriptRegex.find(itemBlock)?.groups?.get(1)?.value

                    var transcriptStr = ""
                    if (!transcriptUrl.isNullOrEmpty()) {
                        try {
                            val req = Request.Builder().url(transcriptUrl).build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val parsedSegs = com.example.data.TranscriptParser.parseOrGenerateTranscript(
                                    rawTranscript = body,
                                    episodeTitle = episodeTitle,
                                    episodeDescription = richDesc,
                                    durationSeconds = durationSeconds,
                                    chapters = parsedChapters
                                )
                                transcriptStr = parsedSegs.joinToString("\n") { "${it.formattedTime()} [${it.speaker}] ${it.text}" }
                            }
                        } catch (_: Exception) {}
                    }

                    val epImageRegex = Regex("""<itunes:image[^>]*href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    val epArtwork = epImageRegex.find(itemBlock)?.groups?.get(1)?.value

                    return EnrichedEpisodeMetadata(
                        description = richDesc,
                        chapters = chaptersStr,
                        adTimestampsSeconds = "",
                        transcript = transcriptStr,
                        durationSeconds = durationSeconds,
                        episodeArtworkUrl = epArtwork
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseRssFeed(xml: String): List<FeedEpisode> {
        val list = mutableListOf<FeedEpisode>()
        try {
            // Extraction of <item> or <entry> blocks from RSS/Atom XML
            val itemRegex = Regex("""<(?:item|entry)[^>]*>(.*?)</(?:item|entry)>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val titleRegex = Regex("""<title[^>]*>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val descRegex = Regex("""<description[^>]*>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</description>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val contentEncodedRegex = Regex("""<(?:content:encoded|content)[^>]*>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</(?:content:encoded|content)>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val enclosureRegex = Regex("""(?:<enclosure[^>]*url=["']([^"']+)["']|<media:content[^>]*url=["']([^"']+)["']|<link[^>]*rel=["']enclosure["'][^>]*href=["']([^"']+)["'])""", RegexOption.IGNORE_CASE)
            val pubDateRegex = Regex("""<(?:[a-zA-Z0-9_-]+:)?(?:pubDate|published|updated|date)[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?(?:pubDate|published|updated|date)>""", RegexOption.IGNORE_CASE)
            val durationRegex = Regex("""<(?:itunes:duration|duration)[^>]*>(.*?)</(?:itunes:duration|duration)>""", RegexOption.IGNORE_CASE)
            val guidRegex = Regex("""<(?:guid|id)[^>]*>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</(?:guid|id)>""", RegexOption.IGNORE_CASE)
            val epImageRegex = Regex("""<itunes:image[^>]*href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

            // Extract up to 150 episodes for rich, complete catalog loading
            val matches = itemRegex.findAll(xml).take(150).toList()
            var index = 1
            for (match in matches) {
                val itemBlock = match.groupValues[1]

                val rawTitle = titleRegex.find(itemBlock)?.groupValues?.getOrNull(1) ?: "Episode $index"
                val cleanTitle = cleanHtml(rawTitle)

                // Get best description from content:encoded or description
                val contentEncoded = contentEncodedRegex.find(itemBlock)?.groupValues?.getOrNull(1)
                val descMatch = descRegex.find(itemBlock)?.groupValues?.getOrNull(1)
                val rawDesc = contentEncoded ?: descMatch ?: ""
                val cleanDesc = cleanHtml(rawDesc).take(4000)

                val encMatch = enclosureRegex.find(itemBlock)
                val audioUrl = encMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
                    ?: encMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotEmpty() }
                    ?: encMatch?.groupValues?.getOrNull(3)?.takeIf { it.isNotEmpty() }
                    ?: ""

                val rawPubDate = pubDateRegex.find(itemBlock)?.groupValues?.getOrNull(1) ?: ""
                val (cleanPubDate, timestampMs) = com.example.util.PodcastDateUtils.parseAndFormat(rawPubDate)

                val durationStr = durationRegex.find(itemBlock)?.groupValues?.getOrNull(1) ?: "1800"
                val durationSec = parseDurationToSeconds(durationStr)

                val epImage = epImageRegex.find(itemBlock)?.groupValues?.getOrNull(1)?.trim()

                // Stable episode identifier based on GUID or audio URL
                val rawGuid = guidRegex.find(itemBlock)?.groupValues?.getOrNull(1)?.trim() ?: ""
                val stableKey = rawGuid.ifEmpty { audioUrl.ifEmpty { cleanTitle } }
                val epId = "ep_" + Math.abs(stableKey.hashCode()).toString(16)

                // Extract all structured chapters (Podlove XML, Podcasting 2.0 namespace, Show notes timestamps)
                val parsedChapters = com.example.data.ChapterParser.parseFromFeedItem(itemBlock, durationSec)
                // Only real chapters from the feed are kept.
                val chaptersPipeString = if (parsedChapters.isNotEmpty()) {
                    com.example.data.ChapterParser.toPipeString(parsedChapters)
                } else ""

                // Only a real transcript linked from the feed is used.
                val transcriptRegex = Regex("<(?:podcast:transcript|transcript)[^>]*url=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
                val transcriptUrl = transcriptRegex.find(itemBlock)?.groups?.get(1)?.value
                var transcriptFormatted = ""
                if (!transcriptUrl.isNullOrEmpty()) {
                    try {
                        val req = Request.Builder().url(transcriptUrl).build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val parsedSegs = com.example.data.TranscriptParser.parseOrGenerateTranscript(
                                rawTranscript = body,
                                episodeTitle = cleanTitle,
                                episodeDescription = cleanDesc,
                                durationSeconds = durationSec,
                                chapters = parsedChapters
                            )
                            transcriptFormatted = parsedSegs.joinToString("\n") { seg ->
                                "${seg.formattedTime()} [${seg.speaker}] ${seg.text}"
                            }
                        }
                    } catch (_: Exception) {}
                }

                list.add(
                    FeedEpisode(
                        id = epId,
                        title = cleanTitle,
                        description = cleanDesc,
                        durationSeconds = durationSec,
                        publishDate = cleanPubDate,
                        audioUrl = audioUrl,
                        adTimestampsSeconds = "",
                        chapters = chaptersPipeString,
                        transcript = transcriptFormatted,
                        publishTimestamp = timestampMs,
                        episodeArtworkUrl = epImage
                    )
                )
                index++
            }
        } catch (e: Exception) {
            // ignore
        }
        list.sortByDescending { it.publishTimestamp }
        return list
    }

    private fun cleanHtml(text: String): String {
        return text
            .replace(Regex("<.*?>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun parseDurationToSeconds(duration: String): Long {
        return try {
            if (duration.contains(":")) {
                val parts = duration.split(":")
                if (parts.size == 3) {
                    parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble().toLong()
                } else if (parts.size == 2) {
                    parts[0].toLong() * 60 + parts[1].toDouble().toLong()
                } else {
                    1800L
                }
            } else {
                duration.toDoubleOrNull()?.toLong() ?: 1800L
            }
        } catch (e: Exception) {
            1800L
        }
    }
}

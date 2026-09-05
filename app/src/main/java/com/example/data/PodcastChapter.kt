package com.example.data

data class PodcastChapter(
    val id: String = "",
    val title: String,
    val startTimeSeconds: Long,
    val durationSeconds: Long? = null
) {
    fun formattedStartTime(): String {
        val hours = startTimeSeconds / 3600
        val minutes = (startTimeSeconds % 3600) / 60
        val seconds = startTimeSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formattedDuration(): String {
        val dur = durationSeconds ?: return ""
        val minutes = dur / 60
        val seconds = dur % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
}

object ChapterParser {

    /**
     * Parses chapters either from explicit format ("0:Intro|120:Topic 1|480:Topic 2")
     * or by extracting timestamps from episode description.
     */
    fun parseChapters(
        rawChapters: String?,
        description: String?,
        totalDurationSeconds: Long
    ): List<PodcastChapter> {
        // 1. Explicit pipe-separated chapters
        if (!rawChapters.isNullOrBlank()) {
            val list = mutableListOf<PodcastChapter>()
            val parts = rawChapters.split("|")
            for ((idx, part) in parts.withIndex()) {
                val sub = part.split(":", limit = 2)
                if (sub.size == 2) {
                    val sec = sub[0].trim().toLongOrNull()
                    val title = sub[1].trim()
                    if (sec != null && title.isNotEmpty()) {
                        list.add(
                            PodcastChapter(
                                id = "ch_$idx",
                                title = title,
                                startTimeSeconds = sec
                            )
                        )
                    }
                }
            }
            if (list.isNotEmpty()) {
                return calculateDurations(list.sortedBy { it.startTimeSeconds }, totalDurationSeconds)
            }
        }

        // 2. Parse from description if available
        if (!description.isNullOrBlank()) {
            val list = mutableListOf<PodcastChapter>()
            val regex = Regex("""(?m)^\s*[\(\[]?(\d{1,2}:\d{2}(?::\d{2})?)[\)\]]?\s*[-–—:]?\s*(.+)$""")
            val matches = regex.findAll(description).toList()
            for ((idx, match) in matches.withIndex()) {
                val ts = match.groups[1]?.value ?: continue
                val title = match.groups[2]?.value?.trim() ?: continue
                val sec = parseTimestamp(ts)
                if (sec != null && title.isNotEmpty()) {
                    list.add(
                        PodcastChapter(
                            id = "ch_desc_$idx",
                            title = title,
                            startTimeSeconds = sec
                        )
                    )
                }
            }
            if (list.size >= 2) {
                return calculateDurations(list.sortedBy { it.startTimeSeconds }, totalDurationSeconds)
            }
        }

        return emptyList()
    }

    private fun parseTimestamp(ts: String): Long? {
        val parts = ts.split(":")
        return when (parts.size) {
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

    private fun calculateDurations(
        chapters: List<PodcastChapter>,
        totalDurationSeconds: Long
    ): List<PodcastChapter> {
        return chapters.mapIndexed { index, chapter ->
            val nextStart = if (index + 1 < chapters.size) {
                chapters[index + 1].startTimeSeconds
            } else {
                totalDurationSeconds.coerceAtLeast(chapter.startTimeSeconds)
            }
            val dur = (nextStart - chapter.startTimeSeconds).coerceAtLeast(0)
            chapter.copy(durationSeconds = dur)
        }
    }
}

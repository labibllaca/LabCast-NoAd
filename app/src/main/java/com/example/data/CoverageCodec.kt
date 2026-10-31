package com.example.data

/** Serializes covered sample ranges for `EpisodeScanEntity.coveredRanges`. */
object CoverageCodec {

    fun encode(ranges: List<LongRange>): String {
        return ranges
            .sortedBy { it.first }
            .joinToString(",") { "${it.first}-${it.last}" }
    }

    fun decode(raw: String): List<LongRange> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { part ->
                val pieces = part.trim().split("-", limit = 2)
                val start = pieces.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val end = pieces.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                if (end < start) null else start..end
            }
            .sortedBy { it.first }
    }
}

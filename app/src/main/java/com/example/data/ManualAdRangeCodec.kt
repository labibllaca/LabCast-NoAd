package com.example.data

data class ManualAdRange(val startMs: Long, val endMs: Long)

object ManualAdRangeCodec {
    fun preserveExisting(existing: String, refreshed: String): String =
        if (existing.isNotBlank()) existing else refreshed

    fun encode(ranges: List<ManualAdRange>): String = ranges
        .filter { it.startMs >= 0L && it.endMs > it.startMs }
        .distinct()
        .joinToString(";") { "${it.startMs}-${it.endMs}" }

    fun decode(encoded: String): List<ManualAdRange> = encoded
        .split(';')
        .mapNotNull { item ->
            val pair = item.trim().split('-', limit = 2)
            if (pair.size != 2) return@mapNotNull null

            val startMs = pair[0].toLongOrNull() ?: return@mapNotNull null
            val endMs = pair[1].toLongOrNull() ?: return@mapNotNull null
            if (startMs < 0L || endMs <= startMs) return@mapNotNull null

            ManualAdRange(startMs, endMs)
        }
        .distinct()

    fun clamp(range: ManualAdRange, durationMs: Long): ManualAdRange? {
        if (durationMs <= 0L || range.startMs < 0L || range.endMs <= range.startMs) return null

        val clamped = ManualAdRange(
            startMs = range.startMs.coerceAtMost(durationMs),
            endMs = range.endMs.coerceAtMost(durationMs)
        )
        return clamped.takeIf { it.endMs > it.startMs }
    }
}

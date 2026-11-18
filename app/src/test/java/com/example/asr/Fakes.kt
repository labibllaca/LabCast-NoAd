package com.example.asr

import com.example.data.AdScanStore
import com.example.data.AdSlotEntity
import com.example.data.CoverageCodec
import com.example.data.EpisodeAdSegmentEntity
import com.example.data.EpisodeAsrSegmentEntity
import com.example.data.EpisodeScanEntity
import com.example.data.EpisodeEntity
import kotlin.math.min

class FakeStore : AdScanStore {
    private val scans = mutableMapOf<String, EpisodeScanEntity>()
    private val slotsByPodcast = mutableMapOf<String, List<AdSlotEntity>>()
    private val asrByEpisode = mutableMapOf<String, MutableList<EpisodeAsrSegmentEntity>>()
    private val adsByEpisode = mutableMapOf<String, List<EpisodeAdSegmentEntity>>()
    var transcript: String? = null
    var savedDurationSeconds: Long? = null

    override suspend fun scan(episodeId: String): EpisodeScanEntity? = scans[episodeId]

    override suspend fun saveScan(scan: EpisodeScanEntity) {
        scans[scan.episodeId] = scan
    }

    override suspend fun slots(podcastId: String): List<AdSlotEntity> =
        slotsByPodcast[podcastId].orEmpty()

    override suspend fun saveSlots(podcastId: String, slots: List<AdSlotEntity>) {
        slotsByPodcast[podcastId] = slots
    }

    override suspend fun asrSegments(episodeId: String): List<EpisodeAsrSegmentEntity> =
        asrByEpisode[episodeId].orEmpty()

    override suspend fun upsertAsrSegments(segments: List<EpisodeAsrSegmentEntity>) {
        if (segments.isEmpty()) return
        val target = asrByEpisode.getOrPut(segments.first().episodeId) { mutableListOf() }
        segments.forEach { incoming ->
            val index = target.indexOfFirst { it.id == incoming.id }
            if (index >= 0) target[index] = incoming else target += incoming
        }
    }

    override suspend fun adSegments(episodeId: String): List<EpisodeAdSegmentEntity> =
        adsByEpisode[episodeId].orEmpty()

    override suspend fun adSegment(id: String): EpisodeAdSegmentEntity? =
        adsByEpisode.values.flatten().firstOrNull { it.id == id }

    override suspend fun deleteAdSegment(id: String) {
        adsByEpisode.keys.toList().forEach { episodeId ->
            adsByEpisode[episodeId] = adsByEpisode[episodeId].orEmpty().filterNot { it.id == id }
        }
    }

    override suspend fun replaceAdSegments(
        episodeId: String,
        segments: List<EpisodeAdSegmentEntity>,
    ) {
        adsByEpisode[episodeId] = segments
    }

    override suspend fun saveTranscriptProjection(episodeId: String, text: String) {
        transcript = text
    }

    override suspend fun saveEpisodeDuration(episodeId: String, seconds: Long) {
        savedDurationSeconds = seconds
    }

    fun scanRecords(): List<EpisodeScanEntity> = scans.values.toList()
    fun ads(episodeId: String): List<EpisodeAdSegmentEntity> = adsByEpisode[episodeId].orEmpty()
    fun asr(episodeId: String): List<EpisodeAsrSegmentEntity> = asrByEpisode[episodeId].orEmpty()
    fun covered(episodeId: String): List<LongRange> =
        CoverageCodec.decode(scans[episodeId]?.coveredRanges.orEmpty())
}

class FakePcmSource(override val durationMs: Long) : PcmSource {
    var totalReadMs = 0L
    val readRanges = mutableListOf<LongRange>()

    override fun reader(startMs: Long): PcmReader {
        readRanges += startMs..durationMs
        return FakeReader(startMs)
    }

    private inner class FakeReader(private var positionMs: Long) : PcmReader {
        override fun read(out: FloatArray): Int {
            if (positionMs >= durationMs) return 0
            val remainingSamples = ((durationMs - positionMs) * 16).toInt()
            val count = min(out.size, remainingSamples)
            if (count <= 0) return 0
            java.util.Arrays.fill(out, 0, count, 0f)
            positionMs += count * 1000L / 16_000L
            totalReadMs += count * 1000L / 16_000L
            return count
        }

        override fun close() = Unit
    }
}

class FakeEngine(private val script: Map<Long, String>) : SpeechRecognizerEngine {
    override fun startStream(startMs: Long): SpeechStream = FakeStream(startMs)
    override fun close() = Unit

    private inner class FakeStream(private val baseMs: Long) : SpeechStream {
        private var fedMs = 0L
        private val emitted = mutableSetOf<Long>()
        private val pending = mutableListOf<AsrSegment>()

        override fun accept(samples: FloatArray) {
            fedMs += samples.size * 1000L / 16_000L
            script.forEach { (time, text) ->
                if (time >= baseMs && time <= baseMs + fedMs && emitted.add(time)) {
                    pending += AsrSegment(time, time + 3_000L, text, 0.95f)
                }
            }
        }

        override fun pollSegments(): List<AsrSegment> {
            val out = pending.toList()
            pending.clear()
            return out
        }

        override fun finish(): List<AsrSegment> = pollSegments()
        override fun close() = Unit
    }
}

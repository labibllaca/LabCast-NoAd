package com.example.asr

import com.example.data.AdScanStore
import com.example.data.AdSlotEntity
import com.example.data.CoverageCodec
import com.example.data.EpisodeAdSegmentEntity
import com.example.data.EpisodeAsrSegmentEntity
import com.example.data.EpisodeEntity
import com.example.data.EpisodeScanEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

data class ScanState(
    val episodeId: String,
    val mode: ScanMode,
    val status: ScanStatus,
    val coveredMs: Long,
    val totalMs: Long,
    val aheadOfPlayheadMs: Long,
    val adsFound: Int,
    val error: String? = null,
)

/**
 * Owns the background transcription of one episode.
 *
 * The controller plans ranges, decodes audio, feeds the recognizer, persists
 * segments and ads, and learns show slots. It never touches playback; the
 * ViewModel reads [currentAds] and decides whether to seek.
 */
class AdScanController(
    private val store: AdScanStore,
    private val engine: SpeechRecognizerEngine,
    private val pcmSources: PcmSourceFactory,
    private val cueDetector: AdCueDetector,
    private val slotLearner: AdSlotLearner,
    private val predictor: AdSlotPredictor,
    private val planner: ScanPlanner,
    private val scope: CoroutineScope,
    private val throttle: suspend () -> Unit = { delay(250L) },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val isEnabled: () -> Boolean = { true },
    private val isModelReady: () -> Boolean = { true },
    private val bootstrap: (EpisodeEntity) -> List<DetectedAd> = { emptyList() },
    private val logger: (String) -> Unit = { android.util.Log.i("AdScan", it) },
) {

    private val _state = MutableStateFlow(
        ScanState("", ScanMode.BOOTSTRAP_FULL, ScanStatus.IDLE, 0, 0, 0, 0)
    )
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _currentAds = MutableStateFlow<List<EpisodeAdSegmentEntity>>(emptyList())
    val currentAds: StateFlow<List<EpisodeAdSegmentEntity>> = _currentAds.asStateFlow()

    private var job: Job? = null
    private var partialSave: Job? = null
    private var currentEpisode: EpisodeEntity? = null
    private var playheadMs = 0L
    private var playing = false
    private var paused = false
    private var active = false
    private var forceFull = false
    private var mode = ScanMode.BOOTSTRAP_FULL
    private var durationMs = 0L
    private var covered = mutableListOf<LongRange>()
    private var slots: List<AdSlotEntity> = emptyList()
    private var ads: List<EpisodeAdSegmentEntity> = emptyList()
    private var asrSegments: MutableList<EpisodeAsrSegmentEntity> = mutableListOf()
    private var pcmSource: PcmSource? = null

    fun start(episode: EpisodeEntity, playheadMs: Long) {
        if (!isEnabled() || !isModelReady()) {
            _state.value = ScanState(
                episode.id, mode, ScanStatus.IDLE, 0, 0, 0, 0,
                error = "ASR disabled or model missing",
            )
            return
        }

        cancelCurrent()
        currentEpisode = episode
        this.playheadMs = playheadMs
        playing = false
        paused = false
        active = true
        _state.value = ScanState(
            episodeId = episode.id,
            mode = mode,
            status = ScanStatus.SCANNING,
            coveredMs = 0,
            totalMs = durationMs,
            aheadOfPlayheadMs = 0,
            adsFound = 0,
        )
        _currentAds.value = emptyList()
        job = scope.launch(dispatcher) { runScan(episode) }
    }

    fun updatePlayhead(positionMs: Long, isPlaying: Boolean) {
        playheadMs = positionMs
        playing = isPlaying
        if (isPlaying) paused = false
    }

    fun seek(positionMs: Long) {
        playheadMs = positionMs
        val ep = currentEpisode ?: return
        if (active) {
            active = false
            job?.cancel()
            start(ep, positionMs)
        }
    }

    fun pause() {
        paused = true
    }

    fun stop() {
        if (!active) return
        active = false
        paused = false
        job?.cancel()
        val ep = currentEpisode ?: return
        partialSave = scope.launch(NonCancellable) {
            saveScan(ep, ScanStatus.PARTIAL)
        }
    }

    fun relearnShow(podcastId: String) {
        scope.launch(dispatcher) {
            val existing = store.slots(podcastId)
            store.saveSlots(podcastId, slotLearner.invalidateAll(existing))
            val ep = currentEpisode
            if (ep != null && ep.podcastId == podcastId) {
                forceFull = true
                start(ep, playheadMs)
            }
        }
    }

    fun onFeedMetadataUpdated(episode: EpisodeEntity) {
        scope.launch(dispatcher) {
            val existingScan = store.scan(episode.id)
            if (existingScan?.status == ScanStatus.COMPLETE.name) return@launch
            val candidates = bootstrap(episode)
            if (candidates.isEmpty()) return@launch
            val existingSlots = store.slots(episode.podcastId)
            if (existingSlots.isEmpty()) {
                slots = candidates.mapIndexed { index, ad -> candidateSlot(episode.podcastId, index, ad) }
                store.saveSlots(episode.podcastId, slots)
            }
            start(episode, playheadMs)
        }
    }

    suspend fun awaitIdle() {
        job?.join()
        partialSave?.join()
    }

    private suspend fun runScan(episode: EpisodeEntity) {
        try {
            val existingScan = store.scan(episode.id)
            covered = CoverageCodec.decode(existingScan?.coveredRanges.orEmpty()).toMutableList()
            slots = store.slots(episode.podcastId)
            asrSegments = store.asrSegments(episode.id).toMutableList()
            ads = store.adSegments(episode.id)
            _currentAds.value = ads

            val source = pcmSources.create(episode)
            pcmSource = source
            val decodedDuration = source.durationMs
            durationMs = when {
                decodedDuration > 0L -> decodedDuration
                episode.durationSeconds > 0L -> episode.durationSeconds * 1000L
                else -> 0L
            }
            if (decodedDuration > 0L &&
                abs(decodedDuration - episode.durationSeconds * 1000L) > 5_000L
            ) {
                store.saveEpisodeDuration(episode.id, decodedDuration / 1000L)
            }

            if (slots.isEmpty()) {
                val candidates = bootstrap(episode)
                if (candidates.isNotEmpty()) {
                    slots = candidates.mapIndexed { index, ad ->
                        candidateSlot(episode.podcastId, index, ad)
                    }
                    store.saveSlots(episode.podcastId, slots)
                }
            }

            mode = when {
                forceFull -> ScanMode.FULL_MANUAL
                slots.any { it.enabled } -> ScanMode.WINDOWED
                else -> ScanMode.BOOTSTRAP_FULL
            }
            forceFull = false

            val windows = predictor.predict(slots, durationMs, includeCandidates = true)
            val planned = planner.plan(mode, durationMs, playheadMs, windows, covered)
            val ranges = mutableListOf<ScanRange>()
            if (playheadMs > 0L && durationMs > 0L) {
                ranges += ScanRange(
                    startMs = playheadMs.coerceAtMost(durationMs),
                    endMs = min(playheadMs + LOOK_AHEAD_MS, durationMs),
                    priority = 0,
                )
            }
            ranges += planned
            val merged = ranges
                .filter { it.endMs > it.startMs }
                .filter { range ->
                    covered.none { c -> c.first <= range.startMs && c.last >= range.endMs }
                }
                .sortedWith(compareBy({ it.priority }, { it.startMs }))

            _state.value = ScanState(
                episodeId = episode.id,
                mode = mode,
                status = ScanStatus.SCANNING,
                coveredMs = coveredMs(),
                totalMs = durationMs,
                aheadOfPlayheadMs = aheadOfPlayheadMs(),
                adsFound = ads.size,
            )
            saveScan(episode, ScanStatus.SCANNING)
            logger("run planned mode=$mode durationMs=$durationMs slots=${slots.size} ranges=${merged.size} covered=${covered.size}")

            merged.forEach { range ->
                if (!active) return@forEach
                scanRange(episode, range)
            }

            val status = if (ads.isEmpty() && slots.isEmpty()) {
                ScanStatus.PARTIAL
            } else {
                ScanStatus.COMPLETE
            }
            saveScan(episode, status)
            _state.value = _state.value.copy(
                status = status,
                coveredMs = coveredMs(),
                totalMs = durationMs,
                aheadOfPlayheadMs = aheadOfPlayheadMs(),
                adsFound = ads.size,
            )
            logger("mode=$mode episode=${episode.id} coveredMs=${coveredMs()} ads=${ads.size}")
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            _state.value = _state.value.copy(status = ScanStatus.FAILED, error = e.message)
            logger("scan failed episode=${episode.id}: ${e::class.java.simpleName}: ${e.message}")
            android.util.Log.e("AdScan", "scan failed", e)
        }
    }

    private suspend fun scanRange(episode: EpisodeEntity, range: ScanRange) {
        val source = pcmSource ?: return
        val reader = source.reader(range.startMs)
        val stream = engine.startStream(range.startMs)
        val buffer = FloatArray(CHUNK_SAMPLES)
        var readMs = 0L

        try {
            while (active) {
                while (paused && active) throttle()
                if (!active) break

                val count = reader.read(buffer)
                if (count <= 0) break
                val maxSamples = ((range.endMs - (range.startMs + readMs)) * SAMPLE_RATE / 1000L).toInt()
                if (maxSamples <= 0) break
                val usable = min(count, maxSamples)
                stream.accept(if (usable == buffer.size) buffer else buffer.copyOf(usable))
                readMs += usable * 1000L / SAMPLE_RATE

                val segments = stream.pollSegments()
                if (segments.isNotEmpty()) {
                    appendSegments(episode, segments)
                }

                if (mode != ScanMode.WINDOWED &&
                    range.startMs + readMs > playheadMs + LOOK_AHEAD_MS
                ) {
                    while (active && range.startMs + readMs > playheadMs + LOOK_AHEAD_MS) {
                        throttle()
                    }
                }
            }

            val tail = stream.finish()
            if (tail.isNotEmpty()) appendSegments(episode, tail)
        } finally {
            stream.close()
            reader.close()
        }

        addCovered(range.startMs..range.endMs)
        saveScan(episode, ScanStatus.SCANNING)
        refreshAds(episode)
        learnSlots(episode)
        saveTranscriptProjection(episode)
    }

    private suspend fun appendSegments(episode: EpisodeEntity, segments: List<AsrSegment>) {
        val entities = segments.map { segment ->
            EpisodeAsrSegmentEntity(
                id = "${episode.id}:${segment.startMs}",
                episodeId = episode.id,
                startMs = segment.startMs,
                endMs = segment.endMs,
                text = segment.text,
                confidence = segment.confidence,
                source = "ASR",
                createdAt = System.currentTimeMillis(),
            )
        }
        asrSegments += entities.filter { new -> asrSegments.none { it.id == new.id } }
        store.upsertAsrSegments(entities)
    }

    private suspend fun refreshAds(episode: EpisodeEntity) {
        val detected = cueDetector.adsFromSegments(
            asrSegments.sortedBy { it.startMs }.map {
                AsrSegment(it.startMs, it.endMs, it.text, it.confidence)
            }
        ).map { ad ->
            EpisodeAdSegmentEntity(
                id = "${episode.id}:${ad.startMs}:${ad.endMs}",
                episodeId = episode.id,
                podcastId = episode.podcastId,
                startMs = ad.startMs,
                endMs = ad.endMs,
                label = ad.label,
                confidence = ad.confidence,
                source = ad.source.name,
                confirmed = ad.confirmed,
                createdAt = System.currentTimeMillis(),
            )
        }
        ads = mergeAds(ads, detected)
        _currentAds.value = ads
        store.replaceAdSegments(episode.id, ads)
    }

    private fun mergeAds(
        existing: List<EpisodeAdSegmentEntity>,
        detected: List<EpisodeAdSegmentEntity>,
    ): List<EpisodeAdSegmentEntity> {
        val merged = existing.toMutableList()
        detected.forEach { incoming ->
            val index = merged.indexOfFirst { it.startMs < incoming.endMs && incoming.startMs < it.endMs }
            if (index < 0) {
                merged += incoming
            } else {
                val current = merged[index]
                merged[index] = when {
                    incoming.confirmed && !current.confirmed -> incoming
                    current.confirmed && !incoming.confirmed -> current
                    incoming.confirmed && current.confirmed &&
                        incoming.endMs > current.endMs -> incoming
                    else -> current
                }
            }
        }
        return merged.sortedBy { it.startMs }
    }

    private suspend fun learnSlots(episode: EpisodeEntity) {
        val confirmed = ads.filter { it.confirmed }.map { ad ->
            DetectedAd(
                startMs = ad.startMs,
                endMs = ad.endMs,
                label = ad.label,
                confidence = ad.confidence,
                startCue = null,
                endCue = null,
                confirmed = true,
                source = runCatching { AdSource.valueOf(ad.source) }.getOrDefault(AdSource.ASR),
            )
        }
        if (confirmed.isEmpty()) return
        slots = slotLearner.learn(slots, episode.podcastId, durationMs, confirmed)
        store.saveSlots(episode.podcastId, slots)
    }

    private suspend fun saveTranscriptProjection(episode: EpisodeEntity) {
        if (asrSegments.isEmpty()) return
        val text = asrSegments
            .sortedBy { it.startMs }
            .joinToString("\n") { "${formatTime(it.startMs)} [${it.source}] ${it.text}" }
        store.saveTranscriptProjection(episode.id, text)
    }

    private suspend fun saveScan(episode: EpisodeEntity, status: ScanStatus) {
        store.saveScan(
            EpisodeScanEntity(
                episodeId = episode.id,
                mode = mode.name,
                status = status.name,
                coveredRanges = CoverageCodec.encode(covered),
                modelVersion = MODEL_VERSION,
                actualDurationMs = durationMs,
                durationSource = if (durationMs > 0L) "DECODED" else "FEED",
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun cancelCurrent() {
        active = false
        paused = false
        job?.cancel()
        job = null
        partialSave?.cancel()
        partialSave = null
    }

    private fun candidateSlot(
        podcastId: String,
        index: Int,
        ad: DetectedAd,
    ): AdSlotEntity {
        val ratio = if (durationMs > 0L) ad.startMs.toFloat() / durationMs else 0f
        return AdSlotEntity(
            podcastId = podcastId,
            slotIndex = index,
            startRatio = ratio,
            startMsAnchor = ad.startMs,
            durationMs = (ad.endMs - ad.startMs).coerceAtLeast(1L),
            startVarianceMs = 0L,
            hits = 0,
            misses = 0,
            confidence = 0.5f,
            source = ad.source.name,
            enabled = true,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun addCovered(range: LongRange) {
        if (range.last <= range.first) return
        covered += range
        covered = covered
            .sortedBy { it.first }
            .fold(mutableListOf<LongRange>()) { acc, r ->
                val last = acc.lastOrNull()
                if (last == null || r.first > last.last + 1) {
                    acc += r
                } else {
                    acc[acc.lastIndex] = last.first..maxOf(last.last, r.last)
                }
                acc
            }
    }

    private fun coveredMs(): Long = covered.sumOf { (it.last - it.first).coerceAtLeast(0L) }

    private fun aheadOfPlayheadMs(): Long {
        val last = covered.maxOfOrNull { it.last } ?: return 0L
        return (last - playheadMs).coerceAtLeast(0L)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000L
        return String.format(
            Locale.ROOT,
            "%02d:%02d",
            totalSeconds / 60L,
            totalSeconds % 60L,
        )
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 1_600
        const val LOOK_AHEAD_MS = 180_000L
        const val MODEL_VERSION = "zipformer-en-20m-2023-02-17"
    }
}

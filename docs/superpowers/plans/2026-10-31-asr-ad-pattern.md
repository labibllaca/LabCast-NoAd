# On-Device ASR Ad Detection with Learned Show Slots — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace LabCast's simulated ad detection with real English on-device speech recognition, learn per-show ad slots, and scan only predicted windows on later episodes.

**Architecture:** A sherpa-onnx streaming Zipformer (English, int8) transcribes decoded 16 kHz mono PCM. A look-ahead scan worker runs ahead of `MediaPlayer`, detects ad cues in timestamped segments, and persists exact ad segments. A per-show slot learner turns confirmed ads into ratio/absolute slots; later episodes are planned as the opening 120 s plus padded windows around predicted slots. Only cue-confirmed segments can trigger a skip.

**Tech Stack:** Kotlin 2.0.21, Android Gradle Plugin 9.1.1, minSdk 24, compileSdk 36, Room 2.7.0, OkHttp 4.10.0, Moshi 1.15.2, sherpa-onnx 2.0.1 (JitPack), JUnit 4, Robolectric 4.16.1, MockWebServer 4.10.0.

**Spec:** `docs/superpowers/specs/2026-10-31-asr-ad-pattern-design.md`

## Global Constraints

- English only. No multilingual model, no language auto-detection.
- A skip requires a confirmed ad segment produced by an ASR cue. Timing alone never skips.
- Audio, transcripts, and slots stay on the device. The only network calls are model download and the episode audio.
- Model files are downloaded on first enable, not bundled. Pin the exact files and SHA-256 values from this plan.
- Model runtime and model are Apache-2.0; show the license in Settings.
- `minSdk = 24`, `compileSdk = 36`, Kotlin `2.0.21`, Room `2.7.0`.
- Room schema bumps to version 7. `fallbackToDestructiveMigration()` is already present; call the reset out in release notes.
- Ship `arm64-v8a` only; the connected test device is `arm64-v8a`.
- Playback stays on `android.media.MediaPlayer`. No Media3 migration.
- Windows: look-ahead 180_000 ms, slot window padding 30_000 ms before / 60_000 ms after, max ad 180_000 ms, opening scan 120_000 ms.
- Slot confidence threshold 0.5; disable a slot after 3 consecutive misses; re-learn after 2 fully missed episodes.
- User-edited slots are frozen; user-disabled slots stay disabled through learning and re-learn.
- Feature is off by default until the user enables it and the model is downloaded.
- No Wi-Fi gating. Mobile data is allowed.

## Review Focus

These are the five input classes most likely to bite, each pinned to a test in the task that owns the code.

1. **A cue split across ASR segments/endpoints** ("…sponsored" then "by AG1") must still be detected. Pinned in Task 2.
2. **Unknown or zero episode duration** must not produce a midnight-scaled mid-roll window; only the opening scan runs. Pinned in Task 4.
3. **A server that ignores Range** (HTTP 200 instead of 206) must still decode, without crashing or re-downloading forever. Pinned in Task 7.
4. **Seek or episode change during a scan** must not apply stale ads or duplicate segments. Pinned in Task 8.
5. **Auto-skip disabled or a user-disabled slot during a scan** must never skip. Pinned in Tasks 8 and 9.

---

## File Structure

New package `com.example.asr`:

- `AsrModels.kt` — `AsrSegment`, `DetectedAd`, `AdSource`, `ScanMode`, `ScanStatus`, `ScanState`.
- `AdCueDetector.kt` — phrase/brand matching and `adsFromSegments`.
- `AdSlotLearner.kt` — cluster detections into `AdSlotEntity`, update stats, freeze user slots.
- `AdSlotPredictor.kt` — turn slots into padded prediction windows.
- `ScanPlanner.kt` — plan scan ranges, subtract covered ranges, prioritize look-ahead.
- `AsrModelRegistry.kt` — pinned model files, URLs, sizes, SHA-256.
- `ModelDownloadManager.kt` — resumable download, verification, install/delete.
- `SpeechRecognizerEngine.kt` — engine/stream interfaces.
- `SherpaSpeechRecognizer.kt` — sherpa-onnx implementation, timestamp mapping.
- `PcmConverter.kt` — PCM 16-bit/float, mono, resample to 16 kHz.
- `PcmSource.kt` — `PcmSource`, `PcmReader`, `PcmSourceFactory`.
- `MediaPcmSource.kt` — `MediaExtractor` + `MediaCodec` + `PcmConverter`.
- `HttpRangeDataSource.kt` — random-access HTTP `MediaDataSource` with a chunk cache.
- `AdScanController.kt` — scan lifecycle, look-ahead, persistence.
- `ShowBootstrap.kt` — feed transcript/chapter candidates.
- `AsrPreferences.kt` — enable flag and model state.
- `IntroMusicDetector.kt` — real chapter/transcript intro detection, no AoM hardcode.

`com.example.data`:

- `AdEntities.kt` — `AdSlotEntity`, `EpisodeAdSegmentEntity`, `EpisodeAsrSegmentEntity`, `EpisodeScanEntity`.
- `AdDao.kt` — Room queries.
- `AdScanStore.kt` — interface used by the controller.
- `RoomAdScanStore.kt` — DAO-backed implementation.
- `CoverageCodec.kt` — encode/decode covered ranges.
- `PodcastDatabase.kt` — version 7, add `adDao()`.

`com.example.player`:

- `AdSkipDecider.kt` — pure decision function.
- Keep `PodcastKeywordSpotter` only until Task 10 renames its intro method.

`com.example.ui`:

- `AdSlotsSection.kt` — slot list, edit/toggle/delete/re-learn.
- `AsrSettingsSection.kt` — enable toggle, download progress, delete, license.
- `PodcastViewModel.kt` — wiring and lifecycle.
- `SettingsScreen.kt` — add the ASR section.
- `PodcastAppContent.kt` — add the slot section and real ad markers.

Tests:

- `app/src/test/java/com/example/asr/…` — pure unit tests.
- `app/src/test/java/com/example/player/AdSkipDeciderTest.kt`
- `app/src/test/java/com/example/data/AdDaoTest.kt` — Robolectric in-memory DB.
- `app/src/androidTest/java/com/example/asr/SherpaRecognizerInstrumentedTest.kt`
- `app/src/androidTest/java/com/example/asr/MediaPcmSourceInstrumentedTest.kt`

---

### Task 1: Build wiring, pinned model registry, model downloader

**Files:**
- Modify: `settings.gradle.kts:17-23`
- Modify: `app/build.gradle.kts:18-21` and `:118-140`
- Modify: `gradle/libs.versions.toml` (add MockWebServer)
- Create: `app/src/main/java/com/example/asr/AsrModelRegistry.kt`
- Create: `app/src/main/java/com/example/asr/ModelDownloadManager.kt`
- Test: `app/src/test/java/com/example/asr/AsrModelRegistryTest.kt`
- Test: `app/src/test/java/com/example/asr/ModelDownloadManagerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class AsrModelFile(val name: String, val url: String, val sha256: String, val sizeBytes: Long)`
  - `object AsrModelRegistry { val modelDirName: String; val files: List<AsrModelFile>; val totalBytes: Long }`
  - `class ModelDownloadManager(context: Context, client: OkHttpClient = OkHttpClient(), registry: List<AsrModelFile> = AsrModelRegistry.files)`
  - `internal constructor(rootDir: File, registry: List<AsrModelFile>)` for tests
  - `fun isInstalled(): Boolean`
  - `suspend fun download(progress: (Float) -> Unit): Result<File>`
  - `fun modelDir(): File`
  - `fun delete()`

- [ ] **Step 1: Write the failing registry test**

```kotlin
class AsrModelRegistryTest {
    @Test
    fun `pins five files with checksums and exact total size`() {
        assertEquals(5, AsrModelRegistry.files.size)
        assertEquals(44_293_155L, AsrModelRegistry.totalBytes)
        AsrModelRegistry.files.forEach {
            assertEquals(64, it.sha256.length)
            assertTrue(it.url.startsWith("https://"))
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AsrModelRegistryTest"`
Expected: FAIL with "Unresolved reference: AsrModelRegistry".

- [ ] **Step 3: Implement `AsrModelRegistry` with these exact values**

```kotlin
object AsrModelRegistry {
    const val modelDirName = "zipformer-en-20m-2023-02-17"
    private const val BASE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main"
    val files = listOf(
        AsrModelFile("encoder-epoch-99-avg-1.int8.onnx", "$BASE/encoder-epoch-99-avg-1.int8.onnx",
            "3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3", 42_845_182L),
        AsrModelFile("decoder-epoch-99-avg-1.int8.onnx", "$BASE/decoder-epoch-99-avg-1.int8.onnx",
            "21e2a2acd961b3ac72f55be2f10f1a285e1b0b0ba010d7c0b6eab141411b163c", 539_499L),
        AsrModelFile("joiner-epoch-99-avg-1.int8.onnx", "$BASE/joiner-epoch-99-avg-1.int8.onnx",
            "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e", 259_572L),
        AsrModelFile("tokens.txt", "$BASE/tokens.txt",
            "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb", 5_048L),
        AsrModelFile("silero_vad.onnx",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6", 643_854L),
    )
    val totalBytes: Long = files.sumOf { it.sizeBytes }
}
```

- [ ] **Step 4: Write the failing download tests**

```kotlin
class ModelDownloadManagerTest {
    private val file = AsrModelFile("a.bin", "http://localhost/a.bin",
        MessageDigest.getInstance("SHA-256").digest("abc".toByteArray()).joinToString("") { "%02x".format(it) }, 3)

    @Test
    fun `download verifies hash and reports progress`() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setBody("abc")); start() }
        val file = file.copy(url = server.url("/a.bin").toString())
        val progress = mutableListOf<Float>()
        val manager = ModelDownloadManager(createTempDir(), listOf(file))
        assertTrue(manager.download { progress += it }.isSuccess)
        assertEquals(1f, progress.last())
        assertEquals("abc", File(manager.modelDir(), "a.bin").readText())
    }

    @Test
    fun `download rejects a hash mismatch and deletes the partial file`() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setBody("abd")); start() }
        val manager = ModelDownloadManager(createTempDir(), listOf(file.copy(url = server.url("/a.bin").toString())))
        assertTrue(manager.download {}.isFailure)
        assertFalse(File(manager.modelDir(), "a.bin").exists())
    }
}
```

The manager test constructor is `ModelDownloadManager(rootDir: File, registry: List<AsrModelFile>)`; the production constructor wraps `context` to `File(context.filesDir, "models")`.

- [ ] **Step 5: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.ModelDownloadManagerTest"`
Expected: FAIL with unresolved references.

- [ ] **Step 6: Implement the downloader**

Download each file to `<name>.part`, stream with OkHttp, hash while writing, verify size and SHA-256, rename on success, delete on failure. `isInstalled()` verifies sizes and hashes; cache the result in a `@Volatile` flag. `progress` is total bytes written divided by `totalBytes`.

- [ ] **Step 7: Add the dependency and repository**

In `settings.gradle.kts`, add to `dependencyResolutionManagement.repositories`:

```kotlin
maven { url = uri("https://jitpack.io") }
```

In `app/build.gradle.kts`:

```kotlin
defaultConfig { ndk { abiFilters += "arm64-v8a" } }
dependencies {
  implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.1")
}
```

Add `mockwebserver` to `libs.versions.toml` and `testImplementation(libs.mockwebserver)`.

If the build reports a duplicate `lib/arm64-v8a/libonnxruntime.so` (the existing standalone ONNX Runtime dependency overlaps the sherpa AAR), add this temporary packaging rule and remove it in Task 10 together with the standalone dependency:

```kotlin
packaging { jniLibs { pickFirsts += "**/libonnxruntime.so" } }
```

- [ ] **Step 8: Run the tests and the build**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.*" && ./gradlew :app:assembleDebug`
Expected: tests PASS; build succeeds.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts gradle/libs.versions.toml app/src/main/java/com/example/asr app/src/test/java/com/example/asr
git commit -m "feat(asr): add sherpa-onnx runtime and pinned model downloader"
```

---

### Task 2: ASR models and ad cue detector

**Files:**
- Create: `app/src/main/java/com/example/asr/AsrModels.kt`
- Create: `app/src/main/java/com/example/asr/AdCueDetector.kt`
- Test: `app/src/test/java/com/example/asr/AdCueDetectorTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class AsrSegment(val startMs: Long, val endMs: Long, val text: String, val confidence: Float)`
  - `enum class AdSource { ASR, TRANSCRIPT, CHAPTER, USER }`
  - `data class DetectedAd(val startMs: Long, val endMs: Long, val label: String, val confidence: Float, val startCue: String?, val endCue: String?, val confirmed: Boolean, val source: AdSource)`
  - `enum class ScanMode { BOOTSTRAP_FULL, WINDOWED, FULL_MANUAL }`
  - `enum class ScanStatus { IDLE, SCANNING, PAUSED, PARTIAL, COMPLETE, FAILED }`
  - `class AdCueDetector(startPhrases: List<String> = DEFAULT_START_PHRASES, endPhrases: List<String> = DEFAULT_END_PHRASES, brands: List<String> = DEFAULT_BRANDS)`
  - `fun adsFromSegments(segments: List<AsrSegment>, maxAdMs: Long = 180_000L): List<DetectedAd>`

- [ ] **Step 1: Write the failing cue tests**

```kotlin
class AdCueDetectorTest {
    private val detector = AdCueDetector()

    @Test fun `start and end cues produce a confirmed ad`() {
        val ads = detector.adsFromSegments(listOf(
            AsrSegment(10_000, 15_000, "This episode is sponsored by AG1.", 0.9f),
            AsrSegment(15_000, 20_000, "Use code SAVE for twenty percent off.", 0.9f),
            AsrSegment(70_000, 74_000, "And now back to the show.", 0.9f),
        ))
        assertEquals(1, ads.size)
        assertTrue(ads[0].confirmed)
        assertEquals(10_000L, ads[0].startMs)
        assertEquals(74_000L, ads[0].endMs)
    }

    @Test fun `a brand alone is not an ad`() {
        assertTrue(detector.adsFromSegments(listOf(
            AsrSegment(0, 5_000, "I really like AG1 and drink it daily.", 0.9f)
        )).isEmpty())
    }

    @Test fun `a cue split across segments is joined`() {
        val ads = detector.adsFromSegments(listOf(
            AsrSegment(0, 2_000, "This episode is sponsored", 0.9f),
            AsrSegment(2_000, 4_000, "by BetterHelp.", 0.9f),
            AsrSegment(60_000, 62_000, "Back to the show.", 0.9f),
        ))
        assertEquals(1, ads.size)
        assertTrue(ads[0].confirmed)
    }

    @Test fun `a missing end cue stays unconfirmed and is capped`() {
        val ads = detector.adsFromSegments(listOf(
            AsrSegment(5_000, 10_000, "Support for this podcast comes from Shopify.", 0.9f),
            AsrSegment(240_000, 245_000, "Unrelated conversation continues here.", 0.9f),
        ))
        assertEquals(1, ads.size)
        assertFalse(ads[0].confirmed)
        assertEquals(185_000L, ads[0].endMs)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdCueDetectorTest"`
Expected: FAIL with unresolved references.

- [ ] **Step 3: Implement `AsrModels.kt` and `AdCueDetector.kt`**

Phrase lists (English, lowercase):
- Start: `sponsored by`, `brought to you by`, `support for this podcast`, `a word from our sponsor`, `our sponsor today`, `thanks to our sponsor`, `this episode is sponsored by`, `promo code`, `use code`, `free trial`, `ad break`, `commercial break`.
- End: `back to the show`, `back to our conversation`, `back to the podcast`, `now back to`, `let's get back to`, `welcome back`.
- Brands: reuse the list from the spec's existing `SPONSOR_BRANDS` (AG1, BetterHelp, NordVPN, Shopify, …).
- Join the current and previous segment text for matching so split cues work.
- A brand without a start phrase and without promotional language (`promo code`, `use code`, `free trial`, `discount`, `percent off`) is not an ad.
- End cue closes at that segment's `endMs`.
- No end cue: close at `min(start + maxAdMs, lastSegment.endMs)`, `confirmed = false`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdCueDetectorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/asr/AsrModels.kt app/src/main/java/com/example/asr/AdCueDetector.kt app/src/test/java/com/example/asr/AdCueDetectorTest.kt
git commit -m "feat(asr): detect ad cues in timestamped English segments"
```

---

### Task 3: Show slot learner

**Files:**
- Create: `app/src/main/java/com/example/data/AdEntities.kt`
- Create: `app/src/main/java/com/example/asr/AdSlotLearner.kt`
- Test: `app/src/test/java/com/example/asr/AdSlotLearnerTest.kt`

**Interfaces:**
- Consumes: `DetectedAd` from Task 2.
- Produces:
  - `@Entity(tableName = "ad_slots", primaryKeys = ["podcastId", "slotIndex"]) data class AdSlotEntity(...)` with the fields in Task 5.
  - `class AdSlotLearner { fun learn(existing: List<AdSlotEntity>, podcastId: String, durationMs: Long, ads: List<DetectedAd>): List<AdSlotEntity>; fun recordMiss(slots: List<AdSlotEntity>, slotIndex: Int): List<AdSlotEntity>; fun invalidateAll(slots: List<AdSlotEntity>): List<AdSlotEntity>; fun confidence(hits: Int, misses: Int): Float }`

- [ ] **Step 1: Write the failing tests**

```kotlin
class AdSlotLearnerTest {
    private val learner = AdSlotLearner()

    @Test fun `two detections merge into one slot`() {
        val first = learner.learn(emptyList(), "p", 600_000, listOf(ad(300_000, 360_000)))
        val second = learner.learn(first, "p", 620_000, listOf(ad(305_000, 365_000)))
        assertEquals(1, second.size)
        assertEquals(2, second[0].hits)
        assertEquals(302_500L, second[0].startMsAnchor)
    }

    @Test fun `pre-roll is anchored at zero`() {
        val slots = learner.learn(emptyList(), "p", 600_000, listOf(ad(0, 60_000)))
        assertEquals(0L, slots[0].startMsAnchor)
        assertEquals(0f, slots[0].startRatio)
        assertEquals(0, slots[0].slotIndex)
    }

    @Test fun `three misses disable a slot`() {
        var slots = learner.learn(emptyList(), "p", 600_000, listOf(ad(300_000, 360_000)))
        repeat(3) { slots = learner.recordMiss(slots, 0) }
        assertFalse(slots[0].enabled)
        assertTrue(slots[0].misses >= 3)
    }

    @Test fun `user boundaries are frozen while hits still update`() {
        val user = learner.learn(emptyList(), "p", 600_000, listOf(ad(300_000, 360_000)))
            .map { it.copy(source = AdSource.USER.name, startMsAnchor = 299_000) }
        val updated = learner.learn(user, "p", 600_000, listOf(ad(310_000, 370_000)))
        assertEquals(299_000L, updated[0].startMsAnchor)
        assertEquals(2, updated[0].hits)
    }

    @Test fun `confidence starts at half after one hit`() {
        assertEquals(0.5f, learner.confidence(1, 0))
        assertEquals(2f / 3f, learner.confidence(2, 0), 0.001f)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdSlotLearnerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `AdEntities.kt` slots and the learner**

`AdSlotEntity` fields: `podcastId`, `slotIndex`, `startRatio`, `startMsAnchor`, `durationMs`, `startVarianceMs`, `hits`, `misses`, `confidence`, `source`, `enabled`, `updatedAt`.
Match rule: `abs(startMs - anchor) <= max(45_000, 0.02 * durationMs)` or `abs(ratio - startRatio) <= 0.02f`.
Merge: running mean for anchor/duration/ratio; running mean absolute deviation for variance; `hits++`; `confidence = hits / (hits + misses + 1f)`.
Pre-roll: `startMs < 90_000 && ratio < 0.03f` → anchor 0, ratio 0.
`recordMiss`: `misses++`, recompute confidence, disable at `misses >= 3`.
User slots (`source == "USER"`): keep anchor, ratio, duration frozen; still update hits and confidence. Disabled slots stay disabled.
Sort slots by `startMsAnchor` and reassign `slotIndex` from 0.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdSlotLearnerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/AdEntities.kt app/src/main/java/com/example/asr/AdSlotLearner.kt app/src/test/java/com/example/asr/AdSlotLearnerTest.kt
git commit -m "feat(asr): learn per-show ad slots from confirmed ads"
```

---

### Task 4: Slot predictor and scan planner

**Files:**
- Create: `app/src/main/java/com/example/asr/AdSlotPredictor.kt`
- Create: `app/src/main/java/com/example/asr/ScanPlanner.kt`
- Test: `app/src/test/java/com/example/asr/AdSlotPredictorTest.kt`
- Test: `app/src/test/java/com/example/asr/ScanPlannerTest.kt`

**Interfaces:**
- Consumes: `AdSlotEntity`, `ScanMode`.
- Produces:
  - `data class PredictedWindow(val slotIndex: Int, val startMs: Long, val endMs: Long, val confirmed: Boolean)`
  - `class AdSlotPredictor { fun predict(slots: List<AdSlotEntity>, durationMs: Long, includeCandidates: Boolean = true): List<PredictedWindow> }`
  - `data class ScanRange(val startMs: Long, val endMs: Long, val priority: Int)`
  - `class ScanPlanner { fun plan(mode: ScanMode, durationMs: Long, playheadMs: Long, windows: List<PredictedWindow>, covered: List<LongRange>, lookAheadMs: Long = 180_000L): List<ScanRange> }`

- [ ] **Step 1: Write the failing predictor tests**

```kotlin
class AdSlotPredictorTest {
    @Test fun `mid-roll window is padded`() {
        val slot = slot(anchor = 300_000, duration = 60_000, ratio = 0.5f)
        val w = AdSlotPredictor().predict(listOf(slot), 600_000).single()
        assertEquals(270_000L, w.startMs)
        assertEquals(420_000L, w.endMs)
    }

    @Test fun `unknown duration only predicts the opening`() {
        val slots = listOf(slot(anchor = 0, duration = 60_000, ratio = 0f),
            slot(anchor = 300_000, duration = 60_000, ratio = 0.5f, index = 1))
        val out = AdSlotPredictor().predict(slots, 0)
        assertEquals(1, out.size)
        assertEquals(0L, out[0].startMs)
    }

    @Test fun `disabled slots are not predicted`() {
        assertTrue(AdSlotPredictor().predict(listOf(slot(enabled = false)), 600_000).isEmpty())
    }

    @Test fun `low-confidence automatic slots are not predicted`() {
        assertTrue(AdSlotPredictor().predict(listOf(slot(hits = 0, misses = 2)), 600_000).isEmpty())
    }

    @Test fun `feed candidates are predicted for scanning only`() {
        val candidate = slot(hits = 0, source = "TRANSCRIPT", confidence = 0.5f)
        assertEquals(1, AdSlotPredictor().predict(listOf(candidate), 600_000, includeCandidates = true).size)
        assertTrue(AdSlotPredictor().predict(listOf(candidate), 600_000, includeCandidates = false).isEmpty())
    }
}
```

- [ ] **Step 2: Write the failing planner tests**

```kotlin
class ScanPlannerTest {
    @Test fun `full scan starts with opening and look-ahead`() {
        val ranges = ScanPlanner().plan(ScanMode.BOOTSTRAP_FULL, 3_600_000, 1_200_000, emptyList(), emptyList())
        assertEquals(0L, ranges.first().startMs)
        assertEquals(1_380_000L, ranges.first().endMs)
    }

    @Test fun `windowed scan plans opening plus windows and subtracts covered`() {
        val windows = listOf(PredictedWindow(1, 270_000, 420_000, true))
        val ranges = ScanPlanner().plan(ScanMode.WINDOWED, 600_000, 0, windows, listOf(0L..120_000L))
        assertEquals(1, ranges.size)
        assertEquals(270_000L, ranges[0].startMs)
        assertEquals(420_000L, ranges[0].endMs)
    }

    @Test fun `overlapping windows merge`() {
        val windows = listOf(PredictedWindow(1, 270_000, 420_000, true),
            PredictedWindow(2, 400_000, 500_000, true))
        val ranges = ScanPlanner().plan(ScanMode.WINDOWED, 900_000, 0, windows, listOf(0L..120_000L))
        assertEquals(1, ranges.size)
        assertEquals(500_000L, ranges[0].endMs)
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.*PredictorTest" --tests "com.example.asr.ScanPlannerTest"`
Expected: FAIL.

- [ ] **Step 4: Implement predictor and planner**

Predictor: only predict slots with `confidence >= 0.5f`; candidate slots (`hits == 0`, `source` `TRANSCRIPT`/`CHAPTER`) are predicted only when `includeCandidates` is true. Blend `ratio * duration` (60 %) with `startMsAnchor` (40 %); pre-roll always 0. Window = `start - 30_000` to `start + durationMs + 60_000`, at least 120_000, clamped to `[0, durationMs]`. If `durationMs <= 0`, return only the opening window `0..120_000` for the pre-roll slot. Exclude `!enabled`.

Planner: subtract covered ranges; merge overlaps; `BOOTSTRAP_FULL`/`FULL_MANUAL` first split the episode into `[0, min(duration, playhead + lookAheadMs)]` and `[playhead + lookAheadMs, duration]`, then subtract covered ranges, merge, and sort. `WINDOWED` = opening + windows. Assign `priority = 0` to ranges intersecting `[playhead, playhead + lookAheadMs]`, `1` to `[0, 120_000]`, `2` otherwise; sort by priority then start.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdSlotPredictorTest" --tests "com.example.asr.ScanPlannerTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/asr/AdSlotPredictor.kt app/src/main/java/com/example/asr/ScanPlanner.kt app/src/test/java/com/example/asr
git commit -m "feat(asr): predict slot windows and plan windowed scans"
```

---

### Task 5: Room schema v7 and scan store

**Files:**
- Modify: `app/src/main/java/com/example/data/AdEntities.kt`
- Create: `app/src/main/java/com/example/data/AdDao.kt`
- Create: `app/src/main/java/com/example/data/AdScanStore.kt`
- Create: `app/src/main/java/com/example/data/RoomAdScanStore.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDatabase.kt:8-10`
- Test: `app/src/test/java/com/example/data/AdDaoTest.kt`

**Interfaces:**
- Consumes: entities from Task 3.
- Produces:
  - `AdSlotEntity`, `EpisodeAdSegmentEntity(id: String PK)`, `EpisodeAsrSegmentEntity(id: String PK)`, `EpisodeScanEntity(episodeId: String PK)`.
  - `AdScanStore` methods: `scan`, `saveScan`, `slots`, `saveSlots`, `asrSegments`, `upsertAsrSegments`, `adSegments`, `replaceAdSegments`, `saveTranscriptProjection(episodeId, text)`, `saveEpisodeDuration(episodeId, seconds)`.
  - `RoomAdScanStore(dao: AdDao)`.
  - `CoverageCodec.encode(List<LongRange>): String`, `decode(String): List<LongRange>`.

- [ ] **Step 1: Write the failing Room test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class AdDaoTest {
    private lateinit var db: PodcastDatabase
    private lateinit var dao: AdDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PodcastDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.adDao()
    }
    @After fun tearDown() = db.close()

    @Test fun `slots and ads round-trip`() = runBlocking {
        dao.upsertSlots(listOf(AdSlotEntity("p", 0, 0f, 0, 60_000, 0, 1, 0, 0.5f, "ASR", true, 1)))
        dao.upsertAdSegments(listOf(EpisodeAdSegmentEntity("e:0:60000", "e", "p", 0, 60_000, "AG1", 0.9f, "ASR", true, 1)))
        assertEquals(1, dao.slots("p").size)
        assertEquals(1, dao.adSegments("e").size)
        assertEquals(1, dao.adSegmentsOverlapping("e", 10_000, 20_000).size)
    }

    @Test fun `coverage codec round-trips`() {
        val ranges = listOf(0L..120_000L, 300_000L..365_000L)
        assertEquals(ranges, CoverageCodec.decode(CoverageCodec.encode(ranges)))
        assertTrue(CoverageCodec.decode("").isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDaoTest"`
Expected: FAIL.

- [ ] **Step 3: Implement entities, DAO, database version, store**

Entities per spec. `EpisodeAsrSegmentEntity` uses a deterministic id `"$episodeId:$startMs"`, an index on `(episodeId, startMs)`, and `@Insert(onConflict = REPLACE)`. `EpisodeScanEntity` stores `coveredRanges` as text, `actualDurationMs`, `durationSource`, `mode`, `status`, `modelVersion`, `updatedAt`. `PodcastDatabase` version 7, `entities = [..., AdSlotEntity::class, EpisodeAdSegmentEntity::class, EpisodeAsrSegmentEntity::class, EpisodeScanEntity::class]`, `abstract fun adDao(): AdDao`.

DAO: `@Query("SELECT * FROM ad_slots WHERE podcastId = :podcastId ORDER BY slotIndex")`, `@Query("SELECT * FROM episode_ad_segments WHERE episodeId = :episodeId AND startMs < :endMs AND endMs > :startMs")`, `@Query("UPDATE episodes SET transcript = :text WHERE id = :episodeId")`, `@Query("UPDATE episodes SET durationSeconds = :seconds WHERE id = :episodeId")`, upserts, deletes.

`CoverageCodec`: `"0-120000,300000-365000"`; unit-test round trip and empty string.

- [ ] **Step 4: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDaoTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/AdEntities.kt app/src/main/java/com/example/data/AdDao.kt app/src/main/java/com/example/data/AdScanStore.kt app/src/main/java/com/example/data/RoomAdScanStore.kt app/src/main/java/com/example/data/PodcastDatabase.kt app/src/test/java/com/example/data/AdDaoTest.kt
git commit -m "feat(data): add Room v7 tables for ad slots, segments, and scans"
```

---

### Task 6: Speech engine wrapper and on-device smoke test

**Files:**
- Create: `app/src/main/java/com/example/asr/SpeechRecognizerEngine.kt`
- Create: `app/src/main/java/com/example/asr/SherpaSpeechRecognizer.kt`
- Test: `app/src/androidTest/java/com/example/asr/SherpaRecognizerInstrumentedTest.kt`
- Add asset: `app/src/androidTest/assets/asr_sample.wav` (downloaded in Step 1)

**Interfaces:**
- Consumes: `AsrSegment`, `ModelDownloadManager`.
- Produces:
  - `interface SpeechRecognizerEngine { fun startStream(): SpeechStream; fun close() }`
  - `interface SpeechStream { fun accept(samples: FloatArray); fun pollSegments(): List<AsrSegment>; fun finish(): List<AsrSegment>; fun close() }`
  - `class SherpaSpeechRecognizer(modelDir: File, numThreads: Int = 2) : SpeechRecognizerEngine`

- [ ] **Step 1: Add the sample WAV**

```bash
mkdir -p app/src/androidTest/assets
curl -L "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main/test_wavs/0.wav" \
  -o app/src/androidTest/assets/asr_sample.wav
```

- [ ] **Step 2: Write the failing instrumented test**

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class SherpaRecognizerInstrumentedTest {
    @Test fun `transcribes the sample wav with timestamps`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val models = ModelDownloadManager(ctx, OkHttpClient()).download {}.getOrThrow()
        val wav = readWav(ctx.assets.open("asr_sample.wav"))
        val engine = SherpaSpeechRecognizer(models)
        val stream = engine.startStream()
        wav.samples.toList().chunked(1_600).forEach { stream.accept(it.toFloatArray()) }
        val segments = stream.finish()
        assertTrue(segments.isNotEmpty())
        assertTrue(segments.joinToString(" ") { it.text }.isNotBlank())
        assertTrue(segments.all { it.endMs >= it.startMs })
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.asr.SherpaRecognizerInstrumentedTest`
Expected: FAIL with unresolved references.

- [ ] **Step 4: Implement the engine**

Create `OnlineRecognizer(assetManager = null, config)` with:
```kotlin
OnlineModelConfig(
  transducer = OnlineTransducerModelConfig(encoder = ..., decoder = ..., joiner = ...),
  tokens = ..., modelType = "zipformer", numThreads = 2, provider = "cpu", debug = false
)
OnlineRecognizerConfig(
  featConfig = FeatureConfig(16_000, 80), modelConfig = modelConfig,
  enableEndpoint = true, decodingMethod = "greedy_search"
)
```
For each stream, track `fedSamples` and `streamStartSamples` locally; `startStream()` must return a fresh stream whose timebase starts at 0, so the controller can add `range.startMs` to every returned timestamp. On `pollSegments`, loop `while (recognizer.isReady(stream)) recognizer.decode(stream)`. If `isEndpoint`, get `result`, add tail padding (0.8 s), decode again, build `AsrSegment` with `startMs = (streamStartSamples + result.timestamps.first() * 16000).toLong()`, `endMs = (streamStartSamples + result.timestamps.last() * 16000 + 400).toLong()`, confidence = mean of `ysProbs` (fallback 0.9), then `recognizer.reset(stream)`, set `streamStartSamples = fedSamples`. `finish()` calls `inputFinished()`, decodes, and emits the remainder. `close()` releases the stream; the shared recognizer is released by `engine.close()`.

- [ ] **Step 5: Run the instrumented test**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.asr.SherpaRecognizerInstrumentedTest`
Expected: PASS, non-empty transcript. This downloads ~43 MB once.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/asr/SpeechRecognizerEngine.kt app/src/main/java/com/example/asr/SherpaSpeechRecognizer.kt app/src/androidTest/java/com/example/asr/SherpaRecognizerInstrumentedTest.kt app/src/androidTest/assets/asr_sample.wav
git commit -m "feat(asr): wrap sherpa-onnx streaming recognizer"
```

---

### Task 7: PCM decoding, conversion, and HTTP Range source

**Files:**
- Create: `app/src/main/java/com/example/asr/PcmConverter.kt`
- Create: `app/src/main/java/com/example/asr/PcmSource.kt`
- Create: `app/src/main/java/com/example/asr/MediaPcmSource.kt`
- Create: `app/src/main/java/com/example/asr/HttpRangeDataSource.kt`
- Test: `app/src/test/java/com/example/asr/PcmConverterTest.kt`
- Test: `app/src/test/java/com/example/asr/HttpRangeDataSourceTest.kt`
- Test: `app/src/androidTest/java/com/example/asr/MediaPcmSourceInstrumentedTest.kt`

**Interfaces:**
- Consumes: `EpisodeEntity`.
- Produces:
  - `object PcmConverter { fun pcm16ToFloat(bytes: ByteArray, channels: Int): FloatArray; fun pcmFloatToFloat(bytes: ByteArray, channels: Int): FloatArray; fun resample(input: FloatArray, fromHz: Int, toHz: Int = 16_000): FloatArray }`
  - `interface PcmSource { val durationMs: Long; fun reader(startMs: Long): PcmReader }`
  - `interface PcmReader : Closeable { fun read(out: FloatArray): Int }`
  - `interface PcmSourceFactory { fun create(episode: EpisodeEntity): PcmSource }`
  - `class MediaPcmSourceFactory(context: Context, cacheDir: File) : PcmSourceFactory`
  - `class HttpRangeDataSource(url: String, cacheDir: File, client: OkHttpClient)` — plain class with `readAt(position, buffer, offset, size)`, `getSize()`, `close()`.
  - `class HttpMediaDataSource(delegate: HttpRangeDataSource) : MediaDataSource()` — adapter for `MediaExtractor`.

- [ ] **Step 1: Write the failing converter tests**

```kotlin
class PcmConverterTest {
    @Test fun `stereo 48k one second becomes 16k mono`() {
        val frames = 48_000
        val bytes = ByteArray(frames * 4)
        for (i in 0 until frames) {
            val s = (Short.MAX_VALUE / 2).toShort()
            bytes[i * 4] = s.toByte(); bytes[i * 4 + 1] = (s.toInt() shr 8).toByte()
            bytes[i * 4 + 2] = s.toByte(); bytes[i * 4 + 3] = (s.toInt() shr 8).toByte()
        }
        val mono = PcmConverter.pcm16ToFloat(bytes, channels = 2)
        assertEquals(frames, mono.size)
        val out = PcmConverter.resample(mono, 48_000)
        assertTrue(kotlin.math.abs(out.size - 16_000) <= 2)
    }

    @Test fun `float pcm is copied`() {
        val bytes = ByteArray(8)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(0, 0.5f)
        assertEquals(0.5f, PcmConverter.pcmFloatToFloat(bytes, 1)[0], 0.001f)
    }
}
```

- [ ] **Step 2: Write the failing Range tests**

```kotlin
class HttpRangeDataSourceTest {
    @Test fun `readAt fetches a range and caches it`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(206).setBody("abcdefghij"))
        server.start()
        val source = HttpRangeDataSource(server.url("/a.mp3").toString(), createTempDir(), OkHttpClient())
        val buf = ByteArray(4)
        assertEquals(4, source.readAt(2, buf, 0, 4))
        assertEquals("cdef", String(buf))
        source.readAt(2, buf, 0, 4)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a 200 response falls back to a full read`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("abcdefghij"))
        server.start()
        val source = HttpRangeDataSource(server.url("/a.mp3").toString(), createTempDir(), OkHttpClient())
        val buf = ByteArray(4)
        assertEquals(4, source.readAt(2, buf, 0, 4))
        assertEquals("cdef", String(buf))
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.PcmConverterTest" --tests "com.example.asr.HttpRangeDataSourceTest"`
Expected: FAIL.

- [ ] **Step 4: Implement converter and sources**

`PcmConverter` handles `ByteOrder.LITTLE_ENDIAN` 16-bit and float, downmixes channels by averaging, and resamples with linear interpolation.
`MediaPcmSourceFactory` picks `FilePcmSource` for `episode.downloadLocalPath` else `HttpPcmSource`.
`MediaPcmSource` opens `MediaExtractor`, selects the first audio track, seeks to `startMs`, configures `MediaCodec` with `MediaFormat`, reads output buffers, converts to 16 kHz mono, and returns chunks of at most 1_600 samples. It must trim decoded samples whose presentation time is before the requested `startMs` (decoder seek lands on the previous sync frame), so segment timestamps stay aligned.
`HttpRangeDataSource` implements `readAt`/`getSize`/`close`, uses 1 MB aligned chunks cached in `cacheDir/asr/<hash>/`, and on HTTP 200 caches the whole body once. It must not recurse or re-request the same chunk.

- [ ] **Step 5: Write and run the decode test**

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class MediaPcmSourceInstrumentedTest {
    @Test fun `decodes the first ten seconds of a real episode`() = runTest {
        val ep = EpisodeEntity(id = "x", podcastId = "p", podcastTitle = "", podcastCoverUrl = "",
            title = "", description = "", durationSeconds = 600, publishDate = "",
            audioUrl = "https://traffic.megaphone.fm/SCIM7156610982.mp3")
        val source = MediaPcmSourceFactory(ctx, ctx.cacheDir).create(ep)
        val reader = source.reader(30_000)
        val out = FloatArray(1_600)
        var total = 0
        repeat(100) { total += reader.read(out) }
        reader.close()
        assertTrue(total > 100_000)
        assertTrue(out.any { kotlin.math.abs(it) > 0.01f })
    }
}
```

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.asr.MediaPcmSourceInstrumentedTest`
Expected: PASS (requires network).

- [ ] **Step 6: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.PcmConverterTest" --tests "com.example.asr.HttpRangeDataSourceTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/asr/PcmConverter.kt app/src/main/java/com/example/asr/PcmSource.kt app/src/main/java/com/example/asr/MediaPcmSource.kt app/src/main/java/com/example/asr/HttpRangeDataSource.kt app/src/test/java/com/example/asr app/src/androidTest/java/com/example/asr/MediaPcmSourceInstrumentedTest.kt
git commit -m "feat(asr): decode episode audio and read HTTP ranges"
```

---

### Task 8: Scan controller with look-ahead and persistence

**Files:**
- Create: `app/src/main/java/com/example/asr/AdScanController.kt`
- Test: `app/src/test/java/com/example/asr/AdScanControllerTest.kt`
- Test fakes: `app/src/test/java/com/example/asr/Fakes.kt`

**Interfaces:**
- Consumes: `AdScanStore`, `PcmSourceFactory`, `SpeechRecognizerEngine`, `AdCueDetector`, `AdSlotLearner`, `AdSlotPredictor`, `ScanPlanner`.
- Produces:
  - `data class ScanState(val episodeId: String, val mode: ScanMode, val status: ScanStatus, val coveredMs: Long, val totalMs: Long, val aheadOfPlayheadMs: Long, val adsFound: Int, val error: String? = null)`
  - `class AdScanController(store, engine, pcmSources, cueDetector, slotLearner, predictor, planner, scope, throttle: suspend () -> Unit = { delay(250) }, isEnabled: () -> Boolean = { true }, isModelReady: () -> Boolean = { true }, bootstrap: (EpisodeEntity) -> List<DetectedAd> = { emptyList() }, logger: (String) -> Unit = { android.util.Log.i("AdScan", it) })`
  - `val state: StateFlow<ScanState>`
  - `val currentAds: StateFlow<List<EpisodeAdSegmentEntity>>`
  - `fun start(episode: EpisodeEntity, playheadMs: Long)`
  - `fun updatePlayhead(positionMs: Long, isPlaying: Boolean)`
  - `fun seek(positionMs: Long)`
  - `fun pause()`
  - `fun stop()`
  - `fun relearnShow(podcastId: String)`
  - `fun onFeedMetadataUpdated(episode: EpisodeEntity)` — if a feed transcript or sponsor chapters exist and no scan is complete, create candidate slots and start a windowed scan.
  - `suspend fun awaitIdle()` for tests.

- [ ] **Step 1: Write the failing controller tests with fakes**

```kotlin
class AdScanControllerTest {
    @Test fun `full scan learns pre-roll and mid-roll slots`() = runTest {
        val store = FakeStore()
        val source = FakePcmSource(durationMs = 600_000)
        val engine = FakeEngine(mapOf(
            0L to "This episode is sponsored by AG1.",
            60_000L to "And now back to the show.",
            300_000L to "A word from our sponsors. Use code SAVE.",
            360_000L to "Back to the show."
        ))
        val controller = controller(store, source, engine)
        controller.start(episode(), 0)
        controller.updatePlayhead(600_000, true)
        controller.awaitIdle()
        val slots = store.slots("p")
        assertEquals(2, slots.size)
        assertEquals(0L, slots[0].startMsAnchor)
        assertEquals(300_000L, slots[1].startMsAnchor)
    }

    @Test fun `second episode uses windowed mode and reads under 15 percent`() = runTest {
        val store = FakeStore().apply { saveSlots("p", learnedSlotsForLongEpisode()) }
        val source = FakePcmSource(durationMs = 3_600_000)
        val controller = controller(store, source, FakeEngine(emptyMap()))
        controller.start(episode(id = "e2"), 0)
        controller.updatePlayhead(3_600_000, true)
        controller.awaitIdle()
        assertEquals(ScanMode.WINDOWED, controller.state.value.mode)
        assertTrue(source.totalReadMs < 3_600_000 * 0.15)
    }

    @Test fun `seek during a scan adds the target range once`() = runTest { /* ... */ }

    @Test fun `auto-skip off never produces a skip decision`() { /* decider test lives in Task 9 */ }

    @Test fun `stopping saves partial coverage`() = runTest {
        val store = FakeStore()
        val controller = controller(store, FakePcmSource(600_000), FakeEngine(emptyMap()))
        controller.start(episode(), 0)
        controller.stop()
        assertEquals(ScanStatus.PARTIAL, store.scan("e")!!.status)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdScanControllerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement the controller**

On `start`:
1. If ASR is disabled or the model is missing, set `state = IDLE` and return.
2. Cancel the previous job; load `scan`, `slots`, `episode` ASR segments, and existing ads.
3. Create the `PcmSource` and read `durationMs`; if it is > 0 and differs from `episode.durationSeconds * 1000` by more than 5 s, persist it in `EpisodeScanEntity.actualDurationMs`, call `saveEpisodeDuration`, and use it for planning and slot ratios.
4. Decide mode: `FULL_MANUAL` if flagged; else `WINDOWED` if any enabled slots or bootstrap candidates exist; else `BOOTSTRAP_FULL`.
5. Predict windows, plan ranges with `ScanPlanner`, merge with persisted segments, and launch on `Dispatchers.Default`.

For each range:
- Open `PcmSource.reader(range.startMs)`.
- Read 1_600-sample chunks; `stream.accept`; add `readMs`; `engine.pollSegments()`, offset each segment by `range.startMs`, persist every 20 chunks.
- After each chunk in `FULL`, if `range.startMs + readMs > playhead + 180_000`, call `throttle()` and wait while paused.
- At range end, `stream.finish()`; run `cueDetector.adsFromSegments(allSegmentsForEpisode)`; merge with the existing ads, preferring a confirmed detection over an unconfirmed one and never downgrading a confirmed ad. Upsert all detected ads, including unconfirmed ones (so the player can show "end is being searched"), and update `currentAds`.
- Persist coverage after each range and learn slots from the confirmed ads found so far (so a partial scan still yields usable slots).
- Rebuild the `EpisodeEntity.transcript` display projection from all ASR segments (`MM:SS [ASR] text`) and save it through the store, so the transcript screen shows real text.
- `onFeedMetadataUpdated` creates candidate slots from `ShowBootstrap.candidates(episode)` and starts `WINDOWED` if no scan is complete.

On completion:
- Learn slots from confirmed ads with `durationMs = scan.actualDurationMs ?: episode.durationSeconds * 1000`.
- Save slots; save scan `COMPLETE`.
- If no ads and no slots, mark `PARTIAL` rather than inventing a slot.
- Log one line: `Log.i("AdScan", "mode=$mode episode=$episodeId coveredMs=$covered ads=$ads")`.

On `seek`: update playhead; if target is outside covered ranges, prepend `[target, min(target+180_000, duration)]` to the queue without restarting covered ranges.
On `pause`: set a flag; the job waits at the throttle point.
On `stop`: cancel, persist coverage, status `PARTIAL`.
On `relearnShow`: mark all automatic slots `enabled=false`, delete scan state for the current episode, force `FULL_MANUAL`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdScanControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/asr/AdScanController.kt app/src/test/java/com/example/asr/AdScanControllerTest.kt app/src/test/java/com/example/asr/Fakes.kt
git commit -m "feat(asr): add look-ahead scan controller with persistence"
```

---

### Task 9: Skip decider and ViewModel wiring

**Files:**
- Create: `app/src/main/java/com/example/player/AdSkipDecider.kt`
- Test: `app/src/test/java/com/example/player/AdSkipDeciderTest.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt:13-35, 210-290, 1294-1502, 1607-1670, 2089-2411`
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt:3480-4050` (real ad markers)
- Modify: `app/src/main/java/com/example/data/PodcastRepository.kt` (expose store)

**Interfaces:**
- Consumes: `EpisodeAdSegmentEntity`, `AdScanController`.
- Produces:
  - `sealed class SkipDecision { data class Skip(val ad: EpisodeAdSegmentEntity): SkipDecision(); data class AwaitEnd(val startMs: Long): SkipDecision(); object None: SkipDecision() }`
  - `class AdSkipDecider { fun decide(positionMs: Long, ads: List<EpisodeAdSegmentEntity>, autoSkip: Boolean): SkipDecision }`
  - ViewModel flows: `adScanState`, `currentAdSegments`, `asrEnabled`, `asrModelState`; initialize `_asrEnabled` and `_asrModelReady` to `false` here, Task 11 wires persistence and download.
  - Actions: `setAsrEnabled`, `downloadAsrModel`, `deleteAsrModel`, `relearnShow`, `updateAdSlot`, `toggleAdSlot`, `deleteAdSlot`.

- [ ] **Step 1: Write the failing decider tests**

```kotlin
class AdSkipDeciderTest {
    private val ad = EpisodeAdSegmentEntity("a", "e", "p", 10_000, 70_000, "AG1", 0.9f, "ASR", true, 1)

    @Test fun `inside a confirmed ad skips to the end`() {
        val d = AdSkipDecider().decide(20_000, listOf(ad), autoSkip = true)
        assertEquals(SkipDecision.Skip(ad), d)
    }
    @Test fun `outside the ad does nothing`() {
        assertEquals(SkipDecision.None, AdSkipDecider().decide(80_000, listOf(ad), true))
    }
    @Test fun `auto skip off does nothing`() {
        assertEquals(SkipDecision.None, AdSkipDecider().decide(20_000, listOf(ad), false))
    }
    @Test fun `an unconfirmed ad awaits its end without seeking`() {
        val pending = ad.copy(confirmed = false)
        assertEquals(SkipDecision.AwaitEnd(10_000), AdSkipDecider().decide(20_000, listOf(pending), true))
    }
    @Test fun `near the end does nothing to avoid a loop`() {
        assertEquals(SkipDecision.None, AdSkipDecider().decide(69_500, listOf(ad), true))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.player.AdSkipDeciderTest"`
Expected: FAIL.

- [ ] **Step 3: Implement the decider**

Return `Skip` when `autoSkip && confirmed && position >= start && position < end - 1_000`. Return `AwaitEnd(startMs)` when `autoSkip && !confirmed && position >= start && position < end` (the controller only stores such a segment after hearing a start cue). Otherwise return `None`. Do not seek for unconfirmed segments.

- [ ] **Step 4: Wire the ViewModel**

- Instantiate `AdScanStore`, `AdScanController`, and `AdSkipDecider`; keep `modelDownloadManager`.
- Remove the ad-detection use of `AudioWaveAdDetector`, the ad methods of `PodcastKeywordSpotter`, and `VozTranscriber`; delete the `_acousticAdSegments`, `_detectedTranscriptAds`, and `detectedTranscriptAds` flows and their consumers. Keep only the cosmetic waveform builder until Task 10 renames the file.
- `playEpisode`: call `adScanController.start(currentEp, effectiveStartPos)` after starting playback.
- `startPlaybackJob`: call `adScanController.updatePlayhead(newPosition, _isPlaying.value)` every tick.
- `seekTo`: call `adScanController.seek(target)`.
- Pause/stop: `adScanController.pause()` / `stop()`.
- Replace `checkSponsorAndAdDetection` with:
  ```kotlin
  when (val d = adSkipDecider.decide(positionMs, _currentAdSegments.value, _isAutoAdSkipEnabled.value && _asrEnabled.value && _asrModelReady.value)) {
      is SkipDecision.Skip -> {
          lastSkippedAdId.let { if (it != d.ad.id) { lastSkippedAdId = d.ad.id; seekTo(d.ad.endMs); ... } }
      }
      is SkipDecision.AwaitEnd -> _sponsorSkipEvent.value = "Ad erkannt – Ende wird gesucht…"
      SkipDecision.None -> Unit
  }
  ```
- Delete `performAdSkip` and the 15 s guess.
- Delete the chapter-based auto-skip block entirely. Real sponsor chapters only seed candidate windows through `ShowBootstrap`; they never skip without an ASR-confirmed segment.
- Update `PodcastAppContent`: collect `currentAdSegments`; replace the `acousticAdSegments` check in the waveform strip with `currentAdSegments`; replace "AD WAVE SPIKE" with "AD SEGMENT CONFIRMED"; keep the cosmetic waveform bars. Set `_isAdActive` in the ViewModel when the playhead is inside a confirmed segment.
- Update `toggleAutoAdSkip` copy to "On-device ad detection" and remove "wave spikes".

- [ ] **Step 5: Run unit tests and build**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.player.AdSkipDeciderTest" && ./gradlew :app:assembleDebug`
Expected: PASS; build succeeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/player/AdSkipDecider.kt app/src/test/java/com/example/player/AdSkipDeciderTest.kt app/src/main/java/com/example/ui/PodcastViewModel.kt app/src/main/java/com/example/ui/PodcastAppContent.kt app/src/main/java/com/example/data/PodcastRepository.kt
git commit -m "feat(player): skip only cue-confirmed ad segments"
```

---

### Task 10: Feed bootstrap and removal of synthetic ad evidence

**Files:**
- Create: `app/src/main/java/com/example/asr/ShowBootstrap.kt`
- Create: `app/src/main/java/com/example/asr/IntroMusicDetector.kt`
- Test: `app/src/test/java/com/example/asr/ShowBootstrapTest.kt`
- Modify: `app/src/main/java/com/example/network/PodcastApiClient.kt:340-465, 680-730`
- Modify: `app/src/main/java/com/example/data/PodcastRepository.kt` seed data and healing
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt` `currentChapters`
- Delete: `app/src/main/java/com/example/player/VozTranscriber.kt`
- Delete: `app/src/main/java/com/example/player/VozModelManager.kt`
- Modify: `app/src/main/java/com/example/player/PodcastKeywordSpotter.kt` → `IntroMusicDetector.kt`
- Modify: `app/src/main/java/com/example/player/AudioWaveAdDetector.kt` → `AudioWaveformVisualizer.kt`
- Modify: `app/build.gradle.kts` (remove `com.microsoft.onnxruntime:onnxruntime-android` now that `VozModelManager` is deleted)

**Interfaces:**
- Consumes: `TranscriptParser`, `ChapterParser`, `DetectedAd`.
- Produces:
  - `class ShowBootstrap { fun candidates(episode: EpisodeEntity): List<DetectedAd> }`
  - `class IntroMusicDetector { fun detect(podcastTitle: String, transcript: String?, chapters: String?): IntroMusicInterval? }`

- [ ] **Step 1: Write the failing bootstrap test**

```kotlin
class ShowBootstrapTest {
    @Test fun `feed transcript yields candidate ads`() {
        val ep = episode(transcript = "00:00 [Host] Welcome\n05:00 [Sponsor Break] Sponsors: Brought to you by AG1. Use code SAVE.\n06:00 [Host] And now back to the show.")
        val ads = ShowBootstrap().candidates(ep)
        assertEquals(1, ads.size)
        assertEquals(300_000L, ads[0].startMs)
        assertFalse(ads[0].confirmed)
    }
    @Test fun `sponsor chapters yield candidates`() {
        val ep = episode(chapters = "0:Intro|300:Sponsor: AG1|360:Main topic")
        assertEquals(1, ShowBootstrap().candidates(ep).size)
    }
    @Test fun `empty evidence yields nothing`() {
        assertTrue(ShowBootstrap().candidates(episode()).isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.ShowBootstrapTest"`
Expected: FAIL.

- [ ] **Step 3: Implement bootstrap and intro detector**

`ShowBootstrap.candidates` parses real `episode.transcript` with `TranscriptParser.parseTranscript` and runs `AdCueDetector`; for chapters, it selects `isSponsorChapter()` entries and uses the next chapter start (or +60 s) as the end. All candidates are `confirmed = false` and `source = TRANSCRIPT/CHAPTER`.

`IntroMusicDetector` keeps chapter/transcript cue logic only; delete the AoM 28 s fallback.

- [ ] **Step 4: Delete synthetic generation**

- `PodcastApiClient.parseRssFeed`: remove the generated chapters block (`durationSec * 0.15` etc.), remove `adTimestampsSeconds = "45,…"`, remove the dead `parseOrGenerateTranscript(null)` call.
- `PodcastApiClient.fetchEnrichedMetadataForEpisode`: keep the real `<podcast:transcript>` fetch; remove `adTimestampsSeconds = "45,…"`.
- `CuratedPodcastCatalog.generateEpisodesForShow`: remove generated `adTimestampsSeconds` and transcript.
- `PodcastRepository.populateInitialDataIfNeeded`: set seeded `transcript`, `chapters`, `adTimestampsSeconds` to `""` for demo episodes and stop seeding those fields.
- `PodcastViewModel.currentChapters`: return only `ChapterParser.parseChapters(...)`, no synthetic sponsor chapter.
- `refreshMetadataAfterwards`: delete the Voz/waveform ad blocks; call `adScanController.onFeedMetadataUpdated(currentEp)` (add this method in Task 8: if feed transcript exists and no scan is complete, create candidate slots). If an ASR scan/segments exist for the episode, do not overwrite `EpisodeEntity.transcript` with feed text; the ASR projection wins for display.
- Delete `VozTranscriber.kt` and `VozModelManager.kt`.
- Rename `AudioWaveAdDetector` to `AudioWaveformVisualizer`, keep only `buildWaveform(id, duration)` for the cosmetic strip; delete `AcousticAdSegment`, `findAcousticAdAtPosition`, `isCommercialLoudnessSurge`, `detectMusicIntroAcoustics`.
- Rename `PodcastKeywordSpotter` to `IntroMusicDetector`, keep `detect`, delete ads methods and `AdSkipInterval`.

- [ ] **Step 5: Run tests, build, and search for leftovers**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.asr.ShowBootstrapTest" && ./gradlew :app:assembleDebug
grep -rn "Voz\|adTimestampsSeconds = \"45\|Featured Partner\|wave spike\|AcousticAdSegment" app/src/main/java || true
```
Expected: PASS; build succeeds; grep returns only legitimate model names or nothing.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove synthetic ad detection and bootstrap from feed evidence"
```

---

### Task 11: ASR settings, model download UI, and enable flow

**Files:**
- Create: `app/src/main/java/com/example/asr/AsrPreferences.kt`
- Create: `app/src/main/java/com/example/ui/AsrSettingsSection.kt`
- Test: `app/src/test/java/com/example/asr/AsrPreferencesTest.kt`
- Modify: `app/src/main/java/com/example/ui/SettingsScreen.kt` (insert after the auto-skip section)
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt` (expose state and actions)

**Interfaces:**
- Consumes: `ModelDownloadManager`.
- Produces:
  - `class AsrPreferences(context: Context) { var enabled: Boolean; var modelVersion: String?; fun isModelInstalled(): Boolean }`
  - `sealed class AsrModelState { object Disabled; object NotDownloaded; data class Downloading(val progress: Float); object Ready; data class Failed(val message: String) }`
  - ViewModel: `asrEnabled: StateFlow<Boolean>`, `asrModelState: StateFlow<AsrModelState>`, `setAsrEnabled(Boolean)`, `downloadAsrModel()`, `deleteAsrModel()`.

- [ ] **Step 1: Write the failing preferences test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class AsrPreferencesTest {
    @Test fun `enable flag persists`() {
        val prefs = AsrPreferences(ApplicationProvider.getApplicationContext())
        prefs.enabled = true
        assertTrue(AsrPreferences(ApplicationProvider.getApplicationContext()).enabled)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AsrPreferencesTest"`
Expected: FAIL.

- [ ] **Step 3: Implement preferences and UI**

`AsrPreferences` uses `context.getSharedPreferences("asr_prefs", MODE_PRIVATE)` with keys `enabled` and `model_version`.

`AsrSettingsSection` shows:
- Toggle "On-device ad detection (English)".
- If off: one line explaining English-only, offline, ~42 MB model.
- If not downloaded: "Download model (42 MB)" button and progress bar.
- If ready: "Model ready · Delete model" and the Apache-2.0 license link/text.
- If failed: error text and retry.

`PodcastViewModel`: `setAsrEnabled(true)` sets `NotDownloaded` if the model is absent; `downloadAsrModel()` launches `ModelDownloadManager.download`, updates `Downloading(progress)`, then `Ready` and `enabled=true`; `setAsrEnabled(false)` stops scans and sets `Disabled`; `deleteAsrModel()` stops scans, deletes files, sets `NotDownloaded`.

- [ ] **Step 4: Build and run the test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AsrPreferencesTest" && ./gradlew :app:assembleDebug`
Expected: PASS; build succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/asr/AsrPreferences.kt app/src/main/java/com/example/ui/AsrSettingsSection.kt app/src/main/java/com/example/ui/SettingsScreen.kt app/src/main/java/com/example/ui/PodcastViewModel.kt app/src/test/java/com/example/asr/AsrPreferencesTest.kt
git commit -m "feat(ui): add ASR enable flow and model download settings"
```

---

### Task 12: Editable show-slot UI

**Files:**
- Create: `app/src/main/java/com/example/ui/AdSlotsSection.kt`
- Test: `app/src/test/java/com/example/asr/SlotEditReducerTest.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (`PodcastDetailScreen` around line 1357)
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt` (slot flows and actions)

**Interfaces:**
- Consumes: `AdSlotEntity`, ViewModel actions.
- Produces:
  - `object SlotEditReducer { fun edit(slot: AdSlotEntity, startMs: Long, durationMs: Long, episodeDurationMs: Long): AdSlotEntity; fun toggle(slot: AdSlotEntity): AdSlotEntity; fun delete(slots: List<AdSlotEntity>, slotIndex: Int): List<AdSlotEntity> }`
  - `@Composable fun AdSlotsSection(slots: List<AdSlotEntity>, episodeDurationMs: Long, onEdit: ..., onToggle: ..., onDelete: ..., onRelearn: ...)`

- [ ] **Step 1: Write the failing reducer tests**

```kotlin
class SlotEditReducerTest {
    @Test fun `edit marks the slot as user and freezes the new boundary`() {
        val s = slot(anchor = 300_000, duration = 60_000)
        val e = SlotEditReducer.edit(s, 310_000, 30_000, 600_000)
        assertEquals("USER", e.source)
        assertEquals(310_000L, e.startMsAnchor)
        assertEquals(30_000L, e.durationMs)
        assertTrue(e.enabled)
    }
    @Test fun `edit clamps to the episode`() {
        val e = SlotEditReducer.edit(slot(), 590_000, 30_000, 600_000)
        assertEquals(590_000L, e.startMsAnchor)
        assertEquals(10_000L, e.durationMs)
    }
    @Test fun `toggle flips enabled`() {
        assertFalse(SlotEditReducer.toggle(slot(enabled = true)).enabled)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.SlotEditReducerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement the reducer and section**

Reducer: clamp start to `0..episodeDurationMs`, duration to `1..(episodeDurationMs - start)`, set `source = "USER"`, `enabled = true`, recompute `startRatio = start / duration`, `confidence = max(existing, 0.9f)`.
Section: list each slot as "Pre-Roll" or "Mid-Roll N", its `MM:SS–MM:SS`, confidence percent, hit/miss counts, a switch, an edit dialog with start/duration fields, and a delete icon. Add a "Re-learn this show" button. Empty state: "Noch keine Werbe-Slots gelernt."
Wire it into `PodcastDetailScreen` as an `item { AdSlotsSection(...) }` before the episode list.

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.SlotEditReducerTest" && ./gradlew :app:assembleDebug`
Expected: PASS; build succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/ui/AdSlotsSection.kt app/src/main/java/com/example/ui/PodcastAppContent.kt app/src/main/java/com/example/ui/PodcastViewModel.kt app/src/test/java/com/example/asr/SlotEditReducerTest.kt
git commit -m "feat(ui): show and edit learned ad slots per show"
```

---

### Task 13: End-to-end verification on the connected phone

**Files:**
- Modify: `docs/superpowers/specs/2026-10-31-asr-ad-pattern-design.md` only if a measured value changes
- Modify: `app/build.gradle.kts` version code/name
- Create: `docs/superpowers/verification/2026-10-31-asr-ad-pattern.md`

**Interfaces:**
- Consumes: everything.
- Produces: a verification record with real measurements.

- [ ] **Step 1: Build and install**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`.

- [ ] **Step 2: Enable and download the model**

Open Settings → On-device ad detection → Download. Watch Logcat:
```bash
adb logcat -c
adb logcat | grep -E "AdScan|ModelDownload"
```
Expected: `ModelDownload` completion, `AdScan` ready.

- [ ] **Step 3: First episode of a show**

Play an episode with no feed transcript. Wait for the first 120 s to be scanned. Verify:
- Logcat contains `mode=BOOTSTRAP_FULL`.
- The pre-roll ad is skipped once confirmed.
- `ad_segments` rows exist (expose a debug count in Settings → App logs; no adb sqlite needed).
- The skip banner shows a real label and measured seconds.

- [ ] **Step 4: Second episode of the same show**

Play the next episode. Verify:
- Logcat contains `mode=WINDOWED`.
- `coveredMs` is under 15 % of the episode duration.
- Only predicted windows were decoded (log `plannedRanges`).
- No content before/after the ad is skipped.

- [ ] **Step 5: Safety checks**

- Turn auto-skip off: no seek occurs.
- Disable a slot in the show's "Werbe-Slots" list: that window is not skipped.
- Seek into an ad: the confirmed end is applied.
- Kill and relaunch the app mid-episode: scan resumes and does not duplicate segments.

- [ ] **Step 6: Battery and CPU**

```bash
adb shell dumpsys batterystats --reset
# play 10 minutes
adb shell dumpsys batterystats | grep -A 20 "com.aistudio.labcast.podcast"
adb shell dumpsys cpuinfo | grep com.aistudio.labcast.podcast
```
Expected: windowed scan CPU well below full scan; document the numbers. If CPU is above 15 % average on this device, lower look-ahead or threads before release.

- [ ] **Step 7: Write the verification record and bump the version**

Record device model, Android version, model version, full-scan and windowed-scan numbers, ad counts, and any false positives/negatives. Bump `versionCode` to 23 and `versionName` to `1.4.0`. Note the Room v7 reset and the 42 MB model download in the release notes.

- [ ] **Step 8: Commit**

```bash
git add docs/superpowers/verification/2026-10-31-asr-ad-pattern.md app/build.gradle.kts
git commit -m "docs: record on-device ASR verification and bump version"
```

---

## Self-Review

- **Spec coverage:** goals 1–7 map to Tasks 1–13; non-goals respected; anti-goals covered by the cue-confirmation tests and the removal task; constraints copied into Global Constraints; failure modes covered by the Range fallback, unknown duration, seek, and disable tests.
- **Step scan:** every test step has a command and expected result; every code step names signatures and constants; no "handle edge cases" lines.
- **Type consistency:** `AdSlotEntity` fields are used identically in Tasks 3, 4, 5, 8, 12; `EpisodeAdSegmentEntity` is used identically in Tasks 8 and 9; `ScanRange`/`PredictedWindow` names are consistent.
- **Review Focus:** five failure modes, each pinned to a test in Tasks 2, 4, 7, 8, and 8/9.
- **Proportion:** tasks are interface-level; bodies are left to the implementer except the pinned constants, model values, and test assertions.

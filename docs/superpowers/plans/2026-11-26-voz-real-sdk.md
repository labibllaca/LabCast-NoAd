# Real Voz SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace LabCast's fabricated Voz code with the real `ai.desertant:voz` SDK, scanning bounded windows of real episode audio and publishing genuine word-timed transcript and skip cues incrementally.

**Architecture:** A `VozEngine` wrapper around the SDK feeds a windowed scanner. `VozScanPlanner` picks short windows around predicted ad slots, `VozAudioDecoder` turns local or background-copied audio into 16 kHz mono samples, `VozCueDetector` turns SDK words into skip ranges, and `VozScanController` orchestrates and caches completed windows in Room. The ViewModel keeps existing UI state and consumes incremental results.

**Tech Stack:** Kotlin 2.3.21, AGP 9.1.1, Gradle 9.3.1, Compose, Room 2.7.x, OkHttp, Moshi, `ai.desertant:bom:6.0.0` + `ai.desertant:voz`, JUnit4/Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-25-voz-real-sdk-design.md`

## Global Constraints

- SDK: `implementation(platform("ai.desertant:bom:6.0.0"))` and `implementation("ai.desertant:voz")`. Version 6.0.0. API 24+, Kotlin 2.3+, arm64-v8a / x86_64.
- Exact SDK API: `Voz(context, directory: String? = null)`, `suspend transcribe(samples: FloatArray, sampleRate: Double = 16_000.0): Transcription`, `Transcription(text, words: List<Word>, duration)`, `Word(text, start: Double, end: Double)` in seconds, `isDownloaded()`, `suspend download()`, `close()`.
- No API key and no license-check bypass. Do not change `AndroidManifest.xml` permissions.
- Never invent transcript text, timestamps, or cue detections. Brand mention alone never opens a skip. A skip is auto-eligible only when a resume cue was heard.
- Never gate playback on model download or audio copying. Never overwrite `EpisodeEntity.transcript` with ASR text.
- Keep `libLiteRtClGlAccelerator.so` in the APK (no packaging excludes).
- Version bump: `versionCode = 36`, `versionName = "1.6.1"`.
- Attribution: one "Powered by Desert Ant Labs" line with a desertant.com link in Settings.
- Test snippets assume standard imports are added: `org.junit.Assert.*`, `org.junit.Test`, `kotlinx.coroutines.runBlocking`, `kotlinx.coroutines.test.runTest`, and package imports for classes in another package.

## Review Focus

- **First play with streamed audio:** a copy must happen before any window can be read, and playback must continue meanwhile — test that the controller tolerates a path supplied by a background copy and that no code waits on it in the UI path.
- **Model not downloaded:** `download()` runs once before the first window and never blocks playback — test with a fake engine that reports `isDownloaded() == false` and assert one `download()` call.
- **Overlap duplication:** a word straddling two windows must appear once with its original absolute time — test word de-duplication with a 3 s overlap.
- **No resume cue:** a start cue alone produces a marker with `isAutoSkipEligible = false`, never an auto-skip — test both the detector and the ViewModel filter.
- **Stale cache:** windows cached under an old SDK version or changed audio file must be rescanned — test that mismatched `modelVersion`/`audioIdentity` entries are ignored.

---

### Task 1: SDK dependency, Kotlin/KSP bump, version bump

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing.
- Produces: `ai.desertant.voz.Voz`, `ai.desertant.voz.Transcription`, `ai.desertant.voz.Word` on the compile classpath; Kotlin 2.3.x compiler.

- [ ] **Step 1: Bump toolchain versions**

In `gradle/libs.versions.toml` set `kotlin = "2.3.21"` and `googleDevtoolsKsp = "2.3.14"`. If the build reports a KSP/Kotlin pairing error, replace `2.3.14` with the KSP 2.3.x version the error names.

- [ ] **Step 2: Add the SDK and bump the app version**

In `app/build.gradle.kts`, inside `dependencies { ... }`, add:

```kotlin
  implementation(platform("ai.desertant:bom:6.0.0"))
  implementation("ai.desertant:voz")
```

Keep `com.microsoft.onnxruntime:onnxruntime-android:1.32.0` for now (Task 9 removes it). Set `versionCode = 36` and `versionName = "1.6.1"` in `defaultConfig`.

- [ ] **Step 3: Verify resolution and compilation**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL (Gradle resolves `ai.desertant:bom:6.0.0`, `ai.desertant:core`, `ai.desertant:voz`, and the Kotlin 2.3 plugin).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Desert Ant Voz 6.0.0 SDK and bump Kotlin to 2.3"
```

---

### Task 2: Move shared transcript models out of the fake transcriber

**Files:**
- Create: `app/src/main/java/com/example/player/VozTranscriptModels.kt`
- Modify: `app/src/main/java/com/example/player/VozTranscriber.kt`

**Interfaces:**
- Consumes: existing `WordTimestamp`, `SentenceSegment`, `SkipRange`, `VozTranscriptResult` declarations in `VozTranscriber.kt`.
- Produces: the same four data classes in package `com.example.player`, in their own file, with `VozTranscriptResult.modelName` default changed to `"Desert Ant Voz (on-device)"`.

- [ ] **Step 1: Move the declarations**

Create `VozTranscriptModels.kt` with package `com.example.player` containing the four existing data classes verbatim, except `VozTranscriptResult.modelName` defaults to `"Desert Ant Voz (on-device)"`. Delete those four declarations from `VozTranscriber.kt`, leaving the class body intact. All consumers are in the same package, so no imports change.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/player/VozTranscriptModels.kt app/src/main/java/com/example/player/VozTranscriber.kt
git commit -m "refactor: extract Voz transcript models"
```

---

### Task 3: VozEngine wrapper

**Files:**
- Create: `app/src/main/java/com/example/player/VozEngine.kt`

**Interfaces:**
- Consumes: `ai.desertant.voz.Voz`, `ai.desertant.voz.Transcription` (Task 1).
- Produces:

```kotlin
interface VozEngine : AutoCloseable {
    fun isDownloaded(): Boolean
    suspend fun download()
    suspend fun transcribe(samples: FloatArray, sampleRate: Double = 16_000.0): Transcription
}
class VozSdkEngine(context: Context) : VozEngine
```

- [ ] **Step 1: Write the wrapper**

Declare the interface exactly as above. `VozSdkEngine` holds one `Voz(context)` and delegates all four members; `close()` delegates to `Voz.close()`. Do not add configuration, vocabulary, or extra parameters.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/player/VozEngine.kt
git commit -m "feat: add testable VozEngine wrapper around the SDK"
```

---

### Task 4: Window planner

**Files:**
- Create: `app/src/main/java/com/example/player/VozScanPlanner.kt`
- Test: `app/src/test/java/com/example/player/VozScanPlannerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:

```kotlin
data class ScanWindow(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}
object VozScanPlanner {
    const val WINDOW_MS: Long = 60_000L
    const val OVERLAP_MS: Long = 3_000L
    const val OPENING_MS: Long = 180_000L
    const val POST_ROLL_MS: Long = 120_000L
    const val PAD_BEFORE_MS: Long = 30_000L
    const val PAD_AFTER_MS: Long = 60_000L
    const val MAX_WINDOWS: Int = 12
    fun plan(durationMs: Long, candidates: List<LongRange>): List<ScanWindow>
}
```

- [ ] **Step 1: Write failing tests**

```kotlin
class VozScanPlannerTest {
    @Test fun openingWindowStartsAtZero() {
        val w = VozScanPlanner.plan(3_600_000L, emptyList())
        assertEquals(0L, w.first().startMs)
        assertTrue(w.any { it.startMs == 0L && it.endMs == 60_000L })
    }
    @Test fun candidateIsPaddedAndSplitIntoWindows() {
        val w = VozScanPlanner.plan(3_600_000L, listOf(600_000L..660_000L))
        val first = w.first { it.startMs in 540_000L..575_000L }
        assertEquals(570_000L, first.startMs)
        assertTrue(w.any { it.startMs == 627_000L })
    }
    @Test fun overlappingCandidatesAreMerged() {
        val w = VozScanPlanner.plan(3_600_000L, listOf(600_000L..660_000L, 640_000L..700_000L))
        assertEquals(1, w.count { it.startMs in 570_000L..640_000L })
    }
    @Test fun windowsAreClampedToEpisodeEnd() {
        val w = VozScanPlanner.plan(30_000L, listOf(10_000L..25_000L))
        assertTrue(w.all { it.endMs <= 30_000L })
    }
    @Test fun coverageStaysUnderTwentyPercentAndCapIsTwelve() {
        val many = (0..20).map { (it * 100_000L)..(it * 100_000L + 10_000L) }
        val w = VozScanPlanner.plan(3_600_000L, many)
        assertTrue(w.size <= VozScanPlanner.MAX_WINDOWS)
        assertTrue(w.sumOf { it.durationMs } <= 3_600_000L * 0.2)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.VozScanPlannerTest --no-daemon`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `plan`**

Algorithm: build ranges = opening `0..min(OPENING_MS, durationMs)`, each candidate padded by `PAD_BEFORE_MS`/`PAD_AFTER_MS` and clamped to duration, and post-roll `max(0, durationMs - POST_ROLL_MS)..durationMs`. Merge ranges that overlap or touch. Split each merged range into windows of `WINDOW_MS` stepping `WINDOW_MS - OVERLAP_MS`, truncating the last window to the range end. Sort by `startMs`, take the first `MAX_WINDOWS`, then drop from the end until total `durationMs <= 20 %` of episode duration. Always keep at least the first window.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.VozScanPlannerTest --no-daemon`
Expected: PASS (all five).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/player/VozScanPlanner.kt app/src/test/java/com/example/player/VozScanPlannerTest.kt
git commit -m "feat: bounded window planner for Voz scans"
```

---

### Task 5: Cue detection from real words

**Files:**
- Create: `app/src/main/java/com/example/player/VozCueDetector.kt`
- Test: `app/src/test/java/com/example/player/VozCueDetectorTest.kt`

**Interfaces:**
- Consumes: `SkipRange` (Task 2), `PodcastKeywordSpotter.SPONSOR_RESUME_PATTERNS` (existing `companion object` list, `PodcastKeywordSpotter.kt:60`).
- Produces:

```kotlin
data class TimedWord(val text: String, val startMs: Long, val endMs: Long)
object VozCueDetector {
    val START_PHRASES: List<String>       // copied from fake VozTranscriber.SPONSOR_TRIGGER_PHRASES
    val BRANDS: List<String>              // copied from fake VozTranscriber.SPONSOR_BRANDS
    fun detect(words: List<TimedWord>): List<SkipRange>
}
```

- [ ] **Step 1: Write failing tests**

```kotlin
class VozCueDetectorTest {
    private fun words(vararg text: String) = text.mapIndexed { i, t ->
        TimedWord(t, i * 1_000L, i * 1_000L + 900L)
    }
    @Test fun startAndResumeCueMakeAutoEligibleRange() {
        val r = VozCueDetector.detect(words("Sponsored by", "NordVPN", "and now back to the show"))
        assertEquals(1, r.size)
        assertTrue(r[0].isAutoSkipEligible)
        assertEquals(0L, r[0].startMs)
        assertEquals(2_000L, r[0].endMs)
    }
    @Test fun startCueWithoutResumeIsMarkerOnly() {
        val r = VozCueDetector.detect(words("Sponsored by", "NordVPN", "a great episode"))
        assertEquals(1, r.size)
        assertFalse(r[0].isAutoSkipEligible)
    }
    @Test fun brandMentionAloneDoesNotOpenRange() {
        assertTrue(VozCueDetector.detect(words("I use NordVPN", "every day")).isEmpty())
    }
    @Test fun noCuesProduceNoRanges() {
        assertTrue(VozCueDetector.detect(words("Welcome to the show", "today")).isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.VozCueDetectorTest --no-daemon`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `detect`**

Lowercase with `Locale.ROOT`. Copy the trigger-phrase and brand lists verbatim from the fake `VozTranscriber`. Iterate words in order; open a candidate only on a `START_PHRASES` hit or a `BRANDS` hit **combined with** promotional language (any trigger phrase or words "promo", "discount", "offer", "free trial"). On a `PodcastKeywordSpotter.SPONSOR_RESUME_PATTERNS` hit, close the open candidate at the last resume word's `endMs` and emit a `SkipRange` with `id = "voz_cue_${startMs}_$endMs"`, `confidence = 0.95f`, `isAutoSkipEligible = true`. At end of input, emit an still-open candidate as a marker with `endMs = last word endMs` and `isAutoSkipEligible = false`. Never derive an end from elapsed time.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.VozCueDetectorTest --no-daemon`
Expected: PASS (all four).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/player/VozCueDetector.kt app/src/test/java/com/example/player/VozCueDetectorTest.kt
git commit -m "feat: detect ad cues from real Voz words"
```

---

### Task 6: PCM conversion and window decoding

**Files:**
- Create: `app/src/main/java/com/example/player/PcmConverter.kt`
- Create: `app/src/main/java/com/example/player/VozAudioDecoder.kt`
- Modify: `app/src/main/java/com/example/util/EpisodeDownloader.kt:23-36`
- Test: `app/src/test/java/com/example/player/PcmConverterTest.kt`

**Interfaces:**
- Consumes: `android.media.MediaExtractor`, `MediaCodec`.
- Produces:

```kotlin
object PcmConverter {
    fun toMono16k(pcm: ShortArray, channelCount: Int, sourceRate: Int): FloatArray
}
interface AudioWindowReader {
    fun readWindow(path: String, startMs: Long, lengthMs: Long): FloatArray
    fun audioIdentity(path: String): String
}
class VozAudioDecoder : AudioWindowReader
```

- [ ] **Step 1: Write failing converter tests**

```kotlin
class PcmConverterTest {
    @Test fun oneSecondOf48kMonoBecomes16kSamples() {
        assertEquals(16_000, PcmConverter.toMono16k(ShortArray(48_000), 1, 48_000).size)
    }
    @Test fun stereoIsDownmixed() {
        val pcm = shortArrayOf(1_000, 3_000, 1_000, 3_000)
        val out = PcmConverter.toMono16k(pcm, 2, 16_000)
        assertEquals(2_000f / 32_768f, out[0], 0.0001f)
    }
    @Test fun samplesStayInFloatRange() {
        val out = PcmConverter.toMono16k(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), 1, 16_000)
        assertTrue(out.all { it in -1f..1f })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.PcmConverterTest --no-daemon`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `PcmConverter.toMono16k`**

Average channel frames into mono, convert to float by dividing by `32768f`, then linearly resample to 16 kHz with ratio `sourceRate / 16_000.0`; output length is `floor(frameCount * 16_000.0 / sourceRate)` rounded to nearest.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.PcmConverterTest --no-daemon`
Expected: PASS (all three).

- [ ] **Step 5: Add an ASR destination option to `EpisodeDownloader`**

Change `downloadToFile` to `suspend fun downloadToFile(context: Context, url: String, episodeId: String, onProgress: (Float) -> Unit, destinationDirName: String = "podcasts"): String?`. Use `destinationDirName` in place of the literal `"podcasts"` when building `dir`; keep the filename `${cleanId}.mp3` and all resumable logic unchanged. Existing callers stay valid.

- [ ] **Step 6: Implement `VozAudioDecoder`**

`readWindow`: open `MediaExtractor()` with `setDataSource(path)`, select the audio track, configure `MediaCodec` for PCM output, `seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)`, drain decoded `ShortArray` until output presentation time reaches `(startMs + lengthMs) * 1_000L` or EOS, feed all decoded shorts to `PcmConverter.toMono16k(..., channelCount, sampleRate)` from the output `MediaFormat`, and return the FloatArray for the window. Release extractor, codec, and buffers in `finally`. `audioIdentity(path)` returns `"$length:${lastModified}"` from `File(path)`.

- [ ] **Step 7: Verify compilation and commit**

Run: `./gradlew :app:compileDebugKotlin --no-daemon` — expected BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/example/player/PcmConverter.kt app/src/main/java/com/example/player/VozAudioDecoder.kt app/src/main/java/com/example/util/EpisodeDownloader.kt app/src/test/java/com/example/player/PcmConverterTest.kt
git commit -m "feat: decode 16 kHz mono windows from local audio"
```

---

### Task 7: Room cache for completed windows

**Files:**
- Create: `app/src/main/java/com/example/data/AsrWindowEntity.kt`
- Create: `app/src/main/java/com/example/data/AsrWindowDao.kt`
- Create: `app/src/main/java/com/example/data/AsrWindowStore.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDatabase.kt`
- Test: `app/src/test/java/com/example/data/AsrWindowStoreTest.kt`

**Interfaces:**
- Consumes: Room, Moshi (both already dependencies).
- Produces:

```kotlin
@Entity(tableName = "asr_windows", primaryKeys = ["episodeId", "startMs"])
data class AsrWindowEntity(
    val episodeId: String, val startMs: Long, val endMs: Long,
    val wordsJson: String, val modelVersion: String,
    val audioIdentity: String, val updatedAt: Long = System.currentTimeMillis()
)
@Dao interface AsrWindowDao {
    @Query("SELECT * FROM asr_windows WHERE episodeId = :episodeId")
    suspend fun windows(episodeId: String): List<AsrWindowEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(windows: List<AsrWindowEntity>)
}
interface AsrWindowStore {
    suspend fun load(episodeId: String): List<AsrWindowEntity>
    suspend fun save(windows: List<AsrWindowEntity>)
}
class RoomAsrWindowStore(private val dao: AsrWindowDao) : AsrWindowStore
```

- [ ] **Step 1: Write failing store test**

```kotlin
class AsrWindowStoreTest {
    @Test fun saveThenLoadRoundTrips() = runBlocking {
        val dao = FakeAsrWindowDao()
        val store = RoomAsrWindowStore(dao)
        store.save(listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "6.0.0", "123:456")))
        assertEquals(1, store.load("e1").size)
        assertEquals(60_000L, store.load("e1")[0].endMs)
    }
}
```

Define `FakeAsrWindowDao` in the test file: implement `AsrWindowDao` with an in-memory `MutableList<AsrWindowEntity>`; `windows` filters by `episodeId`, `upsert` replaces entries with the same `(episodeId, startMs)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.data.AsrWindowStoreTest --no-daemon`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement entity, DAO, store, and migration**

Add `@Database(entities = [..., AsrWindowEntity::class], version = 9, ...)` to `PodcastDatabase`, `abstract fun asrWindowDao(): AsrWindowDao`, and:

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS asr_windows (" +
            "episodeId TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, " +
            "wordsJson TEXT NOT NULL, modelVersion TEXT NOT NULL, audioIdentity TEXT NOT NULL, " +
            "updatedAt INTEGER NOT NULL, PRIMARY KEY(episodeId, startMs))")
    }
}
```

Register `MIGRATION_8_9` in `addMigrations(...)`. `RoomAsrWindowStore` just delegates to the DAO.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.data.AsrWindowStoreTest --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/AsrWindowEntity.kt app/src/main/java/com/example/data/AsrWindowDao.kt app/src/main/java/com/example/data/AsrWindowStore.kt app/src/main/java/com/example/data/PodcastDatabase.kt app/src/test/java/com/example/data/AsrWindowStoreTest.kt
git commit -m "feat: cache completed Voz windows in Room"
```

---

### Task 8: VozScanController

**Files:**
- Create: `app/src/main/java/com/example/player/VozScanController.kt`
- Test: `app/src/test/java/com/example/player/VozScanControllerTest.kt`

**Interfaces:**
- Consumes: `VozEngine` (Task 3), `VozScanPlanner`/`ScanWindow` (Task 4), `VozCueDetector`/`TimedWord` (Task 5), `AudioWindowReader` (Task 6), `AsrWindowStore` (Task 7), `PodcastAdPatternEngine`, `PodcastKeywordSpotter`, `ManualAdRangeCodec`, `EpisodeDownloader`, `VozTranscriptResult`/`SentenceSegment` (Task 2).
- Produces:

```kotlin
data class ScanProgress(val completed: Int, val total: Int, val wordCount: Int, val cueCount: Int, val text: String = "")
class VozScanController(
    private val context: Context,
    private val store: AsrWindowStore,
    private val engineFactory: (Context) -> VozEngine = { VozSdkEngine(it) },
    private val reader: AudioWindowReader = VozAudioDecoder(),
    private val pathResolver: suspend (EpisodeEntity) -> String? = { episode -> /* local path or background ASR copy */ null }
) {
    suspend fun scan(episode: EpisodeEntity, siblings: List<EpisodeEntity>, onProgress: (ScanProgress) -> Unit): VozTranscriptResult
}
```

- [ ] **Step 1: Write failing tests with fakes**

In `VozScanControllerTest.kt`, define `FakeEngine(var downloaded: Boolean = true)` recording `downloadCalls` and `transcribedSizes`, returning `Transcription("Sponsored by NordVPN and now back to the show", listOf(Word("Sponsored by", 1.0, 1.9), Word("NordVPN", 2.0, 2.9), Word("and now back to the show", 3.0, 4.0)), 5.0)`. Define `FakeReader` returning `FloatArray(16_000)` and `audioIdentity = "100:200"`. Define `FakeStore` holding a mutable list. `Context` may be `ApplicationProvider.getApplicationContext()` (Robolectric is already a test dependency). Define a `controller(engine, store = FakeStore(), reader = FakeReader())` helper that passes `pathResolver = { "/tmp/e1.mp3" }`, so tests never touch the network. The fake engine must also record absolute word-start offsets in `engineOffsetsRecorded` for the offset test and requested window starts in `transcribedWindowStarts` for the cache test.

```kotlin
@Test fun downloadsModelOnce() = runBlocking {
    val engine = FakeEngine(downloaded = false)
    controller(engine).scan(episode, emptyList()) {}
    assertEquals(1, engine.downloadCalls)
}
@Test fun wordTimesAreOffsetByWindowStart() = runBlocking {
    val windows = controller(FakeEngine()).scan(episode, emptyList()) {}
    assertTrue(windows.sentences.isNotEmpty())
    // the first window starts at 0; a later window's words must carry the window offset
    assertTrue(engineOffsetsRecorded.any { it >= 570_000L })
}
@Test fun coveredWindowsAreNotRescanned() = runBlocking {
    val store = FakeStore(listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "6.0.0", "100:200")))
    val engine = FakeEngine()
    controller(engine, store).scan(episode, emptyList()) {}
    assertFalse(engine.transcribedWindowStarts.contains(0L))
    assertTrue(engine.transcribedWindowStarts.isNotEmpty())
}
@Test fun staleCachedWindowsAreRescanned() = runBlocking {
    val store = FakeStore(listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "5.0.0", "100:200")))
    val engine = FakeEngine()
    controller(engine, store).scan(episode, emptyList()) {}
    assertTrue(engine.transcribedSizes.isNotEmpty())
}
```

`episode` is an `EpisodeEntity` with `id = "e1"`, `durationSeconds = 3_600L`, `isDownloaded = true`, `downloadLocalPath = "/tmp/e1.mp3"` (the fake reader ignores the file). Record absolute word starts in the fake engine so `engineOffsetsRecorded` can be asserted.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.VozScanControllerTest --no-daemon`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `scan`**

Steps, in order: (1) build candidates from `PodcastAdPatternEngine().learnShowAdPattern` + `predictEpisodeAdSkips`, `PodcastKeywordSpotter().parseAdIntervalsFromTranscript(episode.transcript)`, and `ManualAdRangeCodec.decode(episode.manualAdRanges)`; (2) `windows = VozScanPlanner.plan(episode.durationSeconds * 1_000L, candidates)`; (3) resolve audio path via `pathResolver(episode)` — its production default uses `downloadLocalPath` when `isDownloaded` and `EpisodeDownloader.isValidDownloadedFile`, otherwise `EpisodeDownloader.downloadToFile(context, episode.audioUrl, episode.id, {}, "asr-cache")`; return an empty `VozTranscriptResult` if no path; (4) `audioIdentity = reader.audioIdentity(path)`; (5) load store windows and ignore entries whose `modelVersion != "6.0.0"` or `audioIdentity` differs; (6) with `engineFactory(context).use { ... }`, call `download()` once when `!isDownloaded()`; (7) for each uncovered window, `engine.transcribe(reader.readWindow(path, w.startMs, w.durationMs), 16_000.0)`, offset each word by `w.startMs / 1000.0` into `TimedWord`, run `VozCueDetector.detect`, group words into `SentenceSegment`s on pauses > 600 ms or 12 words with speaker `"Speaker"`, save an `AsrWindowEntity` with Moshi-serialized `TimedWord`s, and call `onProgress(ScanProgress(index + 1, windows.size, totalWords, totalCues, accumulatedText))`; (8) return `VozTranscriptResult(episode.id, sentences = ..., skipRanges = ...)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.player.VozScanControllerTest --no-daemon`
Expected: PASS (all four).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/player/VozScanController.kt app/src/test/java/com/example/player/VozScanControllerTest.kt
git commit -m "feat: incremental windowed Voz scan controller"
```

---

### Task 9: Wire the ViewModel, remove fakes, add attribution

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt:1510-1542`, `:362-363`, `:2302-2305`, `:2334`
- Modify: `app/src/main/java/com/example/player/PodcastKeywordSpotter.kt:11-19`
- Modify: `app/src/main/java/com/example/ui/SettingsScreen.kt`
- Delete: `app/src/main/java/com/example/player/VozTranscriber.kt`, `app/src/main/java/com/example/player/VozModelManager.kt`
- Modify: `app/build.gradle.kts` (remove `com.microsoft.onnxruntime:onnxruntime-android:1.32.0`)
- Modify: `app/src/test/java/com/example/data/MockDataRemovalTest.kt:43-61`
- Test: `app/src/test/java/com/example/ui/AutoSkipEligibilityTest.kt`

**Interfaces:**
- Consumes: `VozScanController.scan` (Task 8), `ScanProgress`, `RoomAsrWindowStore` (Task 7), `PodcastDatabase.asrWindowDao()` (Task 7), `SkipRange.isAutoSkipEligible` (Task 2).
- Produces: real skip ranges merged into `detectedTranscriptAds`; marker-only ranges never auto-skip.

- [ ] **Step 1: Write failing auto-skip filter test**

```kotlin
class AutoSkipEligibilityTest {
    @Test fun markerOnlyVozRangeIsNotAutoSkipped() {
        val marker = AdSkipInterval("voz_marker", 0L, 60_000L, "Sponsored by", "", 0.95f, "Desert Ant Voz STT", isAutoSkipEligible = false)
        val skip = AutoSkipSelection.select(listOf(marker), 30_000L)
        assertNull(skip)
    }
    @Test fun eligibleVozRangeIsAutoSkipped() {
        val skip = AdSkipInterval("voz_cue", 0L, 60_000L, "Sponsored by", "back to the show", 0.95f, "Desert Ant Voz STT")
        assertNotNull(AutoSkipSelection.select(listOf(skip), 30_000L))
    }
}
```

Create `object AutoSkipSelection { fun select(ads: List<AdSkipInterval>, positionMs: Long): AdSkipInterval? }` in `app/src/main/java/com/example/player/AutoSkipSelection.kt` with the same first-match rule used at `PodcastViewModel.kt:2303-2305`, restricted to `ad.isAutoSkipEligible`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.ui.AutoSkipEligibilityTest --no-daemon`
Expected: FAIL — unresolved `isAutoSkipEligible`.

- [ ] **Step 3: Add the eligibility field and use the selector**

Add `val isAutoSkipEligible: Boolean = true` as the last field of `AdSkipInterval` in `PodcastKeywordSpotter.kt`. Replace the `activeAdInterval = transcriptAds.firstOrNull { ... }` expression at `PodcastViewModel.kt:2303` with `AutoSkipSelection.select(transcriptAds, positionMs)`, and change the `stillInAny` check at `:2334` to `transcriptAds.any { it.isAutoSkipEligible && positionMs >= it.startMs && positionMs < it.endMs }`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.ui.AutoSkipEligibilityTest --no-daemon`
Expected: PASS.

- [ ] **Step 5: Wire `VozScanController` into the metadata refresh**

Add `private val _vozTranscript = MutableStateFlow("")` near `_detectedTranscriptAds` in `PodcastViewModel`. Replace the fake block at `PodcastViewModel.kt:1510-1542` with: start a `VozScanController(application, RoomAsrWindowStore(PodcastDatabase.getDatabase(application).asrWindowDao()))`, call `scan(currentEp, siblingEpisodes) { progress -> withContext(Dispatchers.Main) { _isSttScanning.value = true; _vozTranscript.value = progress.text; repository.addSyncLog("Voz", "Fenster ${progress.completed}/${progress.total}: ${progress.wordCount} Wörter, ${progress.cueCount} Cues.") } }`, map `vozResult.skipRanges` into `AdSkipInterval`s with `source = "Desert Ant Voz STT"`, `resumeWord` from the detected resume cue (or `"—"` for markers), and `isAutoSkipEligible = skip.isAutoSkipEligible`. Keep the existing merge with manual and transcript intervals. Remove the write of serialized fabricated sentences into `currentEp.transcript` and replace the `[VOZ CONSOLE]` / "Voz AI Engine" log text with real status text.

- [ ] **Step 6: Delete the fake engines and their dependency**

Delete `VozTranscriber.kt` and `VozModelManager.kt`. Remove `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")` from `app/build.gradle.kts`. Update `MockDataRemovalTest` so `episodeWithoutTranscriptProducesNoSynthesizedVozSegments` no longer instantiates `VozTranscriber`; instead assert that `VozScanPlanner.plan(600_000L, emptyList())` produces windows but `VozCueDetector.detect(emptyList())` produces no skips, keeping the original "no fabricated segments" intent.

- [ ] **Step 7: Add attribution and model status in Settings**

At the end of `SettingsScreen`'s content column, add one `Text` reading `Powered by Desert Ant Labs` with `style = TextStyle(textDecoration = TextDecoration.Underline)` and `AnnotatedString`/`LinkAnnotation.Url("https://desertant.com")` (or the project's existing hyperlink helper). Add a second small `Text` showing `Voz model: ${if (vozEngine.isDownloaded()) "bereit" else "nicht heruntergeladen"}` from a new ViewModel StateFlow updated by the controller; do not trigger a download from Settings.

- [ ] **Step 8: Full verification**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, all tests pass.

Run: `grep -rn "VozTranscriber\|VozModelManager\|onnxruntime\|VOZ CONSOLE" app/src app/build.gradle.kts || true`
Expected: no matches.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: use real Voz SDK for incremental on-device ad detection"
```

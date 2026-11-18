# Manual Ad Marking, Robust Cue Matching, Zipformer Upgrade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a two-press manual ad marker to the player card, make ad-cue matching survive ASR word errors, and upgrade the on-device model to the 2023-06-26 streaming Zipformer with hotword biasing.

**Architecture:** All work lands on `feat/asr-ad-pattern`. Pure logic (text matching, manual mark state, ad selection, merge) lives in new small files with JVM tests; the ViewModel and Compose card only wire it up. The model swap is a registry change plus hotwords passed per recognizer stream; no Room migration.

**Tech Stack:** Kotlin 2.0.21, AGP 9.1.1, compileSdk 36, minSdk 24, Room 2.7.0, sherpa-onnx 2.0.1 (JitPack), JUnit 4, Jetpack Compose Material3.

**Spec:** `docs/superpowers/specs/2026-11-18-manual-ad-mark-cue-fuzzy-zipformer-upgrade-design.md`

## Global Constraints

- Branch: `feat/asr-ad-pattern`. `main` has unrelated WIP; never check out or commit on it. Use a worktree of that branch.
- No Room schema change. Manual ads reuse `episode_ad_segments` with `source = "USER"`.
- `AdSource.USER` already exists; `EpisodeAdSegmentEntity.confirmed` already gates skipping.
- Minimum manual mark length: `5_000 ms`. Maximum sane length: 15 minutes (log only).
- New player strings are English, hardcoded inline (the card is English).
- Model dir name: `zipformer-en-2023-06-26`; registry total `73_298_636` bytes; 5 pinned files.
- Hotwords: uppercase, deduped, max 100 lines, plain phrases (no per-line scores).
- Fuzzy thresholds: `0.84` multi-word cues, `0.90` single-word brands; brand phonetic rescue needs promo language.
- Every task ends with `./gradlew :app:testDebugUnitTest --tests "<class>"` green and a commit.
- Final gate: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`.

## Review Focus

1. A cue phrase spoken in normal conversation ("thanks to our sponsor" as banter) must not open an ad and skip content — pinned in Task 2.
2. A manual mark whose end precedes its start (user seeked back) or exceeds the episode must never be stored — pinned in Task 5.
3. A background rescan after a manual mark must not overwrite or duplicate it — pinned in Task 6.
4. Switching episodes while armed, or with ASR off, must not skip with stale ads from the previous episode — pinned in Task 6.
5. A failed model download after the version bump must leave the app usable with no detection, never crash or load half a model — pinned in Task 4.

---

### Task 1: Text normalization and fuzzy phrase matching

**Files:**
- Create: `app/src/main/java/com/example/asr/TextMatch.kt`
- Test: `app/src/test/java/com/example/asr/TextMatchTest.kt`

**Interfaces:**
- Produces:
  - `object TextMatch`
  - `fun normalize(text: String): String`
  - `fun tokens(text: String): List<String>`
  - `fun fuzzyContains(haystack: String, needle: String, threshold: Double = 0.84): Boolean`
  - `fun phoneticKey(word: String): String`
  - `fun brandMatches(haystack: String, brand: String): Boolean`

- [ ] **Step 1: Write the failing tests**

```kotlin
class TextMatchTest {
    @Test fun `normalizes digits percent ampersand and glued numbers`() {
        assertEquals(
            "use code save fifty for fifty percent off at one eight zero zero five five five zero one zero zero",
            TextMatch.normalize("Use code SAVE50 for 50% off at 1-800-555-0100!"),
        )
    }

    @Test fun `tokens fold buy to by`() {
        assertEquals(listOf("brought", "to", "you", "by"), TextMatch.tokens("Brought to you buy"))
    }

    @Test fun `fuzzy contains tolerates one wrong word`() {
        assertTrue(
            TextMatch.fuzzyContains(
                "this episode is brought to you buy better help",
                "brought to you by",
            )
        )
    }

    @Test fun `fuzzy contains rejects an unrelated phrase`() {
        assertFalse(TextMatch.fuzzyContains("and now back to our chat", "back to the show"))
    }

    @Test fun `phonetic key joins homophones`() {
        assertEquals(TextMatch.phoneticKey("phone"), TextMatch.phoneticKey("fone"))
        assertEquals(TextMatch.phoneticKey("right"), TextMatch.phoneticKey("write"))
        assertNotEquals(TextMatch.phoneticKey("help"), TextMatch.phoneticKey("health"))
    }

    @Test fun `brand matches across spacing and letter digits`() {
        assertTrue(TextMatch.brandMatches("square space dot com slash show", "Squarespace"))
        assertTrue(TextMatch.brandMatches("i drink ag one every morning", "AG1"))
        assertFalse(TextMatch.brandMatches("i drink water every morning", "AG1"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.TextMatchTest"`
Expected: FAIL — `TextMatch` unresolved.

- [ ] **Step 3: Implement `TextMatch.kt`**

Rules the tests do not determine:
- Lowercase with `Locale.ROOT`; `&` → ` and `, `+` → ` plus `, `%` → ` percent `, all other punctuation to spaces.
- Remove hyphens that sit between two digits before splitting, so `1-800-555-0100` becomes the run `18005550100`.
- Split letter↔digit boundaries (`SAVE50` → `save 50`, `AG1` → `ag 1`).
- Digit runs of 1–3 digits → cardinal words (`50` → `fifty`); ≥ 4 digits → digit words (`18005550100` → `one eight zero zero five five five zero one zero zero`).
- `tokens` = `normalize` split on spaces, then canonical map: `buy`/`bye` → `by`, `kode` → `code`, `sight` → `site`.
- `fuzzyContains`: slide windows over `tokens(haystack)` of length `needleTokens.size - 1 .. + 1`, score `1 - levenshtein(window, needle) / max(len)`, return true if any window ≥ `threshold`.
- `phoneticKey`: uppercase letters only, drop a leading `WR`'s `W`, drop vowels except a leading one, then map `PH→F, F→F, V→F, TH→T, GH→(drop), C→K, Q→K, X→KS, Z→S, DG→J, G→K`; collapse repeats. Empty input returns `""`.
- `brandMatches`: true when any token window equals the normalized brand tokens, or every brand token has an equal `phoneticKey` to the window token, or `fuzzyContains(haystack, brand, 0.70)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.TextMatchTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/asr/TextMatch.kt app/src/test/java/com/example/asr/TextMatchTest.kt
git commit -m "feat(asr): normalize and fuzzy-match recognized cue text"
```

---

### Task 2: Robust cue detection

**Files:**
- Modify: `app/src/main/java/com/example/asr/AdCueDetector.kt`
- Test: `app/src/test/java/com/example/asr/AdCueDetectorTest.kt`

**Interfaces:**
- Consumes: `TextMatch.normalize`, `TextMatch.fuzzyContains`, `TextMatch.brandMatches` from Task 1.
- Produces: unchanged `AdCueDetector.adsFromSegments(segments, maxAdMs): List<DetectedAd>`.

- [ ] **Step 1: Add failing tests**

```kotlin
@Test fun `a misheard start cue still opens an ad`() {
    val ads = detector.adsFromSegments(
        listOf(
            AsrSegment(20_000, 24_000, "This episode is brought to you buy Better Help.", 0.8f),
            AsrSegment(80_000, 84_000, "And now back to the show.", 0.9f),
        )
    )
    assertEquals(1, ads.size)
    assertTrue(ads[0].confirmed)
    assertEquals(20_000L, ads[0].startMs)
}

@Test fun `a spoken phone number with promo language opens an ad`() {
    val ads = detector.adsFromSegments(
        listOf(
            AsrSegment(30_000, 35_000, "Call one eight hundred five five five zero one zero and use code SAVE.", 0.8f),
            AsrSegment(90_000, 94_000, "Back to our conversation.", 0.9f),
        )
    )
    assertEquals(1, ads.size)
    assertTrue(ads[0].confirmed)
}

@Test fun `a fuzzy cue in the first eight seconds is ignored`() {
    val ads = detector.adsFromSegments(
        listOf(AsrSegment(3_000, 6_000, "so this episode is brought to you buy us.", 0.7f))
    )
    assertTrue(ads.isEmpty())
}

@Test fun `a resume phrase does not close a young ad`() {
    val ads = detector.adsFromSegments(
        listOf(
            AsrSegment(10_000, 14_000, "This episode is sponsored by AG1.", 0.9f),
            AsrSegment(20_000, 23_000, "So anyway.", 0.9f),
        )
    )
    assertEquals(1, ads.size)
    assertFalse(ads[0].confirmed)
}

@Test fun `the canonical cue is recorded`() {
    val ads = detector.adsFromSegments(
        listOf(
            AsrSegment(20_000, 24_000, "brought to you buy Squarespace", 0.8f),
            AsrSegment(80_000, 84_000, "back to the show", 0.9f),
        )
    )
    assertEquals("brought to you by", ads[0].startCue)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdCueDetectorTest"`
Expected: FAIL on the new cases.

- [ ] **Step 3: Update `AdCueDetector`**

- Build `combined` from `TextMatch.normalize(previous.text) + " " + TextMatch.normalize(current.text)`.
- Phrase match helper `matches(haystack, phrase)`: exact substring, else `TextMatch.fuzzyContains(haystack, phrase, 0.84)`.
- Start cue: `startPhrases.firstOrNull { matches(combined, it) }`; a match is "fuzzy only" when the normalized `phrase` is not a substring.
- Guard: when `segment.endMs <= 8_000` and the start match was fuzzy only, ignore it.
- Brand: `brands.firstOrNull { TextMatch.brandMatches(combined, it) }`; promo phrases use `matches`.
- End cue: `matches` against `endPhrases`, plus the resume phrases `"so anyway"`, `"where were we"`, `"back to our conversation"` only when `segment.endMs - adStartMs >= 20_000`.
- `startCue`/`endCue` store the matched item from the phrase list (canonical), never the raw text.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdCueDetectorTest"`
Expected: PASS, including the five pre-existing tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/asr/AdCueDetector.kt app/src/test/java/com/example/asr/AdCueDetectorTest.kt
git commit -m "feat(asr): tolerate misheard ad cues without loosening boundaries"
```

---

### Task 3: Single-segment store access for edit and delete

**Files:**
- Modify: `app/src/main/java/com/example/data/AdDao.kt`
- Modify: `app/src/main/java/com/example/data/AdScanStore.kt`
- Modify: `app/src/main/java/com/example/data/RoomAdScanStore.kt`
- Modify: `app/src/test/java/com/example/asr/Fakes.kt`
- Test: `app/src/test/java/com/example/data/AdDaoTest.kt`

**Interfaces:**
- Produces on `AdScanStore` (and `RoomAdScanStore`, `FakeStore`):
  - `suspend fun adSegment(id: String): EpisodeAdSegmentEntity?`
  - `suspend fun deleteAdSegment(id: String)`
- Produces on `AdDao`: the same two queries (`@Query("SELECT * FROM episode_ad_segments WHERE id = :id")`, `@Query("DELETE FROM episode_ad_segments WHERE id = :id")`).

- [ ] **Step 1: Add a failing DAO test**

Follow the setup already in `AdDaoTest.kt` (in-memory Room). Insert one segment via `dao.upsertAdSegments(...)`, then:

```kotlin
@Test fun `stores and deletes a single ad segment by id`() = runBlocking {
    dao.upsertAdSegments(listOf(segment(id = "e:1000:2000")))
    assertEquals("e:1000:2000", dao.adSegment("e:1000:2000")?.id)
    dao.deleteAdSegment("e:1000:2000")
    assertNull(dao.adSegment("e:1000:2000"))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDaoTest"`
Expected: FAIL — `adSegment` unresolved.

- [ ] **Step 3: Implement the DAO, interface, and fake**

Add the two queries to `AdDao`, the two methods to `AdScanStore` and `RoomAdScanStore` (delegating to the DAO), and to `FakeStore` (backed by its `adsByEpisode` map, replacing the list entry).

- [ ] **Step 4: Run the DAO test and the controller tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDaoTest" --tests "com.example.asr.AdScanControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/AdDao.kt app/src/main/java/com/example/data/AdScanStore.kt app/src/main/java/com/example/data/RoomAdScanStore.kt app/src/test/java/com/example/asr/Fakes.kt app/src/test/java/com/example/data/AdDaoTest.kt
git commit -m "feat(data): read and delete one ad segment by id"
```

---

### Task 4: Zipformer upgrade, hotwords, and legacy cleanup

**Files:**
- Modify: `app/src/main/java/com/example/asr/AsrModelRegistry.kt`
- Create: `app/src/main/java/com/example/asr/Hotwords.kt`
- Modify: `app/src/main/java/com/example/asr/SherpaSpeechRecognizer.kt`
- Modify: `app/src/main/java/com/example/asr/ModelDownloadManager.kt`
- Modify: `app/src/main/java/com/example/asr/AdScanController.kt` (the `MODEL_VERSION` constant)
- Modify: `app/src/main/java/com/example/ui/AsrSettingsSection.kt` (size copy)
- Test: `app/src/test/java/com/example/asr/HotwordsTest.kt`, `AsrModelRegistryTest.kt`, `ModelDownloadManagerTest.kt`

**Interfaces:**
- Produces: `AsrModelRegistry.modelDirName = "zipformer-en-2023-06-26"`, `totalBytes = 73_298_636L`, 5 files.
- Produces: `object Hotwords { fun build(brands: List<String>, cues: List<String>): String }`.
- Produces: `SpeechRecognizerEngine.startStream(startMs: Long = 0L)` unchanged; `SherpaSpeechRecognizer(modelDir, numThreads = 2, hotwords: String = "")`.
- Produces: `ModelDownloadManager.deleteLegacy()`.

- [ ] **Step 1: Pin the new model files**

```bash
mkdir -p /tmp/zf2626 && cd /tmp/zf2626
BASE=https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main
for f in encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx \
         decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx \
         joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx tokens.txt; do
  curl -L -O "$BASE/$f"; done
shasum -a 256 *.onnx *.txt
ls -l
```

Expected sizes: `71_083_163`, `1_307_236`, `259_335`, `5_048` bytes. Record the four SHA-256 values.

- [ ] **Step 2: Write the failing registry and hotword tests**

```kotlin
// AsrModelRegistryTest
@Test fun `pins the 2023-06-26 model`() {
    assertEquals("zipformer-en-2023-06-26", AsrModelRegistry.modelDirName)
    assertEquals(5, AsrModelRegistry.files.size)
    assertEquals(73_298_636L, AsrModelRegistry.totalBytes)
    assertTrue(AsrModelRegistry.files.any { it.name.contains("chunk-16-left-128.int8.onnx") })
}

// HotwordsTest
@Test fun `uppercases dedupes and caps at one hundred lines`() {
    val lines = Hotwords.build(listOf("AG1", "ag1", "BetterHelp"), emptyList()).lines()
    assertEquals(listOf("AG1", "BETTERHELP"), lines)
    assertEquals(100, Hotwords.build((1..150).map { "brand$it" }, emptyList()).lines().size)
}

@Test fun `includes cue phrases and skips blanks`() {
    val out = Hotwords.build(emptyList(), listOf("promo code", " "))
    assertEquals("PROMO CODE", out)
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AsrModelRegistryTest" --tests "com.example.asr.HotwordsTest"`
Expected: FAIL.

- [ ] **Step 4: Implement registry, hotwords, recognizer, and cleanup**

- `AsrModelRegistry`: `modelDirName = "zipformer-en-2023-06-26"`, new base URL, the four files with the pinned SHAs from Step 1, plus the existing `silero_vad.onnx` entry unchanged.
- `Hotwords.build`: map `trim().uppercase(Locale.ROOT)`, drop blanks, `distinct()`, `take(100)`, join with `"\n"`.
- `SherpaSpeechRecognizer`: take file names from `AsrModelRegistry.files` by suffix (`encoder`, `decoder`, `joiner`, `tokens`), set `hotwordsScore = 1.6f` in `OnlineRecognizerConfig`, and call `recognizer.createStream(hotwords = hotwords)` in `startStream`. Wrap recognizer creation in `try/catch`: on exception, retry once with `hotwords = ""` and log `"Hotwords rejected; continuing without"`.
- `ModelDownloadManager.deleteLegacy()`: delete every directory under `rootDir` whose name starts with `"zipformer-"` and is not `modelDirName`.
- `AdScanController`: `private const val MODEL_VERSION = AsrModelRegistry.modelDirName` (import the registry).
- `AsrSettingsSection`: replace `42 MB` with `73 MB` in both strings (`OFFLINE · ENGLISH · 73 MB MODEL`, `Download model (73 MB)`).

- [ ] **Step 5: Add the cleanup and failed-download tests**

```kotlin
@Test fun `deleteLegacy removes old model dirs and keeps the current one`() {
    File(root, "zipformer-en-20m-2023-02-17").mkdirs()
    File(root, AsrModelRegistry.modelDirName).mkdirs()
    manager.deleteLegacy()
    assertFalse(File(root, "zipformer-en-20m-2023-02-17").exists())
    assertTrue(File(root, AsrModelRegistry.modelDirName).exists())
}

@Test fun `a server error leaves no partial files and isInstalled stays false`() {
    // MockWebServer replies 500 for every request
    val result = runBlocking { manager.download { } }
    assertTrue(result.isFailure)
    assertFalse(manager.isInstalled())
    assertEquals(emptyList<String>(), manager.modelDir().listFiles().orEmpty().map { it.name })
}
```

Call `modelDownloadManager.deleteLegacy()` once in `PodcastViewModel.init` right after the manager is created.

- [ ] **Step 6: Run the model tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AsrModelRegistryTest" --tests "com.example.asr.HotwordsTest" --tests "com.example.asr.ModelDownloadManagerTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/asr app/src/main/java/com/example/ui/AsrSettingsSection.kt app/src/main/java/com/example/ui/PodcastViewModel.kt app/src/test/java/com/example/asr
git commit -m "feat(asr): upgrade to zipformer 2023-06-26 with sponsor hotwords"
```

---

### Task 5: Manual mark state machine

**Files:**
- Create: `app/src/main/java/com/example/asr/ManualAdMark.kt`
- Test: `app/src/test/java/com/example/asr/ManualAdMarkTest.kt`

**Interfaces:**
- Produces:
  - `data class AdMarkState(val startMs: Long? = null) { val isArmed: Boolean }`
  - `sealed class AdMarkResult { data class Armed(val startMs: Long); data class Closed(val range: ManualAdRange); data object RejectedTooShort; data object RejectedOrder }`
  - `data class ManualAdRange(val startMs: Long, val endMs: Long)`
  - `object ManualAdMarker` with `MIN_DURATION_MS = 5_000L`, `MAX_DURATION_MS = 900_000L`, `arm(positionMs)`, `close(state, positionMs, durationMs)`, `cancel()`
  - `fun nudge(segment: EpisodeAdSegmentEntity, moveStart: Boolean, deltaMs: Long, durationMs: Long): EpisodeAdSegmentEntity?`
  - `fun toEntity(episodeId: String, podcastId: String, range: ManualAdRange): EpisodeAdSegmentEntity`

- [ ] **Step 1: Write the failing tests**

```kotlin
class ManualAdMarkTest {
    @Test fun `arm then close produces a range`() {
        val armed = ManualAdMarker.arm(10_000)
        val result = ManualAdMarker.close(armed, 70_000, 600_000)
        assertEquals(AdMarkResult.Closed(ManualAdRange(10_000, 70_000)), result)
    }

    @Test fun `shorter than five seconds is rejected`() {
        assertEquals(
            AdMarkResult.RejectedTooShort,
            ManualAdMarker.close(ManualAdMarker.arm(10_000), 13_000, 600_000),
        )
    }

    @Test fun `end before start is rejected without losing the arm`() {
        assertEquals(
            AdMarkResult.RejectedOrder,
            ManualAdMarker.close(ManualAdMarker.arm(10_000), 4_000, 600_000),
        )
    }

    @Test fun `end is clamped to the episode duration`() {
        val result = ManualAdMarker.close(ManualAdMarker.arm(590_000), 610_000, 600_000)
        assertEquals(AdMarkResult.Closed(ManualAdRange(590_000, 600_000)), result)
    }

    @Test fun `longer than fifteen minutes still closes`() {
        val result = ManualAdMarker.close(ManualAdMarker.arm(0), 1_000_000, 1_200_000)
        assertTrue(result is AdMarkResult.Closed)
    }

    @Test fun `entity uses user source and confirmed`() {
        val e = ManualAdMarker.toEntity("ep", "pod", ManualAdRange(10_000, 70_000))
        assertEquals("ep:10000:70000", e.id)
        assertEquals("USER", e.source)
        assertTrue(e.confirmed)
        assertEquals(1f, e.confidence, 0f)
    }

    @Test fun `nudge moves one boundary`() {
        val e = ManualAdMarker.toEntity("ep", "pod", ManualAdRange(10_000, 70_000))
        val end = ManualAdMarker.nudge(e, moveStart = false, deltaMs = 1_000, durationMs = 600_000)!!
        assertEquals(71_000, end.endMs)
        assertEquals(10_000, end.startMs)
    }

    @Test fun `nudge refuses to shrink below five seconds or cross the edges`() {
        val e = ManualAdMarker.toEntity("ep", "pod", ManualAdRange(10_000, 70_000))
        assertNull(ManualAdMarker.nudge(e, moveStart = true, deltaMs = 66_000, durationMs = 600_000))
        assertNull(ManualAdMarker.nudge(e, moveStart = true, deltaMs = -10_000, durationMs = 600_000))
        assertNull(ManualAdMarker.nudge(e, moveStart = false, deltaMs = 600_000, durationMs = 600_000))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.ManualAdMarkTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Implement `ManualAdMark.kt`**

Rules the signatures do not determine:
- `close` clamps the end to `durationMs` first, rejects `end <= start`, then rejects `end - start < MIN_DURATION_MS`, otherwise returns `Closed`. It never mutates the incoming state.
- `toEntity` label is `"Manual mark"`, `createdAt = System.currentTimeMillis()`, id `"${episodeId}:${range.startMs}:${range.endMs}"`.
- `nudge` returns a copy with the moved boundary and a regenerated id, or `null` when the result would be < 5 s or outside `0..durationMs`.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.ManualAdMarkTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/asr/ManualAdMark.kt app/src/test/java/com/example/asr/ManualAdMarkTest.kt
git commit -m "feat(asr): two-press manual ad range with guarded boundaries"
```

---

### Task 6: Ad selection, USER-safe merge, and ViewModel wiring

**Files:**
- Create: `app/src/main/java/com/example/asr/AdSelection.kt`
- Modify: `app/src/main/java/com/example/asr/AdScanController.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt`
- Test: `app/src/test/java/com/example/asr/AdSelectionTest.kt`, `AdScanControllerTest.kt`, `app/src/test/java/com/example/player/AdSkipDeciderTest.kt`

**Interfaces:**
- Consumes: `ManualAdMarker`, `AdMarkState`, `AdScanStore` additions from Tasks 3 and 5.
- Produces: `object AdSelection { fun visible(all: List<EpisodeAdSegmentEntity>, asrEnabled: Boolean): List<EpisodeAdSegmentEntity> }` — with ASR off returns only `source == "USER"`; with ASR on returns all confirmed entries plus all USER entries.
- Produces on `PodcastViewModel`: `adMarkState: StateFlow<AdMarkState>`, `manualMarkedAds: StateFlow<List<EpisodeAdSegmentEntity>>`, `markAdStart()`, `markAdEnd()`, `cancelAdMark()`, `deleteManualAd(id)`, `nudgeManualAd(id, moveStart, deltaMs)`.

- [ ] **Step 1: Write the failing selection and merge tests**

```kotlin
// AdSelectionTest
@Test fun `asr off keeps only user marks`() {
    val all = listOf(ad("u", "USER", true), ad("a", "ASR", true))
    assertEquals(listOf("u"), AdSelection.visible(all, asrEnabled = false).map { it.id })
}

@Test fun `asr on keeps every confirmed ad`() {
    val all = listOf(ad("u", "USER", true), ad("a", "ASR", true), ad("x", "ASR", false))
    assertEquals(listOf("u", "a"), AdSelection.visible(all, asrEnabled = true).map { it.id })
}

// AdScanControllerTest
@Test fun `a rescan never replaces a user mark`() {
    val controller = controllerWith(FakeStore())
    val existing = listOf(userAd("ep", 10_000, 70_000))
    val detected = listOf(asrAd("ep", 20_000, 60_000))
    val merged = controller.mergeAds(existing, detected)
    assertEquals(listOf("USER"), merged.map { it.source })
    assertEquals(10_000L, merged.single().startMs)
}

@Test fun `overlapping user marks keep the newer one`() {
    val controller = controllerWith(FakeStore())
    val older = userAd("ep", 0, 60_000, createdAt = 1)
    val newer = userAd("ep", 30_000, 90_000, createdAt = 2)
    val merged = controller.mergeAds(listOf(older, newer), emptyList())
    assertEquals(listOf(newer.id), merged.map { it.id })
}

@Test fun `start clears ads before the disabled early return`() {
    val controller = controllerWith(FakeStore(), isEnabled = { false })
    controller.start(episode("ep"), 0)
    assertTrue(controller.currentAds.value.isEmpty())
}
```

`ad(id, source, confirmed)`, `userAd(episodeId, startMs, endMs, createdAt)`, `asrAd(...)`, `episode(id)` and `controllerWith(store, isEnabled)` are local helpers in the test file; build `controllerWith` from the fakes already in `Fakes.kt`.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.AdSelectionTest" --tests "com.example.asr.AdScanControllerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement selection and controller changes**

- `AdSelection.visible`: `all.filter { it.confirmed && (asrEnabled || it.source == "USER") }`.
- `AdScanController.start`: clear `_currentAds` and `ads` **before** the `isEnabled`/`isModelReady` early return.
- `AdScanController.refreshAds`: re-read `store.adSegments(episodeId)` and use its USER entries as protected input to `mergeAds`.
- `mergeAds`: priority `USER` beats anything; between two USER entries the one with the later `createdAt` wins; existing behaviour otherwise. Change its visibility from `private` to `internal` so the unit test can call it.
- `PodcastViewModel`: add the mark flow and actions; on episode start call `adScanStore.adSegments(episode.id)` and publish `AdSelection.visible(...)`; `checkSponsorAndAdDetection` drops the `_asrEnabled`/`_asrModelReady` gate and skips from `_currentAdSegments` only; `markAdEnd()` stores the ad, calls `adSlotLearner.learn(...)` with the current slots and a `DetectedAd(source = USER, confirmed = true)`, saves slots, and appends the ad to `_currentAdSegments`.
- Reset `_adMarkState` to `AdMarkState()` whenever the current episode changes.

- [ ] **Step 4: Add a USER skip test**

```kotlin
@Test fun `a confirmed user mark skips`() {
    val user = ad.copy(source = "USER", confirmed = true)
    assertEquals(SkipDecision.Skip(user), AdSkipDecider().decide(20_000, listOf(user), true))
}
```

- [ ] **Step 5: Run the ASR and player tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.asr.*" --tests "com.example.player.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/asr/AdSelection.kt app/src/main/java/com/example/asr/AdScanController.kt app/src/main/java/com/example/ui/PodcastViewModel.kt app/src/test/java/com/example/asr app/src/test/java/com/example/player
git commit -m "feat(player): load and protect user-marked ads without ASR"
```

---

### Task 7: Player card controls

**Files:**
- Create: `app/src/main/java/com/example/ui/ManualAdControls.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (inside the "Smart Ad & Wave Skipper" card, after the waveform block, before the `if (isAdActive)` block)

**Interfaces:**
- Consumes: `PodcastViewModel.markAdStart/markAdEnd/cancelAdMark/deleteManualAd/nudgeManualAd`, `adMarkState`, `manualMarkedAds`.
- Produces: `@Composable fun ManualAdControls(state: AdMarkState, marks: List<EpisodeAdSegmentEntity>, playbackPositionMs: Long, onStart: () -> Unit, onEnd: () -> Unit, onCancel: () -> Unit, onDelete: (String) -> Unit, onNudge: (String, Boolean, Long) -> Unit)`.

- [ ] **Step 1: Implement `ManualAdControls.kt`**

- Idle: one filled button `Mark ad start` (testTag `manual_ad_mark_button`), `AdGold` background, full width, height 30 dp.
- Armed: a row with the button relabeled `Set ad end · m:ss` (testTag `manual_ad_end_button`), elapsed computed from `playbackPositionMs - state.startMs`, plus an `IconButton` `Close` (testTag `manual_ad_discard_button`).
- Below, when `marks.isNotEmpty()`, a `Manual marks` label and up to 3 rows; each row shows `m:ss – m:ss`, four `TextButton`s labelled `S-1s`, `S+1s`, `E-1s`, `E+1s` (testTags `manual_ad_start_minus`, `manual_ad_start_plus`, `manual_ad_end_minus`, `manual_ad_end_plus`), and a delete `IconButton` (testTag `manual_ad_delete`). Use `LocalCustomColors.current` and the card's existing font sizes (9–11 sp).

- [ ] **Step 2: Insert the composable in the card**

In `PodcastAppContent.kt`, inside the ad-skipper `Card`'s `Column`, after the waveform `if (waveformAmplitudes.isNotEmpty()) { ... }` block and before `if (isAdActive) { ... }`, add a `Spacer(8.dp)` and `ManualAdControls(...)` wired to the view model and `playbackPositionMs`.

- [ ] **Step 3: Compile and run the unit suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/ui/ManualAdControls.kt app/src/main/java/com/example/ui/PodcastAppContent.kt
git commit -m "feat(ui): mark, nudge, and delete ads from the player card"
```

---

### Task 8: Final verification and stale-name sweep

**Files:**
- No new files.

- [ ] **Step 1: Sweep for the old model identity**

Run: `git grep -n "20m-2023-02-17\|epoch-99-avg-1.onnx\|42 MB" -- app/src`
Expected: no matches in app sources (docs and plans may still mention them; `tokens.txt`/`decoder`/`joiner` names now carry `chunk-16-left-128`).

- [ ] **Step 2: Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 3: Build the APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

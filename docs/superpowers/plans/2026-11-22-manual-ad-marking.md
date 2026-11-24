# Manual Ad Marking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add playback controls to save exact ad ranges for an episode and use repeated manual ranges to improve learned ad skips for future episodes of the same podcast.

**Architecture:** Persist manual ranges as an explicit encoded field on `EpisodeEntity`, with a versioned Room migration that preserves existing databases. Playback captures start/end positions, saves validated ranges, and exposes exact ranges to the current episode skip path. The existing `PodcastAdPatternEngine` consumes sibling episodes’ manual ranges and only emits a cross-episode manual prediction when at least two distinct episodes support a slot.

**Tech Stack:** Kotlin, Jetpack Compose, Room, StateFlow, JUnit, Android instrumented Room migration tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-22-manual-ad-marking-design.md`

## Global Constraints

- Keep manual ranges separate from transcript text and legacy `adTimestampsSeconds` hints.
- Preserve user-marked data during episode metadata/feed refreshes.
- Clamp times to episode duration and reject zero/negative ranges.
- Do not create a cross-episode manual prediction without support from at least two distinct manually marked episodes.
- An in-progress mark is temporary and must not survive changing episodes or restarting the app.

## Review Focus

- Start equals end, end precedes start, or either time exceeds episode duration: test validation clamps or rejects without saving an invalid range (Task 1 and Task 3).
- App upgrade from Room v6 with existing episode data: test migration retains all existing rows and gives them an empty manual-range value (Task 1).
- Feed/metadata refresh returns a stale episode object: test existing manual ranges are not overwritten (Task 1).
- Two ranges from one episode versus matching ranges from two distinct episodes: only the latter can create learned prediction (Task 2).
- User changes episode or cancels while a mark is pending: pending state is cleared and no interval is persisted (Task 3).

---

### Task 1: Persist explicit manual ad ranges

**Files:**
- Modify: `app/src/main/java/com/example/data/Entities.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDatabase.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDao.kt`
- Modify: `app/src/main/java/com/example/data/PodcastRepository.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt` episode merge paths
- Create: `app/src/main/java/com/example/data/ManualAdRangeCodec.kt`
- Test: `app/src/test/java/com/example/data/ManualAdRangeCodecTest.kt`
- Test: `app/src/androidTest/java/com/example/data/PodcastDatabaseMigrationTest.kt`

**Interfaces:**
- Produces `ManualAdRange(startMs: Long, endMs: Long)`, `ManualAdRangeCodec.encode(List<ManualAdRange>): String`, `ManualAdRangeCodec.decode(String): List<ManualAdRange>`, and `ManualAdRangeCodec.clamp(range: ManualAdRange, durationMs: Long): ManualAdRange?`.
- Add `EpisodeEntity.manualAdRanges: String = ""`, encoded as semicolon-separated `startMs-endMs` pairs.
- Add repository method `suspend fun addManualAdRange(episodeId: String, range: ManualAdRange)` that validates against the stored episode duration, deduplicates exact ranges, and persists via the DAO.

- [ ] **Step 1: Write failing codec tests** for round-trip of multiple ranges, empty input, malformed entries, and non-positive lengths; test `clamp` separately for endpoint clamping and rejection when the resulting range is empty.
- [ ] **Step 2: Run** `bash gradlew :app:testDebugUnitTest --tests com.example.data.ManualAdRangeCodecTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failures are due to the missing codec.
- [ ] **Step 3: Implement** the range data class and codec; decoding drops malformed/non-positive entries, while `clamp` clamps endpoints to episode duration and returns `null` when the resulting interval is empty.
- [ ] **Step 4: Add a Room v6→v7 migration** adding `manualAdRanges TEXT NOT NULL DEFAULT ''`; register it in `PodcastDatabase` so supported v6 upgrades use the migration rather than destructive fallback.
- [ ] **Step 5: Write and run an instrumented migration test** that creates a v6 SQLite database with the existing episode schema and one episode row, opens it through Room with the v6→v7 migration, and verifies the row is preserved with an empty range field.
- [ ] **Step 6: Add DAO/repository persistence tests** for insert, duplicate suppression, clamped endpoints, invalid input, and read-after-write.
- [ ] **Step 7: Preserve ranges in all episode merge/refresh paths** that construct replacement `EpisodeEntity` values; add a regression test proving a refreshed episode cannot erase a stored manual range.
- [ ] **Step 8: Run focused codec/repository tests and the migration test; confirm all pass.**

### Task 2: Teach the show-level learner from repeated manual ranges

**Files:**
- Modify: `app/src/main/java/com/example/player/PodcastAdPatternEngine.kt`
- Modify: `app/src/main/java/com/example/player/VozTranscriber.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt` background analysis inputs
- Test: `app/src/test/java/com/example/player/PodcastAdPatternEngineManualRangeTest.kt`

**Interfaces:**
- `PodcastAdPatternEngine.learnShowAdPattern(podcastId: String, showEpisodes: List<EpisodeEntity>)` remains the public entry point and reads ranges using `ManualAdRangeCodec`.
- Manual ranges contribute normalized start/end ratios and duration samples grouped into corresponding show-level ad slots; a manual-derived slot is eligible only when at least two distinct sibling episode IDs support it.
- Corresponding slots are ranges whose normalized start positions differ by no more than `0.08` of episode duration; cluster start/end ratios and duration are averaged across distinct supporting episodes.

- [ ] **Step 1: Write failing learner tests** for one marked episode (no manual-derived prediction), two episodes with corresponding ranges (prediction is created), and two ranges from one episode (still insufficient evidence).
- [ ] **Step 2: Run** `bash gradlew :app:testDebugUnitTest --tests com.example.player.PodcastAdPatternEngineManualRangeTest --no-configuration-cache --no-daemon --max-workers=1`; confirm expected failures.
- [ ] **Step 3: Update learning** to decode each episode’s saved ranges and group corresponding intervals when normalized start positions differ by at most `0.08`, maintaining distinct supporting episode IDs and averaged boundaries/duration.
- [ ] **Step 4: Keep exact saved ranges distinct from probabilistic predictions** and ensure a manual-derived prediction is not emitted until two distinct episodes support its slot.
- [ ] **Step 5: Verify** the focused learner tests and existing ad-pattern tests pass.

### Task 3: Add Start Ad / End & Save playback controls

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt`
- Create: `app/src/main/java/com/example/player/ManualAdMarkSession.kt`
- Test: `app/src/test/java/com/example/player/ManualAdMarkSessionTest.kt`
- Test: `app/src/test/java/com/example/player/ManualAdSkipSelectionTest.kt`

**Interfaces:**
- ViewModel exposes pending-start state and methods `startManualAdMark()`, `finishManualAdMark()`, and `cancelManualAdMark()`.
- The Compose playback control reads as **Start Ad** when idle and **End & Save** while marking; while marking, show a cancel action.
- Exact saved ranges for the current episode are converted to ad intervals and participate in the existing automatic skip path on future plays of that episode.

- [ ] **Step 1: Write failing tests** for start/end capture, cancel, episode-change reset, end-before-start rejection, and exact saved-range skip targeting.
- [ ] **Step 2: Run** `bash gradlew :app:testDebugUnitTest --tests com.example.player.ManualAdMarkSessionTest --tests com.example.player.ManualAdSkipSelectionTest --no-configuration-cache --no-daemon --max-workers=1`; confirm expected failures.
- [ ] **Step 3: Implement** ephemeral mark-session state and ViewModel actions; on save, validate/clamp to duration, persist through `PodcastRepository.addManualAdRange`, refresh the current episode state, and clear pending state.
- [ ] **Step 4: Clear pending mark state** when changing or stopping episodes and when the user cancels; do not persist incomplete marks.
- [ ] **Step 5: Load exact ranges on episode playback** and merge them into detected skip intervals without overriding the existing sponsor-chapter-first target resolution.
- [ ] **Step 6: Add the Compose controls** near the existing ad skipper controls, with stable test tags for start/end and cancel states.
- [ ] **Step 7: Verify** focused state/skip tests and the full unit suite pass.

### Task 4: Build and device smoke test

**Files:**
- No additional source files expected.

- [ ] **Step 1: Run** `bash gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --no-daemon --max-workers=1`; verify zero test failures and successful assemble.
- [ ] **Step 2: Inspect `adb devices -l`** and, if the Pixel 9 is connected, install the newly built APK using its explicit serial and verify the installed version.
- [ ] **Step 3: Smoke-test on device:** start a manual range, end and save it, reopen the episode and confirm it is retained; cancel a second pending range and confirm it is absent.
- [ ] **Step 4: Review** `git diff --check` and confirm no unrelated user changes were altered.

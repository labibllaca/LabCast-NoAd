# Manual Marks Modal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open the current episode's saved manual ad marks in a bottom sheet from a button beside the mark start/end control, with nudge and delete actions that persist and refresh skip intervals.

**Architecture:** Keep the encoded `EpisodeEntity.manualAdRanges` representation; add a pure editor for range changes, repository writes for update/delete, thin ViewModel actions that reuse the existing episode-and-interval refresh path, and a standalone Compose sheet used by `FullPlayerScreen`. No Room migration.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Room, StateFlow, JUnit/Robolectric, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-24-manual-marks-modal-design.md`

## Global Constraints

- Ranges stay in `EpisodeEntity.manualAdRanges`, encoded by `ManualAdRangeCodec`; do not change the schema.
- Preserve feed-refresh range preservation and episode merge paths.
- Clamp nudges to the episode duration and reject ranges where end is not after start; deduplicate after edits.
- Do not alter detection, auto-skip selection, the ad-pattern learner, or the in-progress mark session.
- Reuse `LocalCustomColors`, `ModalBottomSheet`, and `testTag` conventions of the existing player sheets.
- Keep all existing mark-related test tags unchanged.

## Review Focus

- Nudge past 0 or the episode duration: clamp, and never produce an invalid or empty range.
- Deleting a mark from the sheet: remove exactly that range, not sibling marks with similar times.
- Edits on a stale episode while background transcript analysis runs: do not overwrite newer ranges.
- Many saved marks (more than three): the sheet scrolls and shows all of them.
- Empty marks: the button still opens the sheet and shows a clear empty state.

---

### Task 1: Edit saved ranges in the data layer

**Files:**
- Create: `app/src/main/java/com/example/data/ManualAdRangeEditor.kt`
- Modify: `app/src/main/java/com/example/data/PodcastRepository.kt`
- Test: `app/src/test/java/com/example/data/ManualAdRangeEditorTest.kt`
- Modify: `app/src/test/java/com/example/data/ManualAdRangeRepositoryTest.kt`

**Interfaces:**
- Produces `object ManualAdRangeEditor { fun nudge(ranges: List<ManualAdRange>, target: ManualAdRange, isStart: Boolean, deltaMs: Long, durationMs: Long): List<ManualAdRange>; fun remove(ranges: List<ManualAdRange>, target: ManualAdRange): List<ManualAdRange> }`.
- Produces repository methods `suspend fun updateManualAdRange(episodeId: String, target: ManualAdRange, replacement: ManualAdRange)` and `suspend fun deleteManualAdRange(episodeId: String, target: ManualAdRange)`.
- Consumes `ManualAdRangeCodec.clamp` and `PodcastDao.updateManualAdRanges`.

- [ ] **Step 1: Write failing `ManualAdRangeEditorTest`** for: nudging the start by -1s clamps at 0; nudging the end by +1s clamps at duration; a nudge that makes end <= start leaves the list unchanged; `remove` drops only the exact target; results preserve order and deduplicate.
- [ ] **Step 2:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.data.ManualAdRangeEditorTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failures are due to the missing editor.
- [ ] **Step 3:** Implement `ManualAdRangeEditor`: replace the target with the clamped new range via `ManualAdRangeCodec.clamp`, returning the original list when clamping fails; `remove` filters out only the exact target.
- [ ] **Step 4:** Run the same command; confirm PASS.
- [ ] **Step 5: Add failing repository tests** to `ManualAdRangeRepositoryTest`: update persists the nudged range; delete removes only that range; unknown episode and invalid replacement are no-ops.
- [ ] **Step 6:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.data.ManualAdRangeRepositoryTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failures are due to missing methods.
- [ ] **Step 7:** Implement the two repository methods using `getEpisodeById` → decode → editor/codec → `updateManualAdRanges`, skipping writes when the encoded value is unchanged.
- [ ] **Step 8:** Run the repository test class; confirm PASS.
- [ ] **Step 9:** Commit `ManualAdRangeEditor`, repository changes, and both test files.

### Task 2: ViewModel actions for nudge and delete

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt`
- Test: `app/src/test/java/com/example/ui/ManualAdRangeViewModelEditTest.kt` (Robolectric, real in-memory Room repository)

**Interfaces:**
- Produces `fun nudgeManualAdRange(range: ManualAdRange, isStart: Boolean, deltaMs: Long)` and `fun deleteManualAdRange(range: ManualAdRange)` on `PodcastViewModel`.
- Produces private helper `syncManualAdRanges(episodeId: String, durationMs: Long)` that reloads the episode, updates `_currentPlayingEpisode` only when the id still matches, and rebuilds `_detectedTranscriptAds` intervals; shared with `finishManualAdMark()`.
- Consumes Task 1 repository methods and `ManualAdSkipSelection.intervals`.

- [ ] **Step 1: Write failing ViewModel tests**: after nudge, `_currentPlayingEpisode.manualAdRanges` decodes to the changed range and `_detectedTranscriptAds` contains intervals with the new boundary; after delete, the range is gone from both; invalid nudge leaves state unchanged; changing episode mid-write does not update the new episode's state.
- [ ] **Step 2:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.ui.ManualAdRangeViewModelEditTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failures.
- [ ] **Step 3:** Extract the reload-and-refresh logic from `finishManualAdMark()` into `syncManualAdRanges(episodeId, durationMs)`, then implement the two new actions with `viewModelScope.launch`: call the Task 1 repository method, then `syncManualAdRanges`. Keep `_isManualAdRangeSaving` for these edits and use `_sponsorSkipEvent` for failures.
- [ ] **Step 4:** Run the ViewModel test class and `ManualAdMarkSessionTest`; confirm PASS.
- [ ] **Step 5:** Commit.

### Task 3: Manual marks button and bottom sheet

**Files:**
- Create: `app/src/main/java/com/example/ui/ManualMarksBottomSheet.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (`FullPlayerScreen` mark row and sheet host)
- Test: `app/src/test/java/com/example/ui/ManualMarksBottomSheetTest.kt`

**Interfaces:**
- Produces `@Composable fun ManualMarksBottomSheet(ranges: List<ManualAdRange>, durationMs: Long, formatTime: (Long) -> String, onNudge: (ManualAdRange, Boolean, Long) -> Unit, onDelete: (ManualAdRange) -> Unit, onDismiss: () -> Unit)`.
- Tags: `manual_marks_button` (open button), `manual_marks_sheet`, `manual_marks_empty`, `manual_marks_row`, `manual_ad_start_minus`, `manual_ad_start_plus`, `manual_ad_end_minus`, `manual_ad_end_plus`, `manual_ad_delete`.
- Consumes `viewModel.manualAdRanges` decoded from the current episode, `viewModel.nudgeManualAdRange`, `viewModel.deleteManualAdRange`, and `viewModel.formatDuration`.

- [ ] **Step 1: Write failing Compose test** for the sheet: all ranges render as `m:ss – m:ss` rows; clicking S+1s calls `onNudge` with `(range, true, 1000L)` and E+1s with `(range, false, 1000L)`; clicking delete calls `onDelete(range)`; empty list shows `manual_marks_empty`.
- [ ] **Step 2:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.ui.ManualMarksBottomSheetTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failure due to missing composable.
- [ ] **Step 3:** Implement `ManualMarksBottomSheet` as a `ModalBottomSheet` with a scrollable column, one row per range, ±1s `TextButton`s and delete `IconButton`, and an empty-state row. Use sheet/theme conventions from `EpisodeDescriptionBottomSheet`.
- [ ] **Step 4:** Run the sheet test; confirm PASS.
- [ ] **Step 5:** In `FullPlayerScreen`, add `var showManualMarksSheet by remember { mutableStateOf(false) }`, an `IconButton` tagged `manual_marks_button` immediately beside the mark start/end control, and host the sheet with the other modals. Decode ranges from `episode!!.manualAdRanges` for the sheet and pass `viewModel::nudgeManualAdRange` / `viewModel::deleteManualAdRange`.
- [ ] **Step 6:** Commit.

### Task 4: Verify and device smoke test

**Files:** None expected.

- [ ] **Step 1:** Run `bash gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --no-daemon --max-workers=1`; confirm zero failures and successful assemble.
- [ ] **Step 2:** Install on the connected device with its explicit serial and open the player; verify the new button sits beside the mark control.
- [ ] **Step 3:** Open the sheet, nudge a mark by +1s, delete a mark, and reopen to confirm persistence and refreshed skip intervals.
- [ ] **Step 4:** Run `git diff --check` and confirm no unrelated changes were altered.

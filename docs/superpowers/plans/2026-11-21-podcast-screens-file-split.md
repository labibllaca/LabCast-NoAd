# Podcast Screens File Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the 5,955-line Compose screen file into feature-focused files without changing app behavior or appearance.

**Architecture:** Keep `PodcastAppContent` as the root/composition file. Move existing top-level composables, together with their private helpers, to cohesive feature files in the same `com.example.ui` package. Preserve signatures; make only the visibility changes required for same-package callers.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle, existing unit/UI tests.

**Spec:** `docs/superpowers/specs/2026-11-21-podcast-screens-file-split-design.md`

## Global Constraints

- Preserve behavior and appearance; no state, navigation, copy, or layout redesign.
- Preserve all existing uncommitted UI and typography edits; do not stage or commit unrelated worktree changes.
- Keep the current package and composable signatures; add no dependency or abstraction layer.
- Build and run existing unit tests after each move group.

## Review Focus

- Existing edited typography calls in `PodcastAppContent.kt` remain intact — inspect diff and compile after each move group.
- Root-to-screen calls still resolve — run `:app:assembleDebug` after each group.
- File-private helpers remain accessible only where needed — compile and avoid widening unrelated symbols.
- Screen-specific imports are complete and no stale imports remain — compile each moved group.
- UI behavior is unchanged despite no guaranteed snapshot baseline — run available unit tests and review moved code for non-move edits.

---

## File map

- Keep: `app/src/main/java/com/example/ui/PodcastAppContent.kt` — app root/navigation and any truly shared shell code.
- Create: `DiscoverScreen.kt` — `DiscoverScreen`, `PodcastGridItem`, and Discover-only helpers.
- Create: `PodcastDetailScreen.kt` — `PodcastDetailScreen` and its local helpers.
- Create: `DownloadsScreen.kt` — `DownloadsScreen`, `SwipeableOfflineEpisodeItem`, and local helpers.
- Create: `HistoryScreen.kt` — `VerlaufScreen`, `VerlaufEpisodeCard`, and local helpers.
- Create: `PodcastComponents.kt` — shared artwork, episode/search cards, and reusable list components referenced by multiple screens.
- Create: `MiniPlayer.kt` — `MiniPlayerSection` and its related navigation controls/helper.
- Create: `FullPlayerScreen.kt` — `FullPlayerScreen` and player-only helpers.
- Create: `PlayerSheets.kt` — episode description, transcript, chapters, and sleep timer sheets.

Keep the named public composables in the same package with unchanged parameters. If symbol references show a proposed grouping is wrong, adjust grouping without changing APIs or behavior.

### Task 1: Establish baseline

**Files:** None.

- [x] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- [x] Record pass/fail and any pre-existing failure; do not fix unrelated failures.

### Task 2: Move Discover screen

**Files:** Create `DiscoverScreen.kt`; modify `PodcastAppContent.kt`.

- [x] Move `DiscoverScreen`, `PodcastGridItem`, and only Discover-specific helpers; preserve the existing typography edits verbatim.
- [x] Keep shared symbols in the root file temporarily; do not duplicate definitions.
- [x] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`; expect the same baseline result.

### Task 3: Move detail, downloads, and history screens

**Files:** Create `PodcastDetailScreen.kt`, `DownloadsScreen.kt`, `HistoryScreen.kt`; modify `PodcastAppContent.kt`.

- [x] Move each screen with its feature-local helpers and unchanged composable signatures.
- [x] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`; expect the same baseline result.

### Task 4: Move reusable cards and artwork

**Files:** Create `PodcastComponents.kt`; modify the feature files and `PodcastAppContent.kt` as needed.

- [x] Move only composables used by multiple feature files, including shared artwork/list/search components; keep single-feature helpers with their screen.
- [x] Change `private` visibility only for helpers required by another file in the same package.
- [x] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`; expect the same baseline result.

### Task 5: Move mini and full player surfaces

**Files:** Create `MiniPlayer.kt`, `FullPlayerScreen.kt`; modify `PodcastAppContent.kt` and `PodcastComponents.kt` as needed.

- [x] Move the mini-player/navigation composables and full-player composable with their local helpers; preserve player behavior and existing typography edits.
- [x] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`; expect the same baseline result.

### Task 6: Move player sheets and final cleanup

**Files:** Create `PlayerSheets.kt`; modify `PodcastAppContent.kt` and moved files.

- [x] Move the four player bottom sheets with their local helpers; remove only imports made unused by moves.
- [x] Confirm `PodcastAppContent.kt` now contains the app shell rather than screen implementations.
- [x] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`; expect the same baseline result.
- [x] Review `git diff` to verify all pre-existing user edits remain and no unrelated paths are staged or modified.

No commit is planned unless requested; do not commit the user's existing edits.

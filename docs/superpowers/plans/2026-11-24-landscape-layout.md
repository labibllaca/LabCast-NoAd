# Landscape Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add good landscape layouts to every app screen while leaving the existing portrait layouts unchanged.

**Architecture:** Detect orientation once in `MainActivity` and provide a `LocalIsLandscape` composition local. Small pure helpers supply padding, width caps, and artwork sizing; each screen branches only where the portrait layout cannot fit a landscape viewport. The full player reuses its existing child content in a two-column landscape arrangement.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Robolectric/Compose tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-24-landscape-layout-design.md`

## Global Constraints

- Portrait rendering and all existing `testTag` values stay unchanged.
- Reuse `Type.kt` font roles and `LocalCustomColors`; add no fonts or dependencies.
- Keep `sp` text sizes and accessibility font scaling; do not introduce fixed heights that clip.
- No playback, detection, data-layer, or ViewModel changes.
- No device UI-driving by the agent; the user runs the device smoke test.

## Review Focus

- Long titles and episode names in landscape: text ellipsizes instead of pushing controls off-screen.
- Small landscape viewport height at large font scale: player controls remain reachable.
- Portrait regression: layout and tags identical to before the change.
- Modal sheets in landscape: capped width/height and still dismissible.
- Bottom bar in landscape: mini player and pill navigation both visible without overlap.

---

### Task 1: Orientation flag and layout helpers

**Files:**
- Create: `app/src/main/java/com/example/ui/Layout.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Test: `app/src/test/java/com/example/ui/LandscapeLayoutTest.kt`

**Interfaces:**
- Produces `val LocalIsLandscape = staticCompositionLocalOf { false }`.
- Produces `object LandscapeLayout` with `fun isLandscape(configuration: android.content.res.Configuration): Boolean`, `fun screenPadding(isLandscape: Boolean): PaddingValues`, `fun listMaxWidth(isLandscape: Boolean): Dp`, `fun sheetMaxWidth(isLandscape: Boolean): Dp`, `fun playerArtworkSize(isLandscape: Boolean, screenHeightDp: Dp): Dp`.
- Landscape artwork size: `screenHeightDp * 0.6f` clamped to `96.dp..200.dp`; portrait returns `280.dp`.

- [ ] **Step 1: Write failing `LandscapeLayoutTest`** with hand-derived literals for: landscape/portrait detection, landscape screen padding `PaddingValues(horizontal = 12.dp, vertical = 8.dp)` versus portrait `PaddingValues(16.dp)`, list/sheet max widths (`Dp.Unspecified` in portrait, `600.dp` in landscape), and artwork sizes at heights 300/600/1000 dp including both clamp edges.
- [ ] **Step 2:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.ui.LandscapeLayoutTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failure due to missing helpers.
- [ ] **Step 3:** Implement `Layout.kt` with the exact signatures above; use `Configuration.ORIENTATION_LANDSCAPE`.
- [ ] **Step 4:** Run the same command; confirm PASS.
- [ ] **Step 5:** In `MainActivity.setContent`, read `LocalConfiguration.current`, compute `LandscapeLayout.isLandscape`, and wrap `MyApplicationTheme` in `CompositionLocalProvider(LocalIsLandscape provides isLandscape)`.
- [ ] **Step 6:** Commit.

### Task 2: Compact bottom bar and list screens

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (`PodcastAppContent.bottomBar`, `DiscoverScreen`, `PodcastDetailScreen`, `DownloadsScreen`, `VerlaufScreen` roots)
- Modify: `app/src/main/java/com/example/ui/SettingsScreen.kt` (`SettingsScreen` root)
- Test: `app/src/test/java/com/example/ui/LandscapeNavigationTest.kt`

**Interfaces:**
- Consumes `LocalIsLandscape` and `LandscapeLayout.screenPadding` / `listMaxWidth`.
- `FloatingPillNavigationBar(activeTab, isAlbanian, onTabSelected, onSearchClick)` keeps its signature and tags; in landscape it uses compact horizontal padding.

- [ ] **Step 1: Write failing Compose test** rendering `FloatingPillNavigationBar` under `CompositionLocalProvider(LocalIsLandscape provides true)` and asserting the existing tab/search tags are displayed and the bar height does not exceed 56 dp.
- [ ] **Step 2:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.ui.LandscapeNavigationTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failure (wrong height/tags).
- [ ] **Step 3:** In the Scaffold `bottomBar`, place the mini player and pill nav in a `Row` when landscape (`MiniPlayerSection` weighted, nav compact); keep the portrait `Column` branch unchanged.
- [ ] **Step 4:** Apply `LandscapeLayout.screenPadding` and `listMaxWidth` to the roots of Discover, PodcastDetail, Downloads, Verlauf, and Settings, leaving portrait values identical.
- [ ] **Step 5:** Run the navigation test and `:app:assembleDebug`; confirm PASS.
- [ ] **Step 6:** Commit.

### Task 3: Landscape full player

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (`FullPlayerScreen`)
- Modify: `app/src/main/java/com/example/ui/Layout.kt` (`LandscapePlayerFrame`)
- Test: `app/src/test/java/com/example/ui/LandscapePlayerFrameTest.kt`

**Interfaces:**
- Consumes `LocalIsLandscape` and `LandscapeLayout.playerArtworkSize`.
- Existing child sections (ad console, slider, transport controls, quick actions, sheet hosts) are reused in both orientations via local composable lambdas; no new public API.

- [ ] **Step 1: Write failing Compose test** rendering a small `LandscapePlayerFrame` slot-based composable (create it inside `Layout.kt`) with tags `landscape_player_frame`, `landscape_player_artwork`, `landscape_player_body`, asserting the artwork slot is to the left of the body in landscape. Use `onNodeWithTag(...).fetchSemanticsNode().boundsInRoot`.
- [ ] **Step 2:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.ui.LandscapePlayerFrameTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failure due to missing composable.
- [ ] **Step 3:** Implement `LandscapePlayerFrame` as a `Row` with weighted artwork/body slots and the three tags above.
- [ ] **Step 4:** Run the frame test; confirm PASS.
- [ ] **Step 5:** In `FullPlayerScreen`, keep the portrait `Column` branch as-is; in landscape wrap the existing artwork and body content in `LandscapePlayerFrame`, sizing artwork with `playerArtworkSize(isLandscape, LocalConfiguration.current.screenHeightDp.dp)`. Reduce landscape paddings but keep all controls and sheet hosts.
- [ ] **Step 6:** Run `:app:assembleDebug` and the full unit suite; confirm PASS.
- [ ] **Step 7:** Commit.

### Task 4: Modal sheets in landscape

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (chapter, transcript, description, sleep-timer sheets)
- Modify: `app/src/main/java/com/example/ui/ManualMarksBottomSheet.kt`
- Modify: `app/src/test/java/com/example/ui/ManualMarksBottomSheetTest.kt`

**Interfaces:**
- Consumes `LocalIsLandscape` and `LandscapeLayout.sheetMaxWidth`.

- [ ] **Step 1: Add a failing test case** to `ManualMarksBottomSheetTest` rendering the sheet under `LocalIsLandscape provides true` and asserting `manual_marks_sheet` is displayed and the dismiss button is still clickable.
- [ ] **Step 2:** Run `bash gradlew :app:testDebugUnitTest --tests com.example.ui.ManualMarksBottomSheetTest --no-configuration-cache --no-daemon --max-workers=1`; confirm failure.
- [ ] **Step 3:** Apply the shared sheet width cap in landscape to all five sheets, e.g. `Modifier.testTag(...).widthIn(max = LandscapeLayout.sheetMaxWidth(isLandscape))`; keep portrait modifiers unchanged.
- [ ] **Step 4:** Run the sheet test; confirm PASS.
- [ ] **Step 5:** Commit.

### Task 5: Verify and hand device smoke test to the user

**Files:** None expected.

- [ ] **Step 1:** Run `bash gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --no-daemon --max-workers=1`; confirm zero failures and successful assemble.
- [ ] **Step 2:** Install the debug APK on the connected device (no agent UI-driving) and report that the landscape smoke test is the user's step.
- [ ] **Step 3:** Run `git diff --check` and confirm no unrelated files changed.

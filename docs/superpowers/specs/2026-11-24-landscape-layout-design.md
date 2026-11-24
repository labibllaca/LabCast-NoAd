# Landscape Layout — Design

## Intent
Keep the current portrait UI unchanged and add a landscape layout for every screen of the Android app so the player, tabs, detail view, and modal sheets remain readable and usable when the device is rotated.

## Goals
- Detect orientation once in `MainActivity` via `LocalConfiguration` and expose a shared `LocalIsLandscape` flag to Compose screens.
- Full player in landscape: artwork on the left, title/ad console/transport controls on the right; all controls reachable without scrolling the artwork off-screen.
- Bottom navigation + mini player become a compact horizontal strip in landscape.
- List screens (Discover, Downloads, Verlauf, Settings, Podcast detail) keep their existing content but use reduced vertical padding and capped content width so cards do not stretch awkwardly.
- Modal bottom sheets are centered with a max width and capped height in landscape.
- Portrait rendering and existing test tags stay unchanged.

## Non-goals
- No tablet/foldable WindowSizeClass system, no new dependencies, no typography changes, no redesign of portrait screens.
- No changes to playback, ad detection, data layer, or view-model logic beyond exposing nothing new.
- No device UI-driving by the agent; the user performs the device smoke test.

## Constraints
- Respect the bundled font roles already in `Type.kt` (Lexend headings, Poppins labels, Lato body); reuse theme styles, add no font assets.
- Use `sp` text sizes and keep accessibility font scaling; avoid fixed heights that clip at larger scales.
- Keep `LocalCustomColors` theming and all existing `testTag` values.
- Prefer small layout helpers over scattering orientation checks through every composable.

## Approaches
1. Shared composition-local orientation flag + targeted per-screen landscape branches (selected): no dependency, portrait paths preserved, precise control over the player.
2. Material 3 `WindowSizeClass`: more infrastructure and a dependency; better for tablets but beyond a phone landscape pass.

## Implementation outline
- Add `ui/Layout.kt` with `LocalIsLandscape` and small helpers: landscape content padding, sheet width/height modifier, and player artwork size derived from available height.
- Provide the flag in `MainActivity` and branch each screen's root container; keep portrait expressions identical.
- In `FullPlayerScreen`, extract the portrait column untouched and add a landscape `Row` variant that reuses the same child composables (ad console, slider, controls, sheet hosts).
- Apply the shared sheet modifier to all `ModalBottomSheet` composables (chapter, transcript, description, sleep timer, manual marks).
- Add focused Compose tests for landscape helpers/tags and a landscape-qualifier render test where practical; run the full unit suite and `assembleDebug`.

## Approval
Awaiting approval before implementation planning.

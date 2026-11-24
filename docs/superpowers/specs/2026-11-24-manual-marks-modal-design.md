# Manual Marks Modal — Design

## Intent
Move the saved manual ad marks list out of the player card into a modal sheet, opened by a button next to the mark ad start/end control. Restore the nudge and delete actions from the device build on `main`'s data model.

## Goals
- A "Manual marks" button sits beside the existing **Start Ad / End & Save** control in `FullPlayerScreen`.
- Tapping it opens a `ModalBottomSheet` listing every saved manual range for the current episode (not just three), each showing `m:ss – m:ss`.
- Each row offers S-1s, S+1s, E-1s, E+1s and delete, matching the controls on the device screenshot.
- Nudges clamp to the episode duration and reject ranges whose end is not after the start; edits deduplicate and persist.
- After any edit, the episode state and the manual skip intervals used by auto-skip refresh.

## Non-goals
- No changes to detection, auto-skip selection, or the ad-pattern learner.
- No new Room migration, cross-device sync, or landscape/responsive work (that is a separate task).
- No change to the in-progress mark session (Start Ad / End & Save / Cancel).

## Constraints
- Ranges remain stored in `EpisodeEntity.manualAdRanges` via `ManualAdRangeCodec`; keep feed-refresh preservation and skip-interval refresh behavior intact.
- Reuse the project's `ModalBottomSheet` + `LocalCustomColors` + `testTag` conventions from the other player sheets.
- Keep all existing mark-related test tags; add stable tags for the new button, sheet, and rows.

## Approaches
1. Full port into a modal (selected): button + sheet with nudge/delete, backed by new repository write methods. Matches the device build; needs small data-layer and test additions.
2. Read-only listing sheet: smaller, but drops nudge/delete the user already has.

## Implementation outline
- `PodcastRepository`: add `updateManualAdRange(episodeId, old, new)` and `deleteManualAdRange(episodeId, range)`, both reading the stored episode, validating/clamping through `ManualAdRangeCodec`, and writing via the existing `updateManualAdRanges` DAO query.
- `PodcastViewModel`: add `nudgeManualAdRange(range, boundary, deltaMs)` and `deleteManualAdRange(range)`; reload the episode, replace/updates encoded ranges, and rebuild manual skip intervals and detected ads as `finishManualAdMark()` already does.
- UI: new `ManualMarksBottomSheet` composable; "Manual marks" button next to the mark control; sheet state in `FullPlayerScreen`; empty state when no ranges exist.
- Tests: codec/repository coverage for nudge clamping, deletion, dedupe, and persistence; UI tags for button/sheet/rows; device smoke test.

## Approval
Awaiting approval before implementation planning.

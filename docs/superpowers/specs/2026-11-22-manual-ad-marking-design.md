# Manual Ad Marking — Design

## Intent
Let listeners mark an ad’s exact start and end during playback, preserve that range for later plays of the same episode, and use confirmed manual ranges to improve ad timing predictions for future episodes of the same podcast.

## Goals
- Provide a playback control that toggles from **Start Ad** to **End & Save** while a mark is in progress.
- Allow cancelling an unfinished mark without saving it.
- Persist exact manual ranges per episode and use them for future playback of that episode.
- Feed normalized timing samples into the existing show-level ad-pattern learner.
- Avoid adding new cross-episode predictions from a single sample; require repeated manual evidence before trusting a learned slot.

## Non-goals
- No editing/deleting saved ranges in this first version.
- No cross-device synchronization of manual ranges.
- No replacement of transcript/chapter detection or current auto-skip controls.

## Approaches
1. **Encode ranges in `adTimestampsSeconds`**: avoids a Room migration but repurposes a field that currently stores single timestamps; rejected due to ambiguity and compatibility risk.
2. **Store explicit episode ranges and extend the existing learner**: requires a Room migration, but keeps start/end data unambiguous and gives the learner exact samples. **Selected.**

## Behavior
- During playback, tapping **Start Ad** captures the current playback position and changes the control to **End & Save**.
- Tapping **End & Save** captures the end position, validates that it is after the start, saves the range to the current episode, and clears the in-progress mark.
- A cancel control clears an in-progress mark without persisting it.
- Saved ranges are available to automatic skipping on future plays of that episode.
- Saved ranges from episodes of the same podcast contribute start/end ratios and duration evidence to the existing pattern learner. Cross-episode learned skips require at least two manually marked episodes supporting a slot; existing independently generated predictions remain otherwise unchanged.
- An unfinished mark is temporary UI state and is not restored after changing episodes or restarting the app.

## Constraints and implementation calls
- Keep manual ranges separate from transcript text and the legacy timestamp hints.
- Add a Room migration for the explicit persisted representation (or a dedicated table if that better matches the existing database conventions).
- Clamp saved times to episode duration and reject zero/negative ranges.
- Test range validation/persistence, migration compatibility, manual playback skip selection, and show-level minimum evidence.

## Approval
Awaiting user approval before implementation planning.

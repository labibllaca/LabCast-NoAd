# Design: Durable Per-Chunk Ad Decisions

Date: 2026-11-05
Status: Draft for approval

## Intent

Let listeners correct false-positive ad labels and keep those corrections effective. Today, manual markings are written into the transcript text. Removing a marker does not suppress the keyword/brand heuristic, so the chunk can be suggested again; matching by timestamp alone can also change multiple chunks.

## Goals

- Persist explicit “ad” and “not an ad” choices per transcript chunk and episode.
- Make user choices override feed markers and heuristic suggestions in the transcript-chunk pipeline.
- Keep decisions across app restarts and transcript re-parsing; avoid applying stale decisions to unrelated chunks.
- Never rewrite transcript text when a user changes a decision.
- Preserve existing user data during the Room schema upgrade.

## Non-goals

- Reworking ad detection or other skip pipelines (chapter, keyword-interval, acoustic).
- Syncing decisions between devices or editing transcript text/timestamps.
- Transcript-sheet layout, scrolling, or other usability changes.

## Approaches

1. Store rejection tags in transcript text: smallest change, but brittle on transcript refresh and continues coupling decisions to a rewritten text blob.
2. Add a rejection-only column to the episode: persists better, but does not model both explicit choices and still requires a migration.
3. **Use a dedicated `ad_decisions` Room table (recommended):** one row per episode/chunk with `isAd`; no row means detector default. Explicit true/false overrides parser results. More wiring, but clear semantics and durable choices.

## Proposed design

- Bump Room database v6 to v7 with a hand-written, non-destructive migration registered before destructive fallbacks.
- Key each decision by episode ID, start time, and a stable-within-transcript segment identity (speaker + text hash) to disambiguate equal timestamps and make stale rows inert after transcript changes.
- Resolve stored decisions over parsed segments before suggestions/UI/auto-skip consume them. Explicit false clears the ad state and excludes the chunk from suggestions; explicit true marks it as an ad.
- Replace timestamp toggle with a set-decision operation. Mark, unmark, and “mark all suggested” write decision rows; none rewrites the transcript.
- Delete decisions when their podcast is removed.

## Tests and acceptance

- Unit-test both override directions, no-decision passthrough, rejected-suggestion exclusion, stale keys, and duplicate timestamps.
- Test Room upsert/last-choice-wins, episode isolation, batch writes, deletion cleanup, and v6→v7 migration preserving existing data.
- Verify in app that a false-positive can be marked “not an ad,” remains unmarked after reopening/re-parsing, and is not suggested or skipped by the transcript-chunk pipeline; verify an explicit ad mark can be reversed the same way.
- Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

## Calls left to implementation

- Exact segment-key representation and migration test harness, provided they preserve the behavior above.

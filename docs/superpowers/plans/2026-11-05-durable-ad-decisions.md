# Durable Ad Decisions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let listeners mark transcript chunks as ads or not ads and have those choices persist and override detection.

**Architecture:** Store explicit per-episode/per-segment decisions in a Room table. Resolve them over parsed transcript segments before suggestions and transcript-chunk auto-skip consume those segments. Replace transcript-text rewrites with decision writes and expose a reject action for suggested chunks.

**Tech Stack:** Kotlin, Room 2.7, Kotlin Flow, JUnit4, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-05-durable-ad-decisions-design.md`

## Global Constraints

- Room database moves from v6 to v7 with a hand-written, non-destructive migration registered before destructive fallbacks.
- A decision is keyed by episode, start time, and segment identity; explicit true/false overrides parser results; no row preserves detector behavior.
- No user decision may rewrite `EpisodeEntity.transcript`.
- Decision cleanup runs before deleting episodes for an unsubscribed podcast.
- Do not modify chapter, keyword-interval, or acoustic ad-skip pipelines.
- Keep the existing Android/JVM test setup and run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

## Review Focus

- Two different segments sharing a timestamp must retain independent decisions — resolver test with equal timestamps and different text.
- A rejection must survive a fresh parse and must not return as a suggestion — resolver and suggestion tests.
- A stale decision after transcript text/time changes must not affect another segment — resolver test with unknown key.
- Repeated decisions must be last-write-wins — DAO upsert test.
- Upgrade from v6 must preserve existing episode data — migration test and final upgrade verification.

---

### Task 1: Segment identity and decision resolution

**Files:**
- Modify: `app/src/main/java/com/example/data/TranscriptParser.kt` (`TranscriptSegment`)
- Create: `app/src/main/java/com/example/data/AdDecisionResolver.kt`
- Test: `app/src/test/java/com/example/data/AdDecisionResolverTest.kt`

**Interfaces:**
- Produces `TranscriptSegment.isRejectedAd: Boolean = false` and `segmentKey: Int = ("$speaker|$text").hashCode()`.
- Produces `AdDecisionKey(startTimeSeconds: Long, segmentKey: Int)` and `AdDecisionResolver.apply(segments: List<TranscriptSegment>, decisions: Map<AdDecisionKey, Boolean>): List<TranscriptSegment>`.

- [ ] **Step 1: Write resolver tests** asserting: true sets `isSponsor=true` and `isRejectedAd=false`; false clears `isSponsor` and sets `isRejectedAd=true`; missing decisions preserve both fields; equal timestamps with different segment keys resolve independently; an unknown key is ignored; resolving the same rejected transcript twice returns the same result.
- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.data.AdDecisionResolverTest'`
Expected: FAIL because the resolver and decision fields do not exist.

- [ ] **Step 3: Add the segment key and rejection flag** to `TranscriptSegment`; keep `segmentKey` out of data-class constructor equality, but derive it from speaker and text.
- [ ] **Step 4: Implement `AdDecisionKey.of(segment)` and `AdDecisionResolver.apply(...)`**; true overrides to ad, false overrides to rejected non-ad, and no entry leaves the parsed segment unchanged.
- [ ] **Step 5: Run resolver tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.data.AdDecisionResolverTest'`
Expected: PASS.

- [ ] **Step 6: Commit** `feat: resolve persisted ad decisions per transcript segment`.

### Task 2: Persist decisions with a safe v6→v7 migration

**Files:**
- Modify: `app/src/main/java/com/example/data/Entities.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDao.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDatabase.kt`
- Modify: `app/src/main/java/com/example/data/PodcastRepository.kt`
- Test: `app/src/test/java/com/example/data/AdDecisionDaoTest.kt`
- Test: `app/src/test/java/com/example/data/PodcastDatabaseMigrationTest.kt`

**Interfaces:**
- Produces `AdDecisionEntity(episodeId: String, startTimeSeconds: Long, segmentKey: Int, isAd: Boolean, decidedAt: Long)`.
- Produces DAO `getAdDecisions(episodeId): Flow<List<AdDecisionEntity>>`, `upsertAdDecision(decision)`, `upsertAdDecisions(decisions)`, and `deleteAdDecisionsForPodcast(podcastId)`.
- Produces repository `getAdDecisions(episodeId)`, `setAdDecision(episodeId, key, isAd)`, and `setAdDecisions(episodeId, decisions)`.

- [ ] **Step 1: Write DAO tests** asserting upsert replaces the same key with the latest value, equal timestamps with different segment keys remain separate rows, batch upsert writes all rows, episode flows do not leak decisions across episodes, and podcast cleanup removes only its episodes' decisions.
- [ ] **Step 2: Run DAO tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.data.AdDecisionDaoTest'`
Expected: FAIL because the entity and DAO methods do not exist.

- [ ] **Step 3: Add the Room entity and DAO methods** with primary key `(episodeId, startTimeSeconds, segmentKey)` and `OnConflictStrategy.REPLACE` for single and batch upserts.
- [ ] **Step 4: Add repository APIs** that convert `AdDecisionKey` values into decision rows; call `deleteAdDecisionsForPodcast` before deleting the podcast's episodes in both removal paths.
- [ ] **Step 5: Add `MIGRATION_6_7` and bump the database to v7**; create `ad_decisions` with all four key/value columns plus `decidedAt`; register migration before both destructive fallbacks.
- [ ] **Step 6: Test migration preservation** by opening a v6 database containing an existing episode, migrating through the registered migration, and asserting the episode remains and `ad_decisions` is available and empty.
- [ ] **Step 7: Run DAO and migration tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.data.AdDecisionDaoTest' --tests 'com.example.data.PodcastDatabaseMigrationTest'`
Expected: PASS; existing v6 episode data is unchanged.

- [ ] **Step 8: Commit** `feat: persist per-episode ad decisions with migration`.

### Task 3: Apply decisions before suggestions and auto-skip

**Files:**
- Modify: `app/src/main/java/com/example/data/TranscriptParser.kt` (`analyzeTranscriptForSuggestedAds`)
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt` (transcript flow, decision methods)
- Test: `app/src/test/java/com/example/data/TranscriptParserSuggestionTest.kt`

**Interfaces:**
- Consumes Task 1 resolver/key and Task 2 repository APIs.
- Produces `PodcastViewModel.setAdDecision(segment: TranscriptSegment, isAd: Boolean)` and updates `transcriptSegments` as decisions change.

- [ ] **Step 1: Write suggestion tests** asserting marked and rejected segments are not suggested, while an undecided keyword-matching segment remains suggested.
- [ ] **Step 2: Run suggestion tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.data.TranscriptParserSuggestionTest'`
Expected: FAIL because rejected chunks are not modeled or filtered.

- [ ] **Step 3: Exclude decided ads and rejected chunks from suggestions** by skipping `isSponsor` and `isRejectedAd` segments.
- [ ] **Step 4: Make `transcriptSegments` decision-aware** using a per-episode decision flow (`flatMapLatest`); parse the current episode transcript and resolve stored decisions before emitting the list.
- [ ] **Step 5: Replace `toggleSegmentAdStatus(startTimeSeconds)`** with `setAdDecision(segment, isAd)` that writes the exact segment key and episode ID; do not update the episode or serialize transcript text.
- [ ] **Step 6: Update `markAllSuggestedAsAds()`** to batch-write true decisions for suggested segment keys; remove transcript rewriting and `repository.updateEpisode` from both marking paths.
- [ ] **Step 7: Run parser/resolver tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.data.AdDecisionResolverTest' --tests 'com.example.data.TranscriptParserSuggestionTest' :app:assembleDebug`
Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit** `feat: apply saved ad decisions to transcript detection`.

### Task 4: Expose explicit accept/reject controls

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (transcript chunk action controls)

**Interfaces:**
- Consumes `TranscriptSegment.isSponsor`, `isRejectedAd`, and `PodcastViewModel.setAdDecision(segment, isAd)`.
- Produces explicit “Mark Ad” and “Not an ad” actions for suggested chunks, plus reversible ad/not-ad actions on decided chunks.

- [ ] **Step 1: Wire actions to explicit decisions**: suggestion accept→true, suggestion reject→false, marked-ad unmark→false, and plain/rejected mark→true. Keep action callbacks segment-specific, not timestamp-only. Preserve the existing Compose style and accessible action descriptions.
- [ ] **Step 2: Compile and run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit** `feat: let listeners reject suggested transcript ads`.

### Task 5: End-to-end verification

**Files:** none unless verification exposes a defect.

- [ ] **Step 1: Run all unit tests and assemble**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Verify in the app**: a suggested chunk exposes both accept and reject actions; reject a false-positive and confirm it loses its suggestion and ad state, close/reopen the episode, and confirm it remains rejected and is not skipped by the transcript-chunk pipeline. Mark it as an ad again and confirm the decision reverses. Verify a v6 install upgrades without losing episodes.
- [ ] **Step 3: Report results** including any manual device checks that could not be run.

## Self-review

- Spec coverage: dedicated table, v6→v7 migration, per-segment override, suggestion filtering, explicit UI rejection, no transcript rewrite, and podcast cleanup are covered in Tasks 1–5.
- Boundary cases: equal timestamps, unknown keys, repeated writes, episode isolation, and migration preservation are pinned by tests.
- Scope: other ad-detection/skip pipelines and transcript-sheet layout changes remain untouched.

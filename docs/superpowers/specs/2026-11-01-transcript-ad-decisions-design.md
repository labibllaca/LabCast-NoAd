# Design: Transcript Ad Decisions, Open-at-Spoken-Line, Compact Chunks

Date: 2026-11-01
Status: Draft for approval

## Intent

Make the transcript bottom sheet usable for correcting the ad detector, instead
of fighting it.

Today the sheet always opens at the top of the script, so the listener has to
hunt for the line being spoken. Tapping a chunk seeks **and silently closes the
sheet** (`PodcastAppContent.kt:4366-4370`), so a tap that lands on the card
instead of the small "Mark Ad" button loses your place. Chunk cards are tall
because the `AD / SPONSOR (AUTO-SKIP)` badge competes for header width with the
speaker name (`:5110-5148`), and there is no way to tell the detector it was
wrong: a suggested chunk can only be accepted, and if you accept it by mistake
you must then unmark it.

The owner wants: open at the current line, then scroll freely; mark/unmark
without the sheet disappearing; reject a wrong suggestion and have that
rejection remembered per episode; and chunks that are only as tall as their
text.

## Goals

1. Opening the transcript scrolls once to the segment being spoken. After that,
   playback never moves the list; only the listener scrolls.
2. Tapping a chunk still seeks, and the sheet **stays open**.
3. Marking and unmarking an ad chunk happens without dismissing the sheet, and
   marking is available for every chunk, not just suggested ones.
4. Algorithm-suggested chunks can be accepted *or* rejected, with both choices
   persisted per episode (approach "C": dedicated table).
5. User decisions override both the feed's own ad markers and the heuristic
   suggestion, and feed the existing transcript auto-skip
   (`PodcastViewModel.kt:2325-2362`).
6. Chunk height follows content: tighter padding and spacing, no fixed heights,
   badge no longer competes with the speaker for the header row.

## Non-goals

- Changing how suggestions are computed (`TranscriptParser.analyzeTranscriptForSuggestedAds`).
- Editing chunk text, splitting/merging chunks, or editing timestamps.
- A "revert to detector default" action. Once a chunk is decided, it is decided;
  toggling it again is a new decision.
- Syncing decisions across devices, or exporting them.
- Changing the player, the audio, or the ad-skip heuristics themselves.
- Auto-scrolling continuously behind the listener's finger ("follow mode").

## Anti-goals (failure even if it works)

- The list jumping while the listener reads, because playback advanced.
- A tap on a chunk dismissing the sheet, or seeking twice for one tap.
- A rejected suggestion silently coming back on the next open or next play.
- The transcript chunk pipeline skipping content the listener explicitly marked
  "not an ad".
- Wiping the user's library because of the schema bump.

## Scope of a rejection (owner's decision)

A rejection suppresses **only the transcript chunk pipeline** — the one this
sheet drives. It deliberately does not touch the three pipelines that run before
it in `checkSponsorAndAdDetection`: raw-transcript keyword intervals
(`_detectedTranscriptAds`), chapter-based skipping (including the fabricated
"Sponsored: Featured Partner" chapter on feed episodes), and the synthetic
acoustic zones. Unifying those is a separate piece of work.

Accepted consequence: a region the listener rejects here can still be skipped by
one of those other pipelines. Closing the sheet does not change their behaviour
either.

## Constraints

- Room database at version 6, `fallbackToDestructiveMigration()` is active
  (`PodcastDatabase.kt:8-28`). Adding a table requires version 7 **and a hand
  written migration**, or every existing install loses subscriptions, playback
  positions and downloads.
- `media3`/MediaPlayer playback and the skip pipeline stay as they are.
- Everything is on-device and offline.
- The connected test device is a Samsung SM-G998B, Android 15, currently with
  `wm size` overridden to 720x1600 (~360 dp wide) — the narrow case is the one
  where the current badge collapses the header, so it is the case to check.

## Approaches considered

**A. Marker in the transcript text.** Add `isRejectedAd` to `TranscriptSegment`,
serialise it as a `[Not Ad]` tag next to the existing `[Sponsor Break]` tag, and
parse it back. No schema change: this is exactly how "marked as ad" is stored
today. Cheapest, but keeps state inside a text blob that gets rewritten on every
toggle, and dies on a transcript re-fetch.

**B. New column on `EpisodeEntity`** (`rejectedAdTimestamps`, `adTimestamps`
style). Survives re-fetch, but still only expresses "rejected", not a general
two-way decision, and needs the same schema bump as C for less modelling.

**C. Separate `ad_decisions` table — chosen.** One row per decided chunk, keyed
by `(episodeId, startTimeSeconds)` with an `isAd` flag. Models the real thing
(mark *and* unmark, per episode), survives transcript re-fetch, and — unlike
today — lets the app stop rewriting the whole transcript string on every toggle.
Cost: entity, DAO, migration, repository and ViewModel wiring.

## Data

### Entity

```kotlin
@Entity(
    tableName = "ad_decisions",
    primaryKeys = ["episodeId", "startTimeSeconds", "segmentKey"]
)
data class AdDecisionEntity(
    val episodeId: String,
    val startTimeSeconds: Long,
    val segmentKey: Int,               // hash of speaker + text, see below
    val isAd: Boolean,                 // true = listener says ad, false = not an ad
    val decidedAt: Long = System.currentTimeMillis()
)
```

No row means "no opinion" — the detector result stands.

**Why `segmentKey` (builder call).** `startTimeSeconds` alone is not unique:
`parseRawTranscriptLines` falls back to `0L` for continuation lines, and a
multi-line WebVTT/SRT cue only consumes its first line
(`TranscriptParser.kt:222-239, 248`), so several segments can share second 0.
Keying on the timestamp alone would let one decision mark a whole cluster, and
would make a `LazyColumn` key crash. `segmentKey` is
`("$speaker|$text").hashCode()`, computed on `TranscriptSegment`, which keeps
rows distinct for duplicates and — because the key also carries the timestamp —
makes rows for a re-fetched, re-timed transcript **inert instead of wrong**
(they simply stop matching). No clearing logic needed on refresh.

### DAO (`PodcastDao`)

```kotlin
@Query("SELECT * FROM ad_decisions WHERE episodeId = :episodeId")
fun getAdDecisions(episodeId: String): Flow<List<AdDecisionEntity>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAdDecision(decision: AdDecisionEntity)

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAdDecisions(decisions: List<AdDecisionEntity>)

@Query("DELETE FROM ad_decisions WHERE episodeId = :episodeId")
suspend fun deleteAdDecisionsForEpisode(episodeId: String)   // called when an episode is deleted
```### Migration

`PodcastDatabase` version 6 → 7, registered with `.addMigrations(MIGRATION_6_7)`
**before** the destructive fallbacks, which stay for older version jumps:

```sql
CREATE TABLE IF NOT EXISTS `ad_decisions` (
  `episodeId` TEXT NOT NULL,
  `startTimeSeconds` INTEGER NOT NULL,
  `segmentKey` INTEGER NOT NULL,
  `isAd` INTEGER NOT NULL,
  `decidedAt` INTEGER NOT NULL,
  PRIMARY KEY(`episodeId`, `startTimeSeconds`, `segmentKey`)
)
```

### Resolution

A pure function, so it is unit-testable without Android:

```kotlin
// TranscriptSegment gains: val isRejectedAd: Boolean = false
//                        and:  val segmentKey: Int = ("$speaker|$text").hashCode()
object AdDecisionResolver {
    fun apply(
        segments: List<TranscriptSegment>,
        decisions: Map<DecisionKey, Boolean>   // (startTimeSeconds, segmentKey) -> isAd
    ): List<TranscriptSegment>
}
```

Rules, applied per segment:

| Decision row | Result |
|---|---|
| `isAd = true`  | `isSponsor = true`, `isRejectedAd = false` |
| `isAd = false` | `isSponsor = false`, `isRejectedAd = true` |
| none | unchanged (feed marker / heuristic result stands) |

`transcriptSegments` becomes `combine(currentEpisode, decisions) { ep, d -> parse(ep).let { AdDecisionResolver.apply(it, d) } }`.
Everything downstream — the sheet, `sponsorCount`, auto-skip — reads that flow,
so a rejection stops a skip without touching the skip code.

Suggested chunks stay derived, with two filters: drop segments already
`isSponsor`, and drop `isRejectedAd`. This also fixes the "N suggested" counter,
which currently includes chunks that are already marked as ads.

## Behaviour changes

1. **Open at the spoken line.** `LazyListState` + a one-shot
   `LaunchedEffect` guarded by a `remember`ed flag: find the last filtered
   segment whose `startTimeSeconds <= currentSec`, `scrollToItem` it with a
   small leading offset (two rows of context), then never again. Re-opening the
   sheet is a fresh composition, so it re-anchors. Filters and search do not
   re-anchor.
2. **Tap = seek, sheet stays open.** The call site
   (`PodcastAppContent.kt:4366-4370`) keeps `viewModel.seekTo(startMs)` and drops
   `showTranscriptSheet = false`. The player is behind the sheet; the sheet
   remains as the place you left.
3. **Explicit mark/unmark.** Trailing icon buttons in the chunk header:
   suggested → ✓ accept / ✕ reject; marked ad → ✕ unmark; normal → ＋ mark.
   Only these write decisions. The `Mark All as Ads` button keeps working and
   writes one batch of `isAd = true` rows (no transcript rewrite).
4. **No more transcript rewriting.** `toggleSegmentAdStatus` and
   `markAllSuggestedAsAds` write decision rows and stop calling
   `serializeSegmentsToTranscriptText`/`repository.updateEpisode`. This removes
   an existing side effect where toggling one chunk rewrote the whole script.
5. **Compact chunks.** `Card` padding 10 dp, list spacing 6 dp, text 12 sp with
   16 sp line height, 26 dp icon buttons, no fixed item heights. The header keeps
   time chip + speaker + `Spacer(weight 1f)` + a short state pill (`AD` / `AI`,
   8-9 sp, `maxLines = 1`) + actions, so the header can no longer be squeezed
   into a taller wrapped layout. A suggestion's reason moves to a single 9-10 sp
   ellipsised line below the body text, shown only while the chunk is still
   undecided — marked and normal chunks stay as short as their text.
6. **The list gets the leftover height instead of a fixed 440 dp box**
   (builder call). Today `LazyColumn` is capped at `heightIn(max = 440.dp)`
   inside a non-scrolling `Column` under the STT card, the suggestions card and
   the search row (`:4599-4604, 5045-5049`); on a 360 dp-wide, ~800 dp-tall sheet
   that header stack plus 440 dp can overflow and clip the list's bottom, and the
   soft keyboard makes it worse. The cap is removed and the list takes
   `Modifier.weight(1f, fill = false)`, so header keeps its natural height, the
   list gets everything left over, and the sheet stays content-sized when short.
7. **Auto-skip is paused while the sheet is open** (owner's decision). A
   `setTranscriptSheetOpen(Boolean)` flag on the ViewModel, set from a
   `DisposableEffect` around the sheet so it resets if the screen is left, makes
   `checkSponsorAndAdDetection` return immediately. That is what makes "tap a
   marked chunk, hear it, correct it" possible: `seekTo` calls the detector
   (`:1607-1615`), and today a marked chunk would be jumped over within a tick.
   On close, the normal skip behaviour resumes; if playback is then sitting
   inside a marked chunk, the next tick skips it — expected, and the flag does
   not remember what was already skipped.

## Edge cases

- **Existing markings.** Transcripts already containing `[Sponsor Break]` (from
  the feed or written by earlier builds) still parse to `isSponsor = true` and
  remain the base state. Decisions override them; nothing needs backfilling.
- **Transcript re-fetch.** Segments can shift timestamps; because the key holds
  both the second and a text hash, old rows no longer match anything and are
  inert — they cannot be applied to the wrong line. No clearing logic, no
  migration of decisions on refresh.
- **Episode deleted / podcast unsubscribed.** Delete that episode's decision
  rows wherever its `EpisodeEntity` is deleted.
- **Segment shorter than the 45 s skip window.** Unchanged; the existing skip
  logic at `:2329` is untouched.
- **Search/filter active while opening.** Anchor to the current line if it
  survives the filter; otherwise stay at the top rather than scrolling to a
  wrong index.
- **Empty transcript / empty suggestions.** Unchanged empty states.

## Testing

- `AdDecisionResolverTest` (JVM): override both directions; no-row passthrough;
  rejected suggestion excluded; unknown timestamps ignored.
- `TranscriptParserTest` (JVM): suggestion list excludes `isSponsor` and
  `isRejectedAd` segments.
- `AdDecisionDaoTest` (Robolectric, in-memory Room): upsert replaces, batch
  upsert, per-episode delete, flow emits.
- Pause flag: with the sheet reported open, `checkSponsorAndAdDetection` performs
  no skip and changes no counters; after close, the next tick inside a marked
  chunk skips exactly once.
- Manual on the phone (720x1600): open at the spoken line; scroll stays put as
  playback advances; mark/unmark keeps the sheet; a marked chunk can be tapped
  and heard while the sheet is open; reject a suggestion, close, reopen, still
  rejected; card heights hug the text.
- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` must pass; install
  with the `installapk` alias.

## Left to the builder

- Exact icon glyphs and pill wording, within the compact-header rule.
- Whether the reason line below suggestions stays one line or gets ellipsised at
  a character budget.
- Whether `AdDecisionResolver` lives in `data/` beside `TranscriptParser` or in
  a new `player/` file.
- Whether to enable `exportSchema` for a real migration test; if enabled, the
  migration above should get an instrumented test too.

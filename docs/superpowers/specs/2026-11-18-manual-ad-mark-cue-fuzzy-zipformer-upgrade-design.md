# Design: Manual Ad Marking, Robust Cue Matching, Zipformer Upgrade

Date: 2026-11-18
Status: Draft for approval
Baseline: branch `feat/asr-ad-pattern` (the ASR pipeline; `main` still has the synthetic
waveform detector)

## Intent

Three changes to the on-device ad pipeline, chosen by the owner:

1. **Manual ad marking.** A control in the player's "Smart Ad & Wave Skipper" card:
   one press at the ad start, one press at the ad end. The marked range becomes a
   confirmed ad for that episode and is skipped like a detected ad.
2. **Robust cue matching.** The current `AdCueDetector` uses `contains()` on lowercase
   text. Real ASR output ("brought to you buy", "square space", "one eight hundred")
   misses. Matching must survive plausible recognition errors without becoming loose
   enough to skip content.
3. **Model upgrade.** Replace the 20M streaming Zipformer with
   `sherpa-onnx-streaming-zipformer-en-2023-06-26` (int8, ~73 MB total) and add
   contextual hotword biasing so sponsor names are recognized.

Why: the owner reports poor transcription of ad passages, and wants a way to correct
the app by hand when detection or transcription fails.

## Goals

1. Two-press manual marking in the player card, without touching playback.
2. Manual ads persist per episode, survive rescan/replay, and drive skipping.
3. Manual ads count as confirmed evidence for the per-show slot learner.
4. Cue matching tolerates ASR word errors, split cues, digit/word variants, and
   British/American spelling, while keeping "no cue → no skip".
5. Better sponsor-name recognition via hotwords, without shipping the model in the APK.
6. All existing ASR tests keep passing; new behavior is unit-tested.

## Non-goals

- No cloud calls, no diarization, no non-English transcription.
- No offline/2-pass rescoring model (rejected: see Approaches B).
- No automatic "this looks like an ad" suggestion for manual marks.
- No manual editing of ASR segments themselves.
- No change to the playback stack (`MediaPlayer`) or to `AdScanController`'s planning.
- No VAD/audio-front-end tuning in this change (deferred until a WER measurement exists).

## Anti-goals (failure even if it works)

- A manual mark that silently disappears after a rescan.
- A manual mark that skips content the user did not mean (e.g. an accidental
  single press creating a 3-second ad, or an end-before-start range).
- Fuzzy matching loose enough to fire on ordinary conversation ("thanks to our
  sponsor" as a normal sentence is a known false positive; see Cue rules).
- A model upgrade that re-downloads 73 MB without the user re-consenting, or
  leaves the old 44 MB model on disk forever.
- Manual marking that requires ASR to be enabled or the model to be downloaded.

## Constraints

- Kotlin 2.0.21, AGP 9.1.1, minSdk 24, compileSdk 36, Room 2.7.0,
  sherpa-onnx 2.0.1 (JitPack), JUnit 4.
- Room DB is v7 on this branch (`EpisodeAdSegmentEntity`, `AdSlotEntity`,
  `EpisodeAsrSegmentEntity`, `EpisodeScanEntity`). This change needs **no schema
  migration**: manual ads reuse `episode_ad_segments` with `source = "USER"`.
- `AdSource.USER` already exists in `AsrModels.kt`.
- `EpisodeAdSegmentEntity.confirmed` already gates skipping (`AdSkipDecider`).
- Model files are downloaded on first enable and pinned by SHA-256 in
  `AsrModelRegistry`; `AsrPreferences.isModelInstalled()` compares the stored
  `modelVersion` with `AsrModelRegistry.modelDirName`.
- The scan's audio comes from `MediaPcmSource` at 16 kHz mono.

## Approaches considered

### A. Direct swap, keep streaming (chosen)

Upgrade the streaming model in place, add per-stream hotwords, fuzzy cue matching,
and the manual mark button. Streaming keeps word-level timing, which the skip
boundaries depend on, and the scan controller is unchanged.

Trade-off: still not the most accurate English ASR available, and hotwords only
help the transducer's beam, they cannot fix a badly misheard word.

### B. Two-pass with an offline model (rejected)

Keep streaming near the playhead, rescan ad windows with an offline Zipformer.
Offline models return one text blob per window with no word timestamps, so cue
positions (and therefore skip boundaries) become approximate, and the controller
needs a second code path. Accuracy gain does not justify losing boundaries.

### C. A plus VAD/front-end tuning (rejected for now)

Lower the Silero threshold, pad speech more, disable VAD inside scan windows.
Plausible fix for music beds, but it is blind without a measured WER baseline,
and it changes every boundary in the app. Deferred as its own change once the
owner has a labeled episode set.

## Decisions

### 1. Manual ad marking

**Interaction.** One button in the card, below the ON/OFF switch and the waveform
strip. Its label is the state:

| State | Label | Action |
|---|---|---|
| idle | `Ad markieren` | arm at `playbackPositionMs` |
| armed | `Ad-Ende setzen · 0:42` (elapsed) | close the range at `playbackPositionMs` |
| armed | small `×` beside it | cancel, no ad stored |

- The mark row is always visible while an episode is loaded, independent of
  `asrEnabled`, `isAsrModelReady`, and `isAutoAdSkipEnabled`.
- Pressing does **not** pause, seek, or change playback.
- Armed state is in-memory only (`AdMarkState` in the ViewModel). Leaving the
  episode, or starting a different one, cancels it.
- Elapsed time updates from the existing position flow; no new ticker.

**Validation.**
- Closing needs `end - start >= 5_000 ms` and `end <= durationMs`.
- `end <= start` (user seeked back in between): do not store, emit
  `Ad-Markierung verworfen: Ende liegt vor dem Start`, keep armed.
- Range longer than 15 minutes: store, but log it (unlikely, not an error).

**Persistence.** `EpisodeAdSegmentEntity` with
`id = "<episodeId>:<startMs>:<endMs>"`, `source = "USER"`, `confirmed = true`,
`confidence = 1.0f`, `label = "Manual mark"`, `createdAt = now`.
Written through `AdScanStore.upsertAdSegments` (already exposed), then merged into
`_currentAdSegments` immediately so skipping works without waiting for a scan.
Nudge and delete need two small DAO additions: `adSegment(id)` and
`deleteAdSegment(id)`, forwarded through `AdScanStore`/`RoomAdScanStore`.

**Surviving the scan.** `AdScanController.refreshAds()` currently rebuilds the list
from segments detected in this run and can replace an overlapping entry. Change the
merge so ads with `source == "USER"` are never replaced by a detected ad, and are
re-read from the store before each merge. `mergeAds` priority becomes:
`USER` > confirmed `ASR` > confirmed `TRANSCRIPT`/`CHAPTER` > unconfirmed.
Two manual ads overlapping each other: the newer one wins.

**Effect on skipping.** A `USER` ad behaves exactly like a confirmed ASR ad in
`AdSkipDecider` (skip when `autoSkip && confirmed && position in [start, end-1s)`).
Because it is confirmed, it also feeds `AdSlotLearner` as a `hits` observation.

**Loading independent of ASR.** Today `_currentAdSegments` is fed only by a running
scan, and `checkSponsorAndAdDetection()` returns early unless ASR is enabled and the
model is ready. Change:

- `AdScanController.start()` clears `_currentAds` **before** the
  `isEnabled`/`isModelReady` early return, so no stale ads from another episode can
  skip.
- The ViewModel always loads stored ads for the current episode when the episode
  starts, through `adScanStore.adSegments(episodeId)`. With ASR off it keeps only
  `source == "USER"`; with ASR on it keeps every stored confirmed ad (so previously
  detected ads still skip while detection is off, but no new detection happens).
- The skip check becomes `hasLoadedAdsFor(episodeId)` instead of the ASR flags.

**Slot learning.** Manual ads go through the same learner path as confirmed ASR ads:
they can create a new slot on first hit and always increment `hits`. The owner can
delete a wrongly created slot in the existing slot editor, so a one-off dynamic ad
cannot silently reshape the show. Because slot learning is otherwise tied to a
completed scan range, `markAdEnd()` calls the learner directly after storing the ad,
so the observation is recorded even with ASR off.

**Correction.** The existing per-show slot editor does not list episode segments. Add
a small "Manual marks" list in the same card section, max 3 entries. Each entry shows
`start – end` and offers `−1 s` / `+1 s` for the start, `−1 s` / `+1 s` for the end,
and a delete `×`. Nudges update the row in place (new id, old row replaced) through
`AdScanStore.upsertAdSegments` plus a delete by id; the effective range is
immediately re-read so the player uses it. Guards: the range may not shrink below
5 s and not cross 0 or `durationMs`; a rejected nudge leaves the row unchanged.

**Language.** The whole card is hardcoded English today (`Smart Ad & Wave Skipper`,
`Zap Ad`), and `AppLanguage` only covers the settings card. The new strings stay
English and inline: `Mark ad start`, `Set ad end · 0:42`, `Discard`, `Manual marks`,
core skip events in English. No localization work in this change.

**Surface.** The control lives in the expanded player card only. The mini player and
the media notification stay unchanged, so a mark can be a second or two late (which
the nudges absorb).

### 2. Robust cue matching

New file `asr/TextMatch.kt`, pure Kotlin, no dependency:

- `normalize(text)`: lowercase (ROOT), strip punctuation/quotes, collapse spaces,
  expand digits to words (`1-800` → `one eight hundred`, `50%` → `fifty percent`),
  normalize `&` → `and`, `+` → `plus`.
- `tokens(text)`: normalized words with a small equivalence table
  (`buy`/`by`, `site`/`sight`, `code`/`kode`, `percent`/`%`), used only for cues.
- `fuzzyContains(haystack, needle, threshold)`: token-window comparison with
  normalized Levenshtein similarity, best window; `>= 0.84` counts as a hit for
  multi-word cues, `>= 0.90` for single-word brands.
- `phoneticKey(word)`: compact Double-Metaphone-style key (implemented inline,
  ~80 lines, primary key only). Used for brand tokens of length ≥ 6 where the
  fuzzy score is 0.70–0.84; the brand then needs the promo-language check as well.

`AdCueDetector` keeps its public API and tests. Changes inside:

- Match on `normalize(prev.text + " " + cur.text)` and keep the existing
  split-cue start attribution (previous segment start when the cue spans both).
- Start cues: exact substring **or** `fuzzyContains` at 0.84. A start cue alone is
  still enough for a confirmed start (unchanged).
- Brand-only starts still require promotional language (unchanged), but the promo
  check also uses fuzzy at 0.84.
- End cues: same treatment; additionally accept the resume sentences already in the
  spec ("back to our conversation", "where were we", "so anyway") only when an ad
  is open and ≥ 20 s have passed, so ordinary conversation cannot close an ad early.
- False-positive guard: start cues that occur inside the first 8 s of an episode
  and match only fuzzily (not exactly) are ignored, because cold-start audio
  produces more errors there.
- Each detection keeps `startCue`/`endCue` as the matched canonical phrase, so the
  log line names what fired.

Hotwords get the 0.84 fuzzy treatment only in the log, not in the recognizer.

### 3. Model upgrade and hotwords

**Registry.** `AsrModelRegistry` points at
`sherpa-onnx-streaming-zipformer-en-2023-06-26`:
`encoder-…-chunk-16-left-128.int8.onnx` (71.1 MB),
`decoder-…-chunk-16-left-128.int8.onnx` (1.3 MB),
`joiner-…-chunk-16-left-128.int8.onnx` (0.3 MB), `tokens.txt`, plus the existing
`silero_vad.onnx`. `modelDirName` becomes
`zipformer-en-2023-06-26`. Exact SHA-256 values are computed from the downloaded
files during implementation and pinned; the download is rejected on mismatch.

**Recognizer.** `SherpaSpeechRecognizer` takes the file names from the registry
instead of hardcoding `encoder-epoch-99-avg-1.int8.onnx`, sets
`hotwordsScore = 1.6f`, and `startStream` passes the hotword string to
`createStream(hotwords = …)` (API verified in sherpa-onnx kotlin-api:
`createStream(hotwords: String = "")`).

**Hotwords content.** Built once at recognizer start from
`AdCueDetector.DEFAULT_BRANDS` + cue phrases, uppercased, one per line, deduped,
capped at 100 lines (sherpa-onnx practical limit). Format is plain phrases, no
per-word scores; the global `hotwordsScore` applies. If the model rejects the file
(sherpa logs an error on creation), the wrapper falls back to no hotwords and logs
once — the app must still recognize.

**Upgrade path.** `isModelInstalled()` already compares
`modelVersion == modelDirName`, so the version bump forces a re-download. Additional
work:
- `ModelDownloadManager.delete()` (or a new `deleteLegacy()` on start) removes the
  old `zipformer-en-20m-2023-02-17` directory so 44 MB are not orphaned.
- Download consent text in `SettingsScreen` states the new size (73 MB) and the
  Apache-2.0 license; download still requires an explicit tap.
- If a download fails after the version bump, `_asrModelReady` stays false and the
  app falls back to cue-only/transcript/chapter detection, exactly as today.

**No mass re-transcription.** Existing transcripts and covered ranges stay valid;
`ScanPlanner` keeps skipping covered audio, so the new model only transcribes episodes
(or windows) first scanned after the update. `MODEL_VERSION` in
`AdScanController` is set from `AsrModelRegistry.modelDirName` (today it is a
hardcoded old string), so new `episode_scans` rows record the truth. The owner can
force a re-transcription per show with the existing "relearn show" action. Re-scoring
the whole library would mean re-downloading and re-decoding every episode — a separate
opt-in feature, deliberately not part of this change.

## Failure modes

| Case | Behavior |
|---|---|
| User presses end with < 5 s elapsed | ignored, still armed, short event message |
| User seeks before the start marker | discarded with a message, stays armed |
| Rescan after a manual mark | manual ad preserved, no duplicate |
| Manual mark overlaps a detected ad | manual wins, no double skip |
| Two manual ads overlap | newer wins |
| Nudge would shrink below 5 s or cross the episode end | nudge rejected, row unchanged |
| Episode switch with ASR off | previous episode's ads cleared, only the new episode's USER ads load |
| Hotwords rejected by the model | recognition continues without hotwords, one log line |
| New model download fails midway | checksum/size mismatch, model not marked installed, old files cleaned at next attempt |
| ASR disabled, user marks an ad | mark stored and skipped (manual path is ASR-independent) |
| Endpoint splits "sponsored" / "by AG1" | still detected (existing test) |

## Testing

Unit (JVM):
- `TextMatchTest`: normalization of digits/percent/ampersand; fuzzy hit/miss at the
  threshold; phonetic key stability; "brought to you buy", "square space",
  "one eight hundred" cases.
- `AdCueDetectorTest` additions: fuzzy start, fuzzy end, digit URL, false-positive
  guard in the first 8 s, split cue (existing), promo+brand fuzz.
- `ManualAdMarkTest`: arm/close/cancel reducer; 5 s minimum; end-before-start;
  15-minute cap logging; id generation; nudge boundaries (±1 s, 5 s floor, episode
  end clamp, rejected nudge is a no-op).
- `AdEpisodeLoadTest`: ASR off loads only USER ads; ASR on loads all confirmed ads;
  switching episodes never keeps the previous episode's ads.
- `AdScanControllerMergeTest`: USER ad survives a detected overlapping ad; newer
  manual ad wins over older manual ad.
- `AdSkipDeciderTest`: confirmed USER ad skips; unconfirmed USER ad never exists
  (construct it anyway and assert no skip).
- `HotwordListTest`: dedupe, uppercase, 100-line cap, stable order.

Instrumented (device, optional): `SherpaRecognizerInstrumentedTest` still decodes
`asr_sample.wav`; add one assertion that `createStream(hotwords=…)` does not throw.

Build gate: `./gradlew :app:testDebugUnitTest` and
`./gradlew :app:assembleDebug`.

## Rollout

- All work on `feat/asr-ad-pattern`; no schema change, so no destructive migration.
- Version bump of the model means existing users re-download on next enable; the
  settings card shows 73 MB before the tap.
- If the owner later wants accuracy beyond this, the next step is B (2-pass) plus a
  measured WER baseline, not more fuzzy thresholds.

# Design: Real On-Device Ad Detection with Learned Per-Show Ad Slots

Date: 2026-10-31
Status: Draft for approval

## Intent

Replace LabCast's simulated ad detection with a real, offline, English-only
speech-to-text pipeline, and cut its CPU and battery cost by learning where
each podcast show places its ads. The first episode of a show is transcribed
in full; later episodes only transcribe narrow windows around the learned ad
slots and confirm with speech cues before skipping.

Why: the current implementation fabricates transcripts, waveforms, and ad
timestamps (`VozTranscriber`, `AudioWaveAdDetector`, `PodcastAdPatternEngine`
defaults, hard-coded `adTimestampsSeconds`). It skips arbitrary parts of the
real episode. The owner listens to English-language podcasts and wants a
trustworthy skip that does not require full transcription of every episode.

## Goals

1. Convert the actually playing episode's audio to timestamped English text
   on-device, with no cloud calls and no audio leaving the phone.
2. Detect sponsor/ad boundaries from that text and skip them precisely.
3. Learn a per-podcast show template of ad slots (pre-roll, mid-rolls) from
   detected segments and from free sources (RSS transcripts and chapters).
4. On later episodes of the same show, transcribe only the predicted ad
   windows plus the opening minutes. Target: under 15 % of the audio that a
   full transcription would process.
5. Never skip on timing alone. A skip requires a speech cue inside the
   predicted window.
6. Make the learned template visible and editable (the user's choice, "D":
   user-confirmed template).
7. Keep playback smooth on the connected device (Samsung SM-G998B, Android
   15, arm64-v8a) and survive screen-off playback.

## Non-goals

- German or any non-English transcription in this version.
- Live-stream/radio support. Episodes must be seekable over HTTP Range or
  downloaded locally.
- Cloud transcription, accounts, or sync of transcripts.
- Detecting ads inside the app's own UI or other apps' audio.
- Per-word speaker diarization.
- Replacing the player with Media3. Playback stays on `MediaPlayer`.

## Anti-goals (failure even if it works)

- Skipping content that was never confirmed as an ad.
- Processing the full episode on every play "just in case".
- A background service that keeps the CPU busy after playback stops.
- Hiding uncertainty: the UI must distinguish learned, confirmed, missed,
  and disabled slots.
- Shipping a model inside the APK without the user's consent.

## Constraints

- Kotlin 2.0.21, AGP 9.1.1, compileSdk 36, minSdk 24, Room 2.7.0.
- `dependencyResolutionManagement` uses `FAIL_ON_PROJECT_REPOS`; JitPack must
  be added there for `com.github.k2-fsa.sherpa-onnx:sherpa-onnx`.
- Existing Room DB is version 6 with `fallbackToDestructiveMigration()`.
  Bumping to 7 may wipe local data; acceptable, but must be called out in the
  release notes.
- Playback uses `android.media.MediaPlayer` via `PodcastAudioManager`; there
  is no PCM tap today.
- The app already has a foreground playback service and an `EpisodeDownloader`
  with local file paths.
- The model is downloaded on first enable, not bundled. Apache-2.0 runtime and
  model; the app must show the license and the download size.

## Chosen approach: A + D

**A. Ratio slots + confirmation windows.** Ads are stored per show as a start
ratio, a duration, and an absolute anchor with variance. Later episodes scan
only padded windows around those predictions and must hear a cue.

**D. User-confirmed template.** The learned slots are visible in the podcast
detail screen. The user can edit boundaries, disable a slot, or force a
re-learn. This is a safety valve for shows whose ad layout changes.

### Alternatives considered and rejected

- **Absolute timestamps only.** Breaks when episode lengths or ad lengths
  change. Kept only as a secondary anchor inside a slot.
- **Audio fingerprint reuse.** Would avoid ASR on later episodes, but still
  decodes the whole file and fails for re-read host ads. Deferred.
- **Media3 `TeeAudioProcessor` tap.** No look-ahead, so the end of an ad is
  unknown when the start arrives. Kept as a fallback only if range requests
  are unsupported.
- **MediaProjection / `AudioPlaybackCapture`.** Consent dialog, Android 14+
  restrictions, no look-ahead. Rejected.
- **Tiny LLM for classification.** An LLM is the wrong layer; deterministic
  cue rules are testable, instant, and free. An LLM may be added later only
  for ambiguous windows.

## Architecture

### 1. Speech engine

- Runtime: `sherpa-onnx` via JitPack (`com.github.k2-fsa.sherpa-onnx:sherpa-onnx`,
  pinned version, arm64-v8a only), Kotlin/Java API.
- Model: streaming Zipformer English, int8
  (`sherpa-onnx-streaming-zipformer-en-2023-06-26`, or the `en-20M` variant
  if the former exceeds the size budget) + `tokens.txt`, plus Silero VAD
  (`silero_vad.onnx`).
- Model files live in `filesDir/models/<modelVersion>/`, downloaded on first
  enable with a progress UI, SHA-256 verified, resumable where possible,
  Wi-Fi-only by default.
- Recognizer config: 16 kHz mono, 2 threads, greedy search, endpointing on,
  VAD enabled. A single recognizer instance is reused; streams are reset per
  scan region.
- A `SpeechRecognizerEngine` interface wraps sherpa so tests can use a fake
  and a future engine can replace it.

### 2. Audio source and look-ahead

`AdAudioSource` produces 16 kHz mono PCM for arbitrary episode ranges.

- **Downloaded episode:** `MediaExtractor` on `downloadLocalPath` +
  `MediaCodec` decode + resample.
- **Streaming episode:** `MediaExtractor.setDataSource(MediaDataSource)` with
  an `HttpRangeMediaDataSource` backed by OkHttp Range requests and a sparse
  on-disk cache in `cacheDir/asr/<episodeId>/`. One reader thread serves
  extractor reads; requests are fetched in 1–2 MB chunks.
- **Fallback:** if the server ignores Range (HTTP 200 instead of 206) and the
  episode is not downloaded, run the "real-time tap" mode: decode only at the
  playhead and skip after the cue (coarser, still real).
- **Look-ahead policy:** keep the transcript 180 s ahead of the playhead,
  pause decoding when ahead, resume on seek or speed change. Pre-scan the
  first 120 s before playback starts (or while the player buffers) so
  pre-rolls can be skipped before they are heard.
- **Seeking:** cancel the current scan, keep all already-produced segments,
  restart at the new playhead minus 15 s. Backward seeks reuse the cached
  transcript and rescan only uncovered ranges.
- **Playback speed:** the required decode rate scales with speed; the worker
  simply processes faster or slower. At 2x speed the look-ahead shrinks, and
  the engine falls back to the no-look-ahead rule in §7.
- **Duration:** the decoded/MediaPlayer duration is canonical. It is stored on
  `EpisodeScanEntity.actualDurationMs`; `EpisodeEntity.durationSeconds` is
  updated only when the decoded value differs by more than 5 s and is > 0, so
  progress and slot ratios agree. If no decoded duration exists yet, the feed
  duration is used and prediction windows get an extra ±60 s of padding.
- **Network:** scanning may use Wi-Fi or mobile data; there is no Wi-Fi gate.
  Scanning a non-downloaded episode fetches bytes separately from
  `MediaPlayer`, roughly doubling data for the scanned regions. This is
  accepted by the owner; the look-ahead cap keeps it bounded.

### 3. Transcript

- Output: timestamped segments `(startMs, endMs, text, confidence)` and a
  rolling word list where the model provides it.
- Segments are stored in a new `EpisodeAsrSegmentEntity` (one row per segment)
  with end time, confidence, and source. This is the source of truth for
  resume, seek, and windowed scans, so covered audio is never decoded twice.
- `EpisodeEntity.transcript` remains a human-readable projection
  (`MM:SS [ASR] text`) for the existing transcript screen and for export.
- Precedence: a feed transcript and an ASR transcript are kept separately.
  `refreshMetadataAfterwards` may not replace ASR segments with feed text once
  an ASR scan exists; the feed transcript is used only as bootstrap evidence
  and as a fallback display when ASR has not run. The transcript screen labels
  the source (`Feed` vs `ASR`).
- Add `EpisodeScanEntity` for scan bookkeeping: mode, status, covered ranges,
  model version, actual duration, updatedAt.

### 4. Cue detection

`AdCueDetector` is deterministic and unit-tested.

- Start cues: "sponsored by", "brought to you by", "support for this podcast
  comes from", "a word from our sponsors", "our sponsor today", "thanks to
  our sponsor", "this episode is sponsored by", "promo code", "use code",
  "free trial", "ad break", "commercial break", plus the existing brand list.
- End cues: "back to the show", "back to our conversation", "back to the
  podcast", "now back to", "let's get back to", "welcome back".
- A start requires one strong cue or a brand plus promotional language.
  A weak brand mention alone does not open an ad.
- Boundaries are trimmed to the surrounding VAD speech edges.
- End resolution order: (1) an end cue, (2) a strong "resume" sentence that is
  not promotional for 15 s, (3) maximum ad length of 180 s. Case (3) is marked
  `unconfirmed` and never used for auto-skip on a new show until a cue-based
  detection has confirmed the same slot at least once.
- Ads with no detected start are never skipped.

### 5. Show slots

`AdSlotEntity` (per podcast):

| Field | Meaning |
|---|---|
| `podcastId` | show key |
| `slotIndex` | stable order, 0 = pre-roll |
| `startRatio` | mean start / episode duration |
| `startMsAnchor` | mean absolute start time |
| `durationMs` | mean detected ad duration |
| `startVarianceMs` | observed spread of starts |
| `hits`, `misses` | confirmation statistics |
| `confidence` | derived 0..1 |
| `source` | `ASR`, `TRANSCRIPT`, `CHAPTER`, `USER` |
| `enabled` | user/auto toggle |
| `updatedAt` | last evidence |

Learning:

- Every detected ad becomes a candidate. Cluster by proximity:
  match if `|startMs - anchor| <= max(45 s, 0.02 * duration)` or
  `|startRatio - ratio| <= 0.02`.
- Update means with a running average; update variance; increment hits.
- Pre-roll is any ad whose start is < 90 s and whose ratio < 0.03; it is
  stored with `startMsAnchor = 0`.
- Slots are only *predicted* when `confidence >= 0.5` and `enabled`.
- A slot that misses its cue increments `misses` and lowers confidence. Three
  consecutive misses disable it. If all slots of a show miss in two
  consecutive scanned episodes, schedule a full re-learn.
- **User authority:** a slot with `source = USER` has frozen boundaries.
  Automatic detections still update its hit/miss statistics but never move its
  start or end. A slot the user disabled stays disabled through learning and
  re-learn; only the user re-enables it. "Re-learn this show" rebuilds only
  automatic slots and preserves `USER` and disabled slots.

Bootstrap priority (cheapest first):

1. `<podcast:transcript>` parsed into timestamped segments.
2. Real chapter markers whose titles are sponsor/ad-like.
3. Full ASR scan of the first playable episode.

If 1 or 2 yields at least one sponsor-like interval, the show starts in
`WINDOWED` mode immediately; a full ASR scan is not paid for. Those intervals
seed **candidate** slots only. Chapter- and transcript-derived candidates are
never auto-skipped until an ASR cue confirms the slot at least once (this keeps
the "never skip on timing alone" rule). Full ASR is required only when neither
source exists, when the user forces a re-learn, or after invalidation.

The old synthetic chapter and `adTimestampsSeconds` generation is deleted so
it cannot masquerade as evidence.

### 6. Scan modes

- `BOOTSTRAP_FULL`: first episode of a show when no feed transcript or sponsor
  chapter is available, or after invalidation. Transcribe
  the whole episode progressively while it plays; persist segments; learn
  slots at the end. Coverage is the whole episode, not only ahead of the
  playhead: the worker scans `0..min(duration, playhead + 180 s)` first, then
  the remaining ranges in ascending order, throttled so playback stays
  smooth. If the user pauses, the worker pauses. If the episode ends before
  coverage is complete, the scan is marked `PARTIAL` and resumes on the next
  play or on Wi-Fi + charger. Slots are learned from covered regions only.
- `WINDOWED`: later episodes. For each predicted slot scan
  `[start - 30 s, end + 60 s]`, extending in 60 s steps up to 180 s if the
  start cue is found but the end cue is not. Always scan the first 120 s.
- `FULL_MANUAL`: user taps "Re-learn this show". Rebuilds automatic slots;
  `USER` slots and user-disabled slots are preserved.
- A full scan may run on Wi-Fi and charger in the background after the first
  listen, but only while the playback service is active or the user started it.

### 7. Skip execution

- Replace the four current detectors (fake transcript, fake waveform, fake
  chapters, hard-coded timestamps) with one path fed by
  `EpisodeAdSegmentEntity`.
- `PodcastViewModel.checkSponsorAndAdDetection` consults segments with
  `confirmed = true`; it seeks to `endMs` once, logs the real saved duration,
  and updates statistics. No 15 s "guess" skip.
- **No-look-ahead rule:** if the playhead reaches a predicted ad before the
  end cue is known, do not seek to the ad start. Keep playing and scanning;
  as soon as the end cue is recognized, jump from the current position to the
  end. The listener may hear the first seconds of the ad, but the app never
  lands mid-content. Pre-scanning the opening 120 s makes this rare for
  pre-rolls; windowed scanning makes it rare for mid-rolls.
- The existing UI banner shows the real label and measured savings. While a
  known ad is being awaited, the player shows "Ad erkannt – Ende wird
  gesucht…" instead of a fake skip message.
- If the end remains unknown at the 180 s cap, the segment is stored as
  `unconfirmed` and is not auto-skipped.

### 8. UI

- Podcast detail: "Werbe-Slots" section listing each learned slot with its
  time range, confidence, and hit/miss counts. Actions: edit boundaries,
  disable, delete, re-learn show.
- Player: real ad markers on the progress bar; a scan indicator (idle,
  scanning, learned).
- Settings: "On-device ad detection (English)" toggle; model status/download;
  battery threshold; delete model; license text.
- First enable shows the model size (~30–50 MB) and requires confirmation.
- Remove all "Voz AI" / "AI Engine" claims and fabricated counters; keep only
  real, measured values.

### 9. Data model changes (Room v7)

- `AdSlotEntity` (new)
- `EpisodeAdSegmentEntity` (new): `id`, `episodeId`, `podcastId`, `startMs`,
  `endMs`, `label`, `confidence`, `source`, `confirmed`, `createdAt`
- `EpisodeAsrSegmentEntity` (new): `id`, `episodeId`, `startMs`, `endMs`,
  `text`, `confidence`, `source` (`ASR` / `FEED`), `createdAt`. Index on
  `(episodeId, startMs)`.
- `EpisodeScanEntity` (new): `episodeId` PK, `mode`, `status`,
  `coveredRanges` (serialized), `modelVersion`, `actualDurationMs`,
  `durationSource`, `updatedAt`
- DAO methods for upsert/query by podcast and episode, and for reading
  segments that overlap a time range.
- `EpisodeEntity.transcript` remains as a derived display field; no schema
  change there.

## Performance budget (SM-G998B)

- Streaming Zipformer int8, 2 threads: RTF 0.05–0.15 → a 90-minute episode is
  5–14 minutes of CPU for a full scan.
- Windowed mode (pre-roll 120 s + 2 slots × ~2.5 min): ~7 minutes of audio
  processed → ~30–60 s CPU per episode. Target < 15 % of full-scan work.
- VAD removes silence before recognition; paused playback stops the worker.
- Look-ahead is capped at 180 s, so network duplication is bounded.
- Measured with `adb shell dumpsys batterystats` over a 10-minute playback
  window and with `top`/`dumpsys cpuinfo` before release.

## Failure modes and handling

| Failure | Handling |
|---|---|
| Model missing or checksum fails | Feature disabled, clear error log, no skip, retry button |
| No network for download | Feature stays off; playback unaffected |
| Server ignores Range | Real-time tap fallback; no look-ahead |
| Episode shorter/longer than template | Ratio + absolute anchor matching; window confirmation |
| Ad placement shifts | Miss counters disable slot; full re-learn after two bad episodes |
| Host-read ad varies each episode | Cue-based detection still works in the window; slot updates |
| End cue never spoken | Stop at 180 s, mark unconfirmed, no auto-skip until confirmed once |
| User seeks inside an ad | Segment lookup still applies; seek maps to end |
| Screen off | ASR runs inside the existing foreground playback service |
| App killed | Scan state and covered ranges persist; resume next play |
| Non-English episode | `language = en`; no ads detected rather than false positives |
| Battery saver / low battery | Windowed scans pause; feature degrades to cue-only near playhead |

## Privacy

- Audio decoding, recognition, and pattern learning happen entirely on-device.
- No audio, transcript, or listening data leaves the phone.
- The only network use is fetching the model from a pinned URL and the normal
  episode audio.

## Testing

1. **Unit (JVM):** cue detector cases, slot clustering/merging, prediction
   windows, miss/disable logic, transcript serialization, regression tests for
   "no cue → no skip".
2. **Fake engine:** a `SpeechRecognizerEngine` fake feeding canned transcripts
   to test scan modes and skip decisions without audio.
3. **Instrumentation (SM-G998B):** decode a short public MP3, assert PCM
   format (16 kHz mono), run a real 60–120 s sample, assert words and
   timestamps.
4. **End-to-end acceptance on device:**
   - Play episode A of a show: full scan, ads found and skipped, slots saved.
   - Play episode B: log shows `WINDOWED`, processed audio < 15 %, pre-roll
     skipped, no content skip.
   - Delete the template, play B: full scan again.
   - Silence the phone during an ad: no false skip.
   - Screen off for 10 minutes: playback continues, CPU stays bounded.
5. **Battery check:** `batterystats` delta on windowed vs full scan; document
   numbers in the PR.

## Rollout

- Feature flag default off until the user enables it and downloads the model.
- Version bump and release notes must mention the Room v7 reset and the new
  download.
- Old fake engines are deleted in the same release so no path can fall back to
  synthetic data.

## Decisions taken by the builder (my calls)

- First episode: full transcription while listening, progressive skips, slots
  saved at the end; later episodes are windowed. (Default proposed and not
  overridden.)
- Look-ahead 180 s; window padding 30 s before / 60 s after; max ad 180 s.
- Slot confidence threshold 0.5; disable after 3 consecutive misses; re-learn
  after 2 fully missed episodes.
- Model downloaded on first enable; nothing bundled. No Wi-Fi gating: the
  owner has mobile traffic covered, so scanning and model download may use
  either connection.
- Slots store ratio + absolute anchor + variance, not a single timestamp.
- Delete `VozTranscriber`, `VozModelManager`, and the synthetic parts of
  `AudioWaveAdDetector` rather than leaving them as a fallback.
- Playback stays on `MediaPlayer`; no Media3 migration in this version.
- ASR segments live in their own table and are the source of truth; the
  episode transcript field is only a display projection. Feed transcripts
  never overwrite ASR results.
- Decoded duration is canonical; feed duration is a fallback with extra
  window padding.
- Feed transcripts and real sponsor chapters bootstrap a show into windowed
  mode without a full ASR scan, but their intervals are candidates only and
  require one ASR cue confirmation before they can auto-skip.
- User-edited slots are frozen and user-disabled slots stay disabled across
  learning and re-learn; re-learn rebuilds automatic slots only.
- When the end of an ad is unknown, the app keeps playing and jumps when the
  end cue arrives rather than seeking to the ad start.
- Bootstrap scans the whole episode eventually, starting with
  `0..playhead+180 s`; incomplete scans resume as `PARTIAL`.
- Network: scanning is allowed on Wi-Fi and mobile data without gating;
  duplicate range reads are accepted. Real-time tap remains only the
  no-Range fallback.

## Left to the builder

- Pin exact sherpa-onnx version and model file URLs/checksums.
- Choose between `en-2023-06-26` and `en-20M-2023-02-17` after measuring size
  and accuracy on the device.
- Implement `HttpRangeMediaDataSource` and its cache eviction.
- Tune cue thresholds and VAD trimming against three real shows.
- Visual design of the slot editor.
- Exact serialization format for covered ranges and ad segments (JSON via
  Moshi is acceptable; the project already depends on Moshi).

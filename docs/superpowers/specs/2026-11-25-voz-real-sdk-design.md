# Design: Replace fake Voz with the real Desert Ant Voz SDK

Date: 2026-11-25
Status: Draft for approval

## Intent

Replace LabCast's fabricated "Voz" code with the real `ai.desertant:voz` SDK.
Transcribe short, predicted windows of episode audio on-device with genuine word
timestamps, publish results incrementally while the episode plays, and only
auto-skip ads that were actually heard in a cue. No invented transcript text,
no invented timestamps, no key.

Why: `VozTranscriber.kt` and `VozModelManager.kt` never touch audio. They parse
`EpisodeEntity.transcript`, divide segment length by word count to fabricate
word times, and load a `voz.onnx` asset that does not exist through raw ONNX
Runtime. `PodcastViewModel` runs this on every episode and persists the result.
The SDK the owner asked for is a different product: a 337 MB LiteRT model that
returns real `text`, `start`, `end`, and `duration`.

## Confirmed SDK facts (do not guess these)

Source: repo README, `docs/models/voz.md`, `docs/setup.md`, and the SDK's
`packages/voz-kotlin/src/main/kotlin/ai/desertant/voz/Voz.kt` on `main`.

- Install: `implementation(platform("ai.desertant:bom:6.0.0"))` and
  `implementation("ai.desertant:voz")` (Maven Central). Version 6.0.0.
- Requirements: API 24+, Kotlin 2.3+, arm64-v8a / x86_64. Keep
  `libLiteRtClGlAccelerator.so` in the APK or every device falls back to CPU.
- `ai.desertant.voz.Voz(context: Context, directory: String? = null) : AutoCloseable`
- `suspend fun transcribe(samples: FloatArray, sampleRate: Double = 16_000.0): Transcription`
- `Transcription(text: String, words: List<Word>, duration: Double)`
- `Word(text: String, start: Double, end: Double)` — seconds, 80 ms resolution.
- `fun isDownloaded(): Boolean`, `suspend fun download()`, `override fun close()`.
- `const val SAMPLE_RATE: Double = 16_000.0`, `class VozException(message: String)`.
- `ai.desertant.core.DesertAnt.configure(apiKey = "...")` exists but is **not
  used**: this is a personal, non-commercial app, so Development Use / research.
- Voz does not detect language; the owner listens to English podcasts. Audio in
  an unsupported language produces confident nonsense, so never claim detection.
- The License Check runs a network request when a model loads (sends install
  id, app id, model/version, SDK version, platform — no user content). The app
  already has `INTERNET` permission. The license requires visible attribution;
  add one "Powered by Desert Ant Labs" line in Settings linking to desertant.com.

## Goals

1. Real `Voz` SDK dependency, pinned through the BOM, builds on the project.
2. Real PCM from episode audio (downloaded or streamed) reaches `transcribe`.
3. Stored word timings and skip bounds come only from SDK output.
4. Scanning is incremental and bounded: windows around predicted ad slots,
   not the whole episode, published as each window finishes.
5. Skip requires a sponsor cue actually heard in the scanned audio.
6. Completed windows are cached so later plays do not rescan them.

## Non-goals

- API key, accounts, billing, server deployment.
- Full-episode transcript on demand.
- Language detection, non-English accuracy claims, multilingual UI.
- Changing playback from `MediaPlayer`, Media3 migration, UI redesign.
- Bundling the model in the APK; it downloads on first use (~337 MB).

## Anti-goals

- Fabricated word timestamps or transcript text, including as a fallback.
- Auto-skip based only on a learned time window with no heard cue.
- Fake "AI engine" log lines implying analysis that did not happen.

## Constraints

- Kotlin 2.0.21 and KSP `2.0.21-1.0.28` today; bump Kotlin to 2.3.21 and KSP
  to a 2.3.x pairing (verify by build). AGP 9.1.1, Gradle 9.3.1, minSdk 24.
- Room DB version 8 with migrations and destructive fallback.
- `EpisodeDownloader.downloadToFile` writes to `filesDir/podcasts/<id>.mp3`.
- `PodcastViewModel.refreshMetadataAfterwards` runs on `Dispatchers.IO`.
- `docs/superpowers/specs/2026-10-31-asr-ad-pattern-design.md` (draft) already
  chose windowed scanning, 180 s look-ahead, 30 s before / 60 s after padding,
  no skip on timing alone, and deletion of the fake engines. Keep those rules.

## Approaches considered

1. **Downloaded episodes only** — decode a local file, skip real STT for
   streamed episodes. Cheapest, but streamed plays get no Voz results. Rejected:
   the owner said network cost is not an issue.
2. **HTTP-range decoder feeding windows** — implement a seekable
   `MediaDataSource` over OkHttp ranges. More code and failure modes than
   needed; rejected in favour of reusing the existing resumable downloader.
3. **Chosen: background download of the audio, then the same decoder for both
   sources.** For a streamed episode, `EpisodeDownloader` copies the audio into
   an ASR cache folder in the background; the decoder then sees an ordinary
   local file. Network cost is acceptable, CPU cost is identical to option 1,
   and no new network/seeking layer is needed.

## Architecture

### Build

- Bump `kotlin` and `googleDevtoolsKsp` in `gradle/libs.versions.toml`.
- Add the BOM and `ai.desertant:voz` in `app/build.gradle.kts`; remove
  `com.microsoft.onnxruntime:onnxruntime-android:1.32.0` (only the fake manager
  uses it).
- Bump `versionCode` to 36 and `versionName` to 1.6.1 per repo convention.
- Do not exclude LiteRT native libs from packaging.

### Audio acquisition and decoding

- Add an optional destination directory parameter to `EpisodeDownloader`.
- `VozAudioSource`: if `EpisodeEntity.isDownloaded` and the local file is valid,
  use `downloadLocalPath`; otherwise download into `filesDir/asr-cache/<id>.mp3`
  on `Dispatchers.IO` and use that. Do not flip `isDownloaded` — the ASR cache
  is a scan artifact, not a user-downloaded episode.
- `VozAudioDecoder`: `MediaExtractor` + `MediaCodec` select the audio track,
  decode to PCM 16-bit, downmix stereo to mono, linearly resample to 16 kHz,
  and return a `FloatArray` for a requested `[startMs, endMs]` window.
  Seek by `MediaExtractor.seekTo(startMicros, SEEK_TO_PREVIOUS_SYNC)`.
- Window size 60 s = 3.84 MB at 16 kHz float32; 3 s overlap for word continuity.

### Window planning

`VozScanPlanner.plan(episode, durationMs, candidates): List<ScanWindow>`:

1. Opening 0–180 s (pre-roll).
2. Padded candidates from `PodcastAdPatternEngine.predictEpisodeAdSkips`,
   `PodcastKeywordSpotter.parseAdIntervalsFromTranscript`, and manual ranges:
   30 s before / 60 s after each, clamped to duration.
3. Post-roll: last 120 s when the learned template has a post-roll.
4. Merge overlaps, cap at 12 windows and at ~20 % of episode duration.

Predicted windows only choose *where to listen*. They never become skips.

### Scanning and publication

- `interface VozEngine` (isDownloaded / download / transcribe / close) wraps the
  SDK so unit tests can use a fake. `VozSdkEngine` is the real implementation.
- `VozScanController`: one `Voz` instance per scan session, `download()` once if
  needed, sequential windows on `Dispatchers.IO`, cancelled when the episode
  changes or playback stops. Windows are scanned in order while playback runs;
  results arrive as each window completes, not as one final batch.
- Word de-duplication in overlap: keep the word whose absolute start is >= the
  window boundary, drop duplicates within 300 ms with identical text.
- Absolute time: `startMs = (word.start + window.startSeconds) * 1000`.
- Map SDK words to existing `WordTimestamp` using the real start/end only.
  Group into `SentenceSegment`s on pauses > 600 ms or > 12 words; speaker is
  the constant `"Speaker"` (Voz has no diarization).
- Sponsor detection runs over the real window text with the existing
  `SPONSOR_TRIGGER_PHRASES` and `SPONSOR_BRANDS`. A brand mention alone is not
  enough to open a skip (it can occur in normal content); a start phrase or
  promotional language is required, and a brand only strengthens the match.
- A skip range opens at the start cue. It closes at a spoken resume cue
  ("back to the show" and the existing resume patterns) if one is heard in the
  same window. If no resume cue is heard, emit only a visual marker with
  `isAutoSkipEligible = false` — never guess an end from elapsed time and never
  auto-skip on a start cue alone. This keeps the spec's rule "never skip on
  timing alone".
- `SkipRange` bounds come from the matched SDK words, padded to the sentence;
  adjacent cue hits merge. `durationMs` stays a property, never the closer.

### Persistence

- New Room entity `AsrWindowEntity(episodeId, startMs, endMs, wordsJson,
  modelVersion, audioIdentity, updatedAt)` with DAO upsert/query, DB version 9,
  migration creating the table. `modelVersion` is the SDK 6.0.0 pin;
  `audioIdentity` is the audio file length + last-modified time (or URL hash).
  Cached windows whose version or audio identity no longer matches are ignored
  and rescanned.
- Completed windows are saved as JSON via the existing Moshi dependency. On a
  later play, load covered windows and schedule only missing ones.
- Leave `EpisodeEntity.transcript` as the feed/display projection; do not
  overwrite it with partial ASR output.

### ViewModel and cleanup

- Replace the fake call block in `refreshMetadataAfterwards` with starting a
  `VozScanController`, collecting incremental results into existing
  `_detectedTranscriptAds` and a new `_vozTranscript` StateFlow.
- No new composable this round: real Voz text is held in the ViewModel and used
  for cue detection and bounds, not shown in a new screen. Manual ad marking
  keeps operating on the feed transcript as it does today.
- Combine the real skip ranges with manual and transcript-derived intervals as
  today. Remove the writes of fabricated transcripts into `currentEp`.
- Replace `[VOZ CONSOLE]` / "Voz AI Engine" synthetic log text with real status
  ("Voz: window x/y, N words, M cues").
- Delete `VozModelManager.kt` and the old `VozTranscriber.kt`. Update
  `MockDataRemovalTest` to assert that an episode with no audio produces no
  segments and no skips (rather than calling the fake transcriber).

## Verification

- `./gradlew :app:compileDebugKotlin` and unit tests pass after the Kotlin/KSP
  bump; fix any KSP pairing issue the build reports.
- New unit tests: window planner coverage/overlap/caps; absolute time offsets;
  word-to-sentence mapping preserves real times; cue detection emits nothing
  without a cue; persisted windows are skipped on later plans.
- Manual on-device check with one downloaded episode: first run downloads the
  model, later runs use cache; word times align with playback; a sponsor cue in
  the opening window produces exactly one skip range. This step needs the
  337 MB download and is reported as manual verification, not claimed as run.

## Decisions taken by the builder (my calls)

- **First play:** playback is never gated and never waits for the model or the
  audio copy. The owner explicitly accepts that the full transcript is not
  there right away, so the opening ad may only be skipped on a later play once
  its window has been cached. When the scanner falls behind the playhead it
  keeps scanning; it does not seek playback.
- **Model download:** automatic and silent on first scan, with a log line and a
  model-status line in Settings. No opt-in dialog, no blocking, no key.
- **Brand mentions:** insufficient on their own to open a skip (see above).
- **Cached words:** keyed by model version and audio identity (see above).
- **UI:** no new screen or composable this round.

## Left to the builder

- Final window/overlap constants (start at 60 s / 3 s), cue padding, and the
  cache eviction policy for `filesDir/asr-cache`.
- Exact KSP 2.3.x patch pairing; resolve by building.
- Whether to expose a "Rescan episode" action in Settings (not in this change).

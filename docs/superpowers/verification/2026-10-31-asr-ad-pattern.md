# ASR Ad Detection — Verification Record

Date: 2026-10-31
Device: Samsung SM-G998B (Galaxy S21 Ultra), Android 15 (SDK 35), arm64-v8a
Build: debug, applicationId `com.aistudio.labcast.podcast.dev` (installed alongside the existing release app)

## Automated checks run

| Check | Command | Result |
|---|---|---|
| JVM unit suite | `./gradlew :app:testDebugUnitTest` | PASS |
| Debug build | `./gradlew :app:assembleDebug` | PASS |
| sherpa-onnx model + recognizer smoke test | `SherpaRecognizerInstrumentedTest` | PASS — real download, real transcript, timestamped segments |
| PCM decode from local audio | `MediaPcmSourceInstrumentedTest` | PASS — MediaExtractor/MediaCodec → 16 kHz mono |
| Model installation | Settings screen | PASS — 42 MB model downloaded, “Model ready” shown |

## What the automated tests prove

- The pinned model files download and hash correctly.
- sherpa-onnx loads on the phone and produces English text with timestamps.
- Episode audio decodes to 16 kHz mono through `MediaCodec`.
- Cue detection and slot learning pass their unit tests.
- Windowed planning stays under 15 % of a one-hour episode in the controller test.
- A skip is only produced for a confirmed segment; unconfirmed segments await the end cue.

## Pending: end-to-end playback test (owner)

The owner will run these on the phone:

1. Open **LabCast** with the `.dev` package (the debug build), Settings → on-device ad detection is enabled and the model is ready.
2. Play one episode of a show with no feed transcript.
   - Expected Logcat: `AdScan: run planned mode=BOOTSTRAP_FULL …`
   - Expected: confirmed ad markers, a real skip banner, measured saved seconds.
3. Play a second episode of the same show.
   - Expected Logcat: `AdScan: run planned mode=WINDOWED …` and a much smaller `coveredMs`.
   - Expected: only the learned windows are scanned; no content before/after is skipped.
4. Turn auto-skip off, then disable one slot in the show's “Werbe-Slots” list.
   - Expected: no seek in either case.
5. Kill and relaunch mid-episode.
   - Expected: scan resumes without duplicate segments.

## Known limitations at hand-off

- Full playback E2E and battery numbers are not measured yet; the owner is testing.
- The debug build uses `.dev` so it does not overwrite the installed `com.aistudio.labcast.podcast` app or its data.
- The phone's connection to `dcs-cached.megaphone.fm` was intermittent during testing; the PCM test was moved to a local generated sample so it is not network-dependent.
- Version code/name are unchanged (`22` / `1.3.9`) until the owner confirms the E2E result.

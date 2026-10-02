# 4-Phase Implementation Plan: Audio Ad Skipping, Music Intro Detection & Strict Issue Logging

This document outlines the four phases of implementation for LabCast:

---

## Phase 1: Settings Log Clean-Up (Strict Issues & Errors Only)
- **Objective**: Ensure the Settings "App-Logs & Protokollierung" section records **only** errors, warnings, and actual issues that happen during app interaction, removing routine info logs (e.g. play/pause/seek events, normal sync logs, sleep timer countdowns).
- **Components**:
  1. Add `isErrorOrIssue: Boolean` and `logLevel: String` ("ERROR", "WARN", "INFO") to logging models (`SyncLogEntity`).
  2. Guard `repository.addSyncLog()` so normal informational logs are not persisted to the issues log.
  3. Capture real errors:
     - ExoPlayer `PlaybackException` (network stream drops, decoder errors).
     - Network timeouts & HTTP errors during podcast search or RSS fetching.
     - Offline download failures & storage errors.
     - Audio focus losses / unexpected interruptions.
     - GitHub OTA update check/download exceptions.
  4. Redesign Settings UI:
     - Filter by category: `[ALL ISSUES]`, `[AUDIO PLAYER]`, `[NETWORK]`, `[DOWNLOADS]`, `[OTA]`.
     - Display timestamp, severity badge (`ERROR`, `WARN`), component tag, and error reason.
     - Show clean empty state when no issues exist: *"Keine Fehler oder Probleme festgestellt (All systems operating normally)"*.
     - Include a "Test-Fehler protokollieren" (Log Test Error) button for diagnostics.

---

## Phase 2: Intro Music / Jingle Skipper (e.g., Art of Manliness "AoM")
- **Objective**: Automatically detect or allow skipping of musical intros/jingles that precede speech in podcasts like *The Art of Manliness (AoM)*.
- **Components**:
  1. **Configurable Per-Podcast Intro Skip**:
     - Add `introSkipSeconds: Int` to `PodcastEntity` (default 0; pre-set to 32 seconds for *Art of Manliness*).
     - Add Intro Skip slider/toggle in `PodcastDetailScreen` and Player Settings (0–90 seconds).
  2. **Acoustic Music Intro Detection**:
     - In `AudioWaveAdDetector`, analyze initial audio waveform (0–60s) for continuous harmonic density, rhythmic frequency energy, and absence of speech cadence pauses characteristic of musical intros.
     - Identify the exact drop-off point where intro music fades into host speech.
  3. **Auto-Skip Action**:
     - When starting an episode from the beginning (`positionMs == 0`), automatically seek to the end of the intro music if enabled, accompanied by a visual banner/toast: *"Intro-Musik übersprungen (Skipped AoM Intro - 32s)"*.

---

## Phase 3: Dual-Parameter Ad Skipper ("Sponsors" → "Back to the show" + Audio Volume Analysis)
- **Objective**: Accurately detect and skip commercial ad blocks when trigger phrases occur (e.g., from *"Sponsors"* until *"and now back to the show"*), corroborated by audio volume/loudness analysis.
- **Components**:
  1. **Semantic Phrase Boundary Matching**:
     - Start Cues: *"Sponsors"*, *"Our sponsor today"*, *"Support for this podcast comes from"*, *"A word from our sponsors"*, *"Brought to you by"*.
     - End Cues: *"And now back to the show"*, *"Back to the show"*, *"Back to our conversation"*, *"Now back to [Host]"*, *"Welcome back"*.
  2. **Acoustic Volume / Loudness Parameter**:
     - Monitor moving RMS volume and dynamic range compression. Commercials are typically mastered +3dB to +5dB louder than conversational podcast speech.
     - Combined confidence: When both a start cue and a volume jump occur, flag the commercial break; seek smoothly when the exit cue is reached.
  3. **Skip Execution & Feedback**:
     - Skip immediately past the ad segment, update saved time metrics, and alert the user with an animated indicator: *"Sponsor-Block übersprungen ('Sponsors' → 'Back to the show')"*.

---

## Phase 4: Multi-Approach Audio Detection Engine (Approaches A, B, and C)
- **Objective**: Combine three synergistic detection strategies for maximum accuracy, robustness, and battery efficiency:
  - **Approach A (Timestamped RSS Transcripts)**:
    - Ingest `<podcast:transcript>` (SRT, VTT, JSON) and parse timestamped lines for commercial markers.
    - Zero battery overhead, millisecond-accurate boundary calculation.
  - **Approach B (Audio Volume & Waveform RMS Dynamics)**:
    - Real-time audio waveform energy analyzer in `AudioWaveAdDetector`.
    - Detects dynamic ad insertion (DAI) loudness wars, spectral jingle surges, and dynamic range compression.
  - **Approach C (Lightweight Keyword Spotting / Pattern Engine)**:
    - Intelligent on-device `PodcastKeywordSpotter` scanning real-time speech cues and transcript segments for trigger words with confidence thresholds.
    - Provides seamless fallback when full transcripts are unavailable.

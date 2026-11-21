# Architecture review — 2026-11-21

Read-only survey of the Android/Kotlin app. No `CLAUDE.md`, `AGENTS.md`, `ARCHITECTURE.md`, `docs/adr/`, or `docs/FIXLOG.md` was present. The app has one Gradle target (`:app`); UI, data, network, player, service, and utility code live under `com.example`. `PodcastViewModel` constructs the Room database/repository itself. Existing uncommitted changes were left untouched.

## Compose screens in one file [Strong]

Files: `app/src/main/java/com/example/ui/PodcastAppContent.kt` (5,955 lines)
Signal: A single file contains app navigation plus Discover, detail, downloads, history, player, and multiple bottom sheets.
Problem: A change to one screen means navigating a large mixed-feature file; many unrelated screen changes share a diff and review surface.
Proposal: Move existing top-level composables into feature files, preserving their signatures and behavior; keep the app shell in `PodcastAppContent.kt`.
Interface:
```kotlin
@Composable fun DiscoverScreen(viewModel: PodcastViewModel)
@Composable fun DownloadsScreen(viewModel: PodcastViewModel)
@Composable fun FullPlayerScreen(viewModel: PodcastViewModel, onClose: () -> Unit)
```
Benefits: Better locality and smaller reviews without new abstractions or behavior changes.
Proof of same behavior: Existing unit tests/build; compare existing screen snapshots if available.
Plan: Move one cohesive screen group per step; build after each move.

## Podcast state owner [Strong]

Files: `app/src/main/java/com/example/ui/PodcastViewModel.kt` (2,575 lines)
Signal: One ViewModel owns playback/ad detection, search/producers, downloads/sync, settings, history/navigation, and app updates.
Problem: These independent flows share a large state owner; modifying or testing a feature requires understanding unrelated state and lifecycle behavior.
Proposal: Extract a cohesive feature at a time only where it can own its state and dependencies; candidate first boundary is update checking, already delegated in work to `GitHubUpdateManager` but still exposes many settings/status fields here.
Interface:
```kotlin
val updateStatus: StateFlow<UpdateStatus>
fun checkForGitHubUpdates()
fun downloadUpdate()
fun installDownloadedApk(context: Context)
```
Benefits: Smaller feature ownership and more focused tests; avoid splitting purely by line count.
Proof of same behavior: Existing test/build baseline and focused state-flow tests for any extracted owner.
Plan: Choose one domain, establish its public state/actions, migrate callers, then remove forwarding from the old owner.

## Settings surface [Worth exploring]

Files: `app/src/main/java/com/example/ui/SettingsScreen.kt` (1,584 lines)
Signal: One composable file groups settings for playback, appearance, updates, network, and logs.
Problem: A local setting change can be hard to review alongside unrelated controls and state collection.
Proposal: Split into cohesive private composables/files only after checking shared state and current dirty edits; retain `SettingsScreen` as the composition root.
Interface:
```kotlin
@Composable fun SettingsScreen(viewModel: PodcastViewModel)
```
Benefits: More localized review; no new module boundary required.
Proof of same behavior: Existing UI tests and snapshots/build.
Plan: Move one section at a time; do not combine with visual redesign.

## Top recommendation

Start with the screen-file split: the 5,955-line file has the clearest locality problem and can be improved with mechanical moves while preserving its current public composables. The ViewModel is also a strong candidate, but should be tackled one domain at a time after selecting the boundary.

Considered, not recommended: New DI framework or a repository interface with only one production implementation; neither is justified by this survey. No architecture decision record is warranted until a hard-to-reverse boundary is chosen.

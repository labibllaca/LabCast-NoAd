# Recent Episodes as App Shortcuts — Design

## Intent
Long-pressing the LabCast launcher icon should offer the three most recently played episodes, newest first, so a listener can jump straight back into one. Finished episodes stay in the list; the list is purely "most recently played".

## Goals
- The app publishes up to three dynamic shortcuts, one per most recently played episode, newest first.
- Each shortcut shows the episode title as the short label and `Podcast · Episode` as the long label, with the play glyph as its icon.
- Tapping a shortcut opens `MainActivity`, starts that episode through the normal `playEpisode` path (resume at the saved position; a finished episode restarts from the beginning, exactly like tapping it in Verlauf), and opens the full player.
- The shortcut set is republished whenever the recent list changes, and entries disappear when an episode is no longer among the last three.
- Ordering is by when playback started, not by how far the episode was listened to.

## Non-goals
- No change to Verlauf ordering, filtering, or its data.
- No static shortcuts, "continue listening" shortcut, per-podcast shortcuts, or Android 13 conversation shortcuts.
- No per-episode artwork as shortcut icons (network images are unreliable at shortcut-publish time); the shared play glyph is used.
- No new playback start path outside `PodcastViewModel.playEpisode`.

## Anti-goals
- A shortcut launching the wrong episode because intents were reused or matched by equality.
- Shortcuts pointing at deleted episodes, or duplicates when the same episode is replayed.
- Blocking app startup on shortcut publishing or on the database.

## Constraints
- `minSdk 24`: shortcuts exist from API 25, so the publish path must be a safe no-op on 24.
- Launchers cap dynamic shortcuts (`getMaxShortcutCountPerActivity`, commonly 4); the publisher clamps to the smaller of that and 3.
- Short labels are truncated by the launcher; keep them short and deterministic.
- Room schema change requires a migration and an update to the existing migration test.

## Approaches
1. **`lastPlayedAt` column + `MainActivity` intent extra** (selected): add `EpisodeEntity.lastPlayedAt`, DB v8 with `MIGRATION_7_8`, set it in `playEpisode`, and drive shortcuts from the episodes flow. Shortcut `PendingIntent`s target `MainActivity` with the episode id; `onCreate`/`onNewIntent` hand it to Compose, which calls a new `playEpisodeById`. One activity, no new manifest activities, works cold and warm.
2. Reuse `playbackPositionMs` as the ordering key: no migration, but orders by progress rather than recency, so a briefly opened episode ranks below a long-running one. Rejected.
3. A transparent `ShortcutDispatchActivity`: cleaner intent separation, but adds an activity and a second playback-start path that must stay in sync. Rejected.

## Implementation outline
- **Data**: `EpisodeEntity.lastPlayedAt: Long = 0L` (`@ColumnInfo(defaultValue = "0")`); `PodcastDatabase` version 8 plus `MIGRATION_7_8` (`ALTER TABLE episodes ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0`); DAO/repository `updateLastPlayedAt(episodeId, timestamp)`.
- **ViewModel**: stamp `lastPlayedAt = System.currentTimeMillis()` when `playEpisode` starts (and reflect it in the current-episode state); add `playEpisodeById(id)` that loads the entity and delegates; expose `recentEpisodes: StateFlow<List<EpisodeEntity>>` (lastPlayedAt > 0, descending, take 3) built on a pure selector function.
- **Shortcuts**: new `AppShortcuts` helper wrapping `ShortcutManagerCompat`. It takes a small pure list of specs (id, short label, long label, episode id) and publishes with `setDynamicShortcuts` so removed episodes vanish. Unique intent data per episode, immutable `PendingIntent` with `FLAG_UPDATE_CURRENT`, and `singleTop` launch mode so a warm app receives `onNewIntent`.
- **MainActivity**: `singleTop`, read the episode id in `onCreate` and `onNewIntent` into a small state holder consumed by a `LaunchedEffect` in Compose, then clear it.
- **Tests**: pure selector and label-truncation tests; a Robolectric test for the spec builder; update `PodcastDatabaseMigrationTest` for v8 and add a 7→8 case asserting rows survive with `lastPlayedAt = 0`; device smoke test of the long-press menu and cold/warm taps.
- **Release**: bump `versionCode` 34 → 35 and `versionName` 1.5.1 → 1.6.0 per the build-file convention.

## Approval
Awaiting approval before implementation planning.

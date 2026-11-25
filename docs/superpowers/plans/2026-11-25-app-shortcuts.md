# Recent Episodes as App Shortcuts — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Long-pressing the LabCast launcher icon shows the three most recently played episodes and tapping one resumes it.

**Architecture:** A new `lastPlayedAt` column records when playback started (DB v7 → v8). A pure `RecentEpisodes` selector turns the episode list into three ordered `ShortcutSpec`s; `AppShortcuts` publishes them with `ShortcutManagerCompat.setDynamicShortcuts`. Shortcut intents target `MainActivity` (now `singleTop`) with the episode id, which Compose forwards to `PodcastViewModel.playEpisodeById`.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.x, AndroidX Core `ShortcutManagerCompat`, JUnit 4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-25-app-shortcuts-design.md`

## Global Constraints

- `minSdk 24`, `compileSdk 36`, `targetSdk 36`; app shortcuts are API 25+, so publishing is a silent no-op on API 24.
- At most 3 shortcuts, clamped to `ShortcutManagerCompat.getMaxShortcutCountPerActivity`.
- Ordering is by `lastPlayedAt` descending; finished episodes are included.
- `lastPlayedAt` defaults to `0` for existing rows and means "never played" when `0`.
- No change to the Verlauf tab's ordering or data.
- Short labels are trimmed, whitespace-collapsed, and capped at 40 chars; long labels at 80 chars; blank titles become `Episode`.
- Every task's tests run with `./gradlew testDebugUnitTest --tests <class>`.

## Review Focus

1. A shortcut whose episode was deleted (podcast removed) — must do nothing, not crash.
2. A shortcut tapped while the app is open and another episode plays — must switch episodes via `onNewIntent`, not open a second activity.
3. Two episodes stamped in the same millisecond — order must be stable, not launcher-dependent.
4. A launcher reporting a limit below 3 — must publish the smaller number.
5. An API 24 device — `publish` must return silently.

---

### Task 1: Persist last-played timestamps

**Files:**
- Modify: `app/src/main/java/com/example/data/Entities.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDao.kt`
- Modify: `app/src/main/java/com/example/data/PodcastRepository.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDatabase.kt`
- Create: `app/src/test/java/com/example/data/LastPlayedPersistenceTest.kt`
- Modify: `app/src/androidTest/java/com/example/data/PodcastDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `EpisodeEntity.lastPlayedAt: Long` (default `0L`)
  - `PodcastDao.updateLastPlayedAt(episodeId: String, timestamp: Long)`
  - `PodcastRepository.updateLastPlayedAt(episodeId: String, timestamp: Long)`
  - `PodcastDatabase.MIGRATION_7_8`, database `version = 8`

- [ ] **Step 1: Write the failing persistence test**

Create `LastPlayedPersistenceTest.kt` with a Robolectric test that inserts an episode, stamps it, and reads it back:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastPlayedPersistenceTest {
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        dao = PodcastDatabase.getDatabase(app).podcastDao()
    }

    @Test
    fun stampsAndReadsLastPlayedAt() = runBlocking {
        dao.insertEpisodes(listOf(episode("persist-1")))
        assertEquals(0L, dao.getEpisodeById("persist-1")!!.lastPlayedAt)

        dao.updateLastPlayedAt("persist-1", 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, dao.getEpisodeById("persist-1")!!.lastPlayedAt)
    }

    private fun episode(id: String) = EpisodeEntity(
        id = id, podcastId = "podcast", podcastTitle = "Show", podcastCoverUrl = "",
        title = "Episode $id", description = "", durationSeconds = 600,
        publishDate = "2026-01-01", audioUrl = "https://example.invalid/a.mp3"
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.data.LastPlayedPersistenceTest`
Expected: FAIL at compile time — `lastPlayedAt` and `updateLastPlayedAt` are unresolved.

- [ ] **Step 3: Add the column, DAO query, repository method, and migration**

In `EpisodeEntity`, append after `manualAdRanges`:

```kotlin
@androidx.room.ColumnInfo(defaultValue = "0")
val lastPlayedAt: Long = 0L
```

In `PodcastDao`:

```kotlin
@Query("UPDATE episodes SET lastPlayedAt = :timestamp WHERE id = :episodeId")
suspend fun updateLastPlayedAt(episodeId: String, timestamp: Long)
```

In `PodcastRepository`, beside `updateEpisodeProgress`:

```kotlin
suspend fun updateLastPlayedAt(episodeId: String, timestamp: Long) {
    podcastDao.updateLastPlayedAt(episodeId, timestamp)
}
```

In `PodcastDatabase`: set `version = 8`, add

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
    }
}
```

and register both in the builder: `.addMigrations(MIGRATION_6_7, MIGRATION_7_8)`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.example.data.LastPlayedPersistenceTest`
Expected: PASS.

- [ ] **Step 5: Extend the migration test for v8**

In `PodcastDatabaseMigrationTest`, register both migrations in the existing 6→7 test (`.addMigrations(PodcastDatabase.MIGRATION_6_7, PodcastDatabase.MIGRATION_7_8)`) and add:

```kotlin
@Test
fun migratesVersionSevenEpisodesWithoutLosingRows() {
    // build current DB, insert one episode, close
    // raw-open, ALTER TABLE episodes DROP COLUMN lastPlayedAt, PRAGMA user_version = 7, close
    // reopen with addMigrations(MIGRATION_7_8)
    assertEquals("Existing episode", episode?.title)
    assertEquals(0L, episode?.lastPlayedAt)
}
```

Mirror the existing test's setup/teardown exactly (unique database name, `context.deleteDatabase`).

- [ ] **Step 6: Compile the instrumented tests**

Run: `./gradlew assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL. (Running them needs an attached device; `adb devices` currently lists none.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/data app/src/test/java/com/example/data/LastPlayedPersistenceTest.kt app/src/androidTest/java/com/example/data/PodcastDatabaseMigrationTest.kt
git commit -m "feat(data): track when each episode was last played"
```

---

### Task 2: Select the recent episodes and build shortcut labels

**Files:**
- Create: `app/src/main/java/com/example/shortcuts/RecentEpisodes.kt`
- Create: `app/src/test/java/com/example/shortcuts/RecentEpisodesTest.kt`

**Interfaces:**
- Consumes: `EpisodeEntity.lastPlayedAt` from Task 1.
- Produces:
  - `data class ShortcutSpec(val id: String, val shortLabel: String, val longLabel: String, val episodeId: String)`
  - `RecentEpisodes.MAX_SHORTCUTS: Int = 3`
  - `RecentEpisodes.select(episodes: List<EpisodeEntity>, limit: Int = MAX_SHORTCUTS): List<EpisodeEntity>`
  - `RecentEpisodes.shortcutSpecs(episodes: List<EpisodeEntity>, limit: Int = MAX_SHORTCUTS): List<ShortcutSpec>`

- [ ] **Step 1: Write the failing selection and label tests**

```kotlin
class RecentEpisodesTest {
    @Test fun ordersByLastPlayedDescendingAndCapsAtThree()
    @Test fun includesFinishedEpisodes()
    @Test fun ignoresEpisodesThatWereNeverPlayed()
    @Test fun breaksTimestampTiesByIdForStableOrder()
    @Test fun collapsesDuplicateIds()
    @Test fun respectsALowerLimitAndZero()
    @Test fun buildsShortAndLongLabels()
    @Test fun truncatesLongTitlesAndFallsBackForBlankTitles()
}
```

Concrete assertions for the important ones:

```kotlin
assertEquals(listOf("d", "b", "c"),
    RecentEpisodes.select(listOf(ep("a", 1_000), ep("b", 3_000), ep("c", 2_000), ep("d", 4_000))).map { it.id })

assertEquals(listOf("a", "b"),
    RecentEpisodes.select(listOf(ep("a", 5_000), ep("b", 5_000))).map { it.id })

assertTrue(RecentEpisodes.select(listOf(ep("done", 9_000, completed = true))).single().isCompleted)
assertEquals(emptyList<EpisodeEntity>(), RecentEpisodes.select(listOf(ep("a", 1_000)), limit = 0))

val spec = RecentEpisodes.shortcutSpecs(listOf(ep("a", 1_000, title = "Deep Work", podcast = "Show"))).single()
assertEquals("episode_a", spec.id)
assertEquals("Deep Work", spec.shortLabel)
assertEquals("Show · Deep Work", spec.longLabel)
assertEquals("a", spec.episodeId)

assertEquals("Episode", RecentEpisodes.shortcutSpecs(listOf(ep("a", 1_000, title = "   "))).single().shortLabel)
assertEquals(40, RecentEpisodes.shortcutSpecs(listOf(ep("a", 1_000, title = "x".repeat(80)))).single().shortLabel.length)
```

`ep(id, lastPlayedAt, completed = false, title = "Episode $id", podcast = "Show")` is a private test helper building `EpisodeEntity`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests com.example.shortcuts.RecentEpisodesTest`
Expected: FAIL at compile time — `RecentEpisodes`/`ShortcutSpec` unresolved.

- [ ] **Step 3: Implement the selector and spec builder**

In `RecentEpisodes.kt`:

```kotlin
object RecentEpisodes {
    const val MAX_SHORTCUTS = 3

    fun select(episodes: List<EpisodeEntity>, limit: Int = MAX_SHORTCUTS): List<EpisodeEntity> =
        episodes.filter { it.lastPlayedAt > 0L }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<EpisodeEntity> { it.lastPlayedAt }.thenBy { it.id })
            .take(limit.coerceAtLeast(0))

    fun shortcutSpecs(episodes: List<EpisodeEntity>, limit: Int = MAX_SHORTCUTS): List<ShortcutSpec> =
        select(episodes, limit).map { ep ->
            ShortcutSpec(
                id = "episode_${ep.id}",
                shortLabel = label(ep.title, 40),
                longLabel = label("${ep.podcastTitle} · ${ep.title}", 80),
                episodeId = ep.id
            )
        }

    private fun label(text: String, max: Int): String {
        val clean = text.trim().replace(Regex("\\s+"), " ").ifBlank { "Episode" }
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.example.shortcuts.RecentEpisodesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/shortcuts app/src/test/java/com/example/shortcuts
git commit -m "feat(shortcuts): select recent episodes and label shortcuts"
```

---

### Task 3: Publish shortcuts and open episodes from them

**Files:**
- Create: `app/src/main/java/com/example/shortcuts/AppShortcuts.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/example/shortcuts/AppShortcutsTest.kt`
- Create: `app/src/test/java/com/example/ui/RecentEpisodesViewModelTest.kt`

**Interfaces:**
- Consumes: `RecentEpisodes.shortcutSpecs`, `ShortcutSpec`, `PodcastRepository.updateLastPlayedAt`.
- Produces:
  - `AppShortcuts.EXTRA_EPISODE_ID: String`
  - `AppShortcuts.publish(context: Context, specs: List<ShortcutSpec>)`
  - `PodcastViewModel.recentEpisodes: StateFlow<List<EpisodeEntity>>`
  - `PodcastViewModel.playEpisodeById(episodeId: String, openPlayer: Boolean = true)`

- [ ] **Step 1: Write the failing shortcut publishing test**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShortcutsTest {
    @Test fun publishesThreeOrderedShortcutsWithLabelsAndUniqueIntents()
    @Test fun clampsToTheLauncherLimit()
    @Test fun doesNothingOnApi24()
}
```

Assertions: publish five specs whose ids are `episode_a`…`episode_e`; `ShortcutManagerCompat.getDynamicShortcuts(context)` has ids `["episode_a","episode_b","episode_c"]`, ranks `0,1,2`, short label of the first equals its spec label, and each intent's `getStringExtra(AppShortcuts.EXTRA_EPISODE_ID)` matches. For the clamp test use `Shadows.shadowOf(context.getSystemService(ShortcutManager::class.java)).setMaxShortcutCountPerActivity(2)` and expect two. For API 24 use `@Config(sdk = [24])` and expect an empty list with no exception.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.shortcuts.AppShortcutsTest`
Expected: FAIL at compile time — `AppShortcuts` unresolved.

- [ ] **Step 3: Implement `AppShortcuts`**

```kotlin
object AppShortcuts {
    const val EXTRA_EPISODE_ID = "com.example.extra.SHORTCUT_EPISODE_ID"

    fun publish(context: Context, specs: List<ShortcutSpec>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val limit = minOf(RecentEpisodes.MAX_SHORTCUTS,
            ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        val shortcuts = specs.take(limit).mapIndexed { index, spec ->
            ShortcutInfoCompat.Builder(context, spec.id)
                .setShortLabel(spec.shortLabel)
                .setLongLabel(spec.longLabel)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_play_arrow))
                .setRank(index)
                .setIntent(intentFor(context, spec.episodeId))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun intentFor(context: Context, episodeId: String) =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("labcast://shortcut/episode/${Uri.encode(episodeId)}")
            putExtra(EXTRA_EPISODE_ID, episodeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
```

The unique `data` URI is required: identical intents would make `PendingIntent`s collide and launch the wrong episode.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.example.shortcuts.AppShortcutsTest`
Expected: PASS.

- [ ] **Step 5: Write the failing ViewModel test**

In `RecentEpisodesViewModelTest` (Robolectric, `StandardTestDispatcher` set as Main, mirroring `ManualAdRangeViewModelEditTest`):

```kotlin
@Test fun exposesOnlyTheThreeMostRecentlyPlayed() = runTest { /* insert 4 stamped episodes, build VM, advanceUntilIdle, assert ids */ }
@Test fun ignoresUnknownEpisodeIdsFromShortcuts() = runTest { /* playEpisodeById("missing"), advanceUntilIdle, assert currentPlayingEpisode == null */ }
```

Use ids unique to this class (`vm-recent-1`…) because the database singleton is shared across tests.

- [ ] **Step 6: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.ui.RecentEpisodesViewModelTest`
Expected: FAIL at compile time — `recentEpisodes`/`playEpisodeById` unresolved.

- [ ] **Step 7: Wire the ViewModel**

Add beside `historyEpisodes`:

```kotlin
val recentEpisodes: StateFlow<List<EpisodeEntity>> = repository.allEpisodes
    .map { RecentEpisodes.select(it) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

fun playEpisodeById(episodeId: String, openPlayer: Boolean = true) {
    viewModelScope.launch {
        repository.getEpisodeById(episodeId)?.let { playEpisode(it, openPlayer) }
    }
}
```

In `playEpisode`, replace the assignment `var currentEp = episode; _currentPlayingEpisode.value = currentEp` with a stamp first:

```kotlin
var currentEp = episode
val playedAt = System.currentTimeMillis()
repository.updateLastPlayedAt(currentEp.id, playedAt)
currentEp = currentEp.copy(lastPlayedAt = playedAt)
_currentPlayingEpisode.value = currentEp
```

In `init`, publish whenever the recent list changes:

```kotlin
viewModelScope.launch {
    recentEpisodes
        .map { RecentEpisodes.shortcutSpecs(it) }
        .distinctUntilChanged()
        .collect { AppShortcuts.publish(getApplication(), it) }
}
```

- [ ] **Step 8: Run the ViewModel test and the full unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, including the earlier `LastPlayedPersistenceTest` and `RecentEpisodesTest`.

- [ ] **Step 9: Handle the shortcut intent in `MainActivity`**

Add `android:launchMode="singleTop"` to the `MainActivity` entry in `AndroidManifest.xml`.

In `MainActivity`:

```kotlin
private val pendingShortcutEpisodeId = MutableStateFlow<String?>(null)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingShortcutEpisodeId.value = intent?.getStringExtra(AppShortcuts.EXTRA_EPISODE_ID)
    // ...existing setup unchanged...
    setContent {
        // ...existing content...
        val shortcutEpisodeId by pendingShortcutEpisodeId.collectAsStateWithLifecycle()
        LaunchedEffect(shortcutEpisodeId) {
            val id = shortcutEpisodeId ?: return@LaunchedEffect
            pendingShortcutEpisodeId.value = null
            viewModel.playEpisodeById(id)
        }
    }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingShortcutEpisodeId.value = intent.getStringExtra(AppShortcuts.EXTRA_EPISODE_ID)
}
```

`playEpisode` already opens the player and resumes from `playbackPositionMs`; a finished episode restarts at 0 through the existing logic.

- [ ] **Step 10: Build and smoke-test on a device**

Run: `./gradlew assembleDebug` — expected BUILD SUCCESSFUL. With a device attached, `./gradlew installDebug`, play four different episodes, then long-press the icon and confirm: three entries, newest first, finished ones included; cold-start tap opens the player on that episode; warm tap while another episode plays switches episodes.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/example/shortcuts/AppShortcuts.kt app/src/main/java/com/example/ui/PodcastViewModel.kt app/src/main/java/com/example/MainActivity.kt app/src/main/AndroidManifest.xml app/src/test/java/com/example/shortcuts/AppShortcutsTest.kt app/src/test/java/com/example/ui/RecentEpisodesViewModelTest.kt
git commit -m "feat(shortcuts): publish recent episodes as launcher shortcuts"
```

---

### Task 4: Release bump and full verification

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Bump the version**

`versionCode = 35`, `versionName = "1.6.0"`.

- [ ] **Step 2: Run the full verification**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, no failing tests.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.6.0 (35) for app shortcuts"
```

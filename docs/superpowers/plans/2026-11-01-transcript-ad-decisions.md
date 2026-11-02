# Transcript Ad Decisions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the listener open the transcript at the line being spoken, mark or reject ad chunks without the sheet closing, remember those decisions per episode in their own table, and shrink chunks to their content.

**Architecture:** A new Room table `ad_decisions` (v6 → v7, hand-written migration) stores one row per decided chunk, keyed by `(episodeId, startTimeSeconds, segmentKey)`. A pure `AdDecisionResolver` overlays those rows on the parsed transcript, so the sheet, the chunk counter and the existing chunk auto-skip all read one already-resolved flow. The UI gains a one-shot anchor scroll, keeps the sheet open on tap, and pauses the skipper while it is open.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room 2.7 + KSP, Hilt-free `AndroidViewModel`, JUnit4 + Robolectric + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-01-transcript-ad-decisions-design.md`

## Global Constraints

- `podcast_database` goes from version 6 to version 7; `MIGRATION_6_7` must be registered **before** `fallbackToDestructiveMigration()`, which stays for older jumps.
- Decision rows key on `(episodeId, startTimeSeconds, segmentKey)`; `segmentKey = ("$speaker|$text").hashCode()`.
- A rejection suppresses **only** the transcript chunk pipeline (`checkSponsorAndAdDetection` section 3). Chapter, keyword-interval and acoustic skipping are untouched.
- Auto-skip is paused for **all** pipelines while the transcript sheet is open, and resumes on close.
- No code path may rewrite `episode.transcript` because of a mark/unmark.
- Chunk cards have no fixed height; padding 10 dp, list spacing 6 dp, body text 12 sp / 16 sp line height, icon buttons 26 dp.
- `minSdk 24`, `targetSdk 36`, Kotlin 2.2.10, Compose BOM 2024.09.00, Room 2.7.0 — do not bump.
- Every task ends with a green `./gradlew :app:testDebugUnitTest` and its own commit.

## Review Focus

- **Duplicate timestamps.** Continuation lines and extra cue lines both fall back to second `0`, so several segments share a timestamp. Expect: deciding one of them leaves the others untouched. Pinned by `AdDecisionResolverTest` (Task 1) and `AdDecisionDaoTest` (Task 3).
- **Rejected chunks staying rejected.** A rejection must survive a re-parse of the same transcript. Expect: re-applying the stored decision yields `isSponsor = false, isRejectedAd = true` every time. Pinned by `AdDecisionResolverTest` (Task 1).
- **Stale decisions after a transcript re-fetch.** Rows whose timestamp/key no longer exist must be inert, never applied to a different line. Pinned by `AdDecisionResolverTest` (Task 1).
- **Repeated taps on the same chunk.** Two fast taps must leave one row holding the last choice, not two rows or a crash. Pinned by `AdDecisionDaoTest` (Task 3).
- **Suggestions that are already ads.** The suggestion list must never contain a chunk already marked as an ad or already rejected. Pinned by `TranscriptParserSuggestionTest` (Task 2).
- **Auto-skip disabled while the sheet is open.** The pause must not resurrect skipping when auto-skip is off, and must not stay paused after close. Pinned by `AdDetectionGateTest` (Task 4).

---

### Task 1: Segment identity and the decision resolver

**Files:**
- Modify: `app/src/main/java/com/example/data/TranscriptParser.kt:5-22` (`TranscriptSegment`)
- Create: `app/src/main/java/com/example/data/AdDecisionResolver.kt`
- Test: `app/src/test/java/com/example/data/AdDecisionResolverTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `TranscriptSegment.isRejectedAd: Boolean` (constructor, default `false`), `TranscriptSegment.segmentKey: Int`; `AdDecisionKey(startTimeSeconds: Long, segmentKey: Int)` with `AdDecisionKey.of(segment: TranscriptSegment): AdDecisionKey`; `AdDecisionResolver.apply(segments: List<TranscriptSegment>, decisions: Map<AdDecisionKey, Boolean>): List<TranscriptSegment>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDecisionResolverTest {

    private fun seg(time: Long, text: String, speaker: String = "Host", isSponsor: Boolean = false) =
        TranscriptSegment(startTimeSeconds = time, speaker = speaker, text = text, isSponsor = isSponsor)

    private fun decide(vararg pairs: Pair<TranscriptSegment, Boolean>) =
        pairs.associate { AdDecisionKey.of(it.first) to it.second }

    @Test
    fun `accepting a chunk marks it as an ad`() {
        val s = seg(120, "Brought to you by AG1")
        val out = AdDecisionResolver.apply(listOf(s), decide(s to true))
        assertTrue(out.single().isSponsor)
        assertFalse(out.single().isRejectedAd)
    }

    @Test
    fun `rejecting a chunk clears the feed marker and flags it rejected`() {
        val s = seg(120, "This episode is brought to you by AG1", isSponsor = true)
        val out = AdDecisionResolver.apply(listOf(s), decide(s to false))
        assertFalse(out.single().isSponsor)
        assertTrue(out.single().isRejectedAd)
    }

    @Test
    fun `a chunk without a decision keeps the parser result`() {
        val marked = seg(60, "Sponsor break", isSponsor = true)
        val plain = seg(90, "Back to the show")
        val out = AdDecisionResolver.apply(listOf(marked, plain), emptyMap())
        assertTrue(out[0].isSponsor)
        assertFalse(out[1].isSponsor)
        assertFalse(out[1].isRejectedAd)
    }

    @Test
    fun `decisions for unknown timestamps are ignored`() {
        val s = seg(120, "Back to the show")
        val stale = TranscriptSegment(startTimeSeconds = 999, speaker = "Host", text = "Old line")
        val out = AdDecisionResolver.apply(listOf(s), decide(stale to true))
        assertFalse(out.single().isSponsor)
    }

    @Test
    fun `duplicate timestamps are decided per segment not per second`() {
        val first = seg(0, "Line one of the cue")
        val second = seg(0, "Line two of the cue")
        val out = AdDecisionResolver.apply(listOf(first, second), decide(second to true))
        assertFalse(out[0].isSponsor)
        assertTrue(out[1].isSponsor)
    }

    @Test
    fun `rejecting stays rejected when the same transcript is resolved again`() {
        val s = seg(300, "Use code SAVE20 at checkout", isSponsor = true)
        val decisions = decide(s to false)
        val once = AdDecisionResolver.apply(listOf(s), decisions)
        val twice = AdDecisionResolver.apply(listOf(s), decisions)
        assertEquals(once, twice)
        assertTrue(twice.single().isRejectedAd)
    }
}
```

- [ ] **Step 2: Run the test to watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDecisionResolverTest"`
Expected: FAIL — unresolved reference `AdDecisionResolver`, `isRejectedAd`, `segmentKey`.

- [ ] **Step 3: Add the segment marker and the identity key**

`isRejectedAd` goes in the **constructor** (so `copy()` can set it, as the resolver does); `segmentKey` stays a body property so data-class equality ignores it:

```kotlin
data class TranscriptSegment(
    val startTimeSeconds: Long,
    val speaker: String,
    val text: String,
    val isSponsor: Boolean = false,
    val sponsorBrand: String? = null,
    val isRejectedAd: Boolean = false
) {
    val segmentKey: Int = ("$speaker|$text").hashCode()

    fun formattedTime(): String { /* unchanged */ }
}
```

- [ ] **Step 4: Implement the resolver**

`app/src/main/java/com/example/data/AdDecisionResolver.kt`:

```kotlin
package com.example.data

data class AdDecisionKey(val startTimeSeconds: Long, val segmentKey: Int) {
    companion object {
        fun of(segment: TranscriptSegment) = AdDecisionKey(segment.startTimeSeconds, segment.segmentKey)
    }
}

object AdDecisionResolver {
    fun apply(
        segments: List<TranscriptSegment>,
        decisions: Map<AdDecisionKey, Boolean>
    ): List<TranscriptSegment> = segments.map { seg ->
        when (decisions[AdDecisionKey.of(seg)]) {
            true -> seg.copy(isSponsor = true, isRejectedAd = false)
            false -> seg.copy(isSponsor = false, isRejectedAd = true)
            null -> seg
        }
    }
}
```

- [ ] **Step 5: Run the test to watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDecisionResolverTest"`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/data/TranscriptParser.kt app/src/main/java/com/example/data/AdDecisionResolver.kt app/src/test/java/com/example/data/AdDecisionResolverTest.kt
git commit -m "feat: resolve per-segment ad decisions over parsed transcripts"
```

---

### Task 2: Suggestions exclude decided chunks

**Files:**
- Modify: `app/src/main/java/com/example/data/TranscriptParser.kt:66-96` (`analyzeTranscriptForSuggestedAds`)
- Test: `app/src/test/java/com/example/data/TranscriptParserSuggestionTest.kt`

**Interfaces:**
- Consumes: `TranscriptSegment.isSponsor`, `TranscriptSegment.isRejectedAd` (Task 1).
- Produces: `TranscriptParser.analyzeTranscriptForSuggestedAds` unchanged in signature, but never returning a chunk whose segment is `isSponsor` or `isRejectedAd`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptParserSuggestionTest {

    @Test
    fun `marked and rejected chunks are not suggested`() {
        val marked = TranscriptSegment(60, "Host", "Brought to you by AG1", isSponsor = true)
        val rejected = TranscriptSegment(90, "Host", "Use code SAVE20 at shopify.com")
        val rejectedResolved = AdDecisionResolver.apply(
            listOf(rejected),
            mapOf(AdDecisionKey.of(rejected) to false)
        ).single()
        val clean = TranscriptSegment(120, "Host", "Back to the topic of sleep")

        val suggestions = TranscriptParser.analyzeTranscriptForSuggestedAds(
            listOf(marked, rejectedResolved, clean)
        )

        assertEquals(emptyList<SuggestedAdChunk>(), suggestions)
    }

    @Test
    fun `an undecided sponsored chunk is still suggested`() {
        val plain = TranscriptSegment(60, "Host", "This episode is brought to you by LMNT")
        val suggestions = TranscriptParser.analyzeTranscriptForSuggestedAds(listOf(plain))
        assertEquals(1, suggestions.size)
        assertEquals("Sponsor Brand: LMNT", suggestions.single().reason)
    }
}
```

- [ ] **Step 2: Run it to watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.TranscriptParserSuggestionTest"`
Expected: FAIL — `marked and rejected chunks are not suggested` returns 2 suggestions instead of 0.

- [ ] **Step 3: Skip decided segments in the loop**

First line inside `for (seg in segments)` in `analyzeTranscriptForSuggestedAds`:

```kotlin
if (seg.isSponsor || seg.isRejectedAd) continue
```

- [ ] **Step 4: Run it to watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.TranscriptParserSuggestionTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/TranscriptParser.kt app/src/test/java/com/example/data/TranscriptParserSuggestionTest.kt
git commit -m "feat: stop suggesting chunks the listener already decided"
```

---

### Task 3: Persistence — entity, DAO, migration, repository

**Files:**
- Modify: `app/src/main/java/com/example/data/Entities.kt` (append entity)
- Modify: `app/src/main/java/com/example/data/PodcastDao.kt`
- Modify: `app/src/main/java/com/example/data/PodcastDatabase.kt`
- Modify: `app/src/main/java/com/example/data/PodcastRepository.kt:97-105` (delete cleanup)
- Test: `app/src/test/java/com/example/data/AdDecisionDaoTest.kt`

**Interfaces:**
- Consumes: `AdDecisionKey` (Task 1).
- Produces: `AdDecisionEntity(episodeId, startTimeSeconds, segmentKey, isAd, decidedAt)`; `PodcastDao.getAdDecisions(episodeId): Flow<List<AdDecisionEntity>>`, `upsertAdDecision(AdDecisionEntity)`, `upsertAdDecisions(List<AdDecisionEntity>)`, `deleteAdDecisionsForPodcast(podcastId: String)`; `PodcastRepository.getAdDecisions(episodeId): Flow<List<AdDecisionEntity>>`, `setAdDecision(episodeId, startTimeSeconds, segmentKey, isAd)`, `setAdDecisions(episodeId, entries: List<Pair<AdDecisionKey, Boolean>>)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdDecisionDaoTest {

    private lateinit var db: PodcastDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.podcastDao()
    }

    @After
    fun tearDown() = db.close()

    private fun decision(time: Long, key: Int = 7, isAd: Boolean = true, episodeId: String = "ep1") =
        AdDecisionEntity(episodeId, time, key, isAd, decidedAt = 1L)

    @Test
    fun `repeated taps leave one row holding the last choice`() = runTest {
        dao.upsertAdDecision(decision(60, isAd = true))
        dao.upsertAdDecision(decision(60, isAd = false))
        val rows = dao.getAdDecisions("ep1").first()
        assertEquals(1, rows.size)
        assertEquals(false, rows.single().isAd)
    }

    @Test
    fun `segments sharing a timestamp keep separate decisions`() = runTest {
        dao.upsertAdDecision(decision(0, key = 1, isAd = true))
        dao.upsertAdDecision(decision(0, key = 2, isAd = false))
        val rows = dao.getAdDecisions("ep1").first()
        assertEquals(2, rows.size)
        assertEquals(setOf(true, false), rows.map { it.isAd }.toSet())
    }

    @Test
    fun `batch upsert writes every entry`() = runTest {
        dao.upsertAdDecisions(listOf(decision(10), decision(20), decision(30)))
        assertEquals(3, dao.getAdDecisions("ep1").first().size)
    }

    @Test
    fun `decisions are scoped to their episode`() = runTest {
        dao.upsertAdDecision(decision(60, episodeId = "ep1"))
        dao.upsertAdDecision(decision(60, episodeId = "ep2"))
        assertEquals(1, dao.getAdDecisions("ep1").first().size)
        assertEquals(1, dao.getAdDecisions("ep2").first().size)
    }

    @Test
    fun `unsubscribing clears the podcast's decision rows`() = runTest {
        dao.insertPodcasts(
            listOf(
                PodcastEntity("p1", "A", "a", "", "", ""),
                PodcastEntity("p2", "B", "b", "", "", "")
            )
        )
        dao.insertEpisodes(
            listOf(
                EpisodeEntity("e1", "p1", "A", "", "t", "", 600, "2026-01-01", ""),
                EpisodeEntity("e2", "p2", "B", "", "t", "", 600, "2026-01-01", "")
            )
        )
        dao.upsertAdDecision(decision(60, episodeId = "e1"))
        dao.upsertAdDecision(decision(60, episodeId = "e2"))

        dao.deleteAdDecisionsForPodcast("p1")

        assertTrue(dao.getAdDecisions("e1").first().isEmpty())
        assertEquals(1, dao.getAdDecisions("e2").first().size)
    }
}
```

- [ ] **Step 2: Run it to watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDecisionDaoTest"`
Expected: FAIL — unresolved `AdDecisionEntity`, `getAdDecisions`, `upsertAdDecisions`, `deleteAdDecisionsForPodcast`.

- [ ] **Step 3: Add the entity**

`Entities.kt`:

```kotlin
@Entity(
    tableName = "ad_decisions",
    primaryKeys = ["episodeId", "startTimeSeconds", "segmentKey"]
)
data class AdDecisionEntity(
    val episodeId: String,
    val startTimeSeconds: Long,
    val segmentKey: Int,
    val isAd: Boolean,
    val decidedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: Add the DAO queries**

```kotlin
@Query("SELECT * FROM ad_decisions WHERE episodeId = :episodeId")
fun getAdDecisions(episodeId: String): Flow<List<AdDecisionEntity>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAdDecision(decision: AdDecisionEntity)

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAdDecisions(decisions: List<AdDecisionEntity>)

@Query("DELETE FROM ad_decisions WHERE episodeId IN (SELECT id FROM episodes WHERE podcastId = :podcastId)")
suspend fun deleteAdDecisionsForPodcast(podcastId: String)
```

- [ ] **Step 5: Bump the database and add the migration**

```kotlin
@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, SyncLogEntity::class, AdDecisionEntity::class],
    version = 7,
    exportSchema = false
)
```

and in `getDatabase`, before the destructive fallbacks:

```kotlin
.addMigrations(MIGRATION_6_7)
```

with, in the companion object:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ad_decisions` (" +
                "`episodeId` TEXT NOT NULL, " +
                "`startTimeSeconds` INTEGER NOT NULL, " +
                "`segmentKey` INTEGER NOT NULL, " +
                "`isAd` INTEGER NOT NULL, " +
                "`decidedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`episodeId`, `startTimeSeconds`, `segmentKey`))"
        )
    }
}
```

plus `import androidx.room.migration.Migration` and `import androidx.sqlite.db.SupportSQLiteDatabase`.

- [ ] **Step 6: Add the repository API and delete cleanup**

```kotlin
fun getAdDecisions(episodeId: String): Flow<List<AdDecisionEntity>> =
    podcastDao.getAdDecisions(episodeId)

suspend fun setAdDecision(episodeId: String, startTimeSeconds: Long, segmentKey: Int, isAd: Boolean) {
    podcastDao.upsertAdDecision(AdDecisionEntity(episodeId, startTimeSeconds, segmentKey, isAd))
}

suspend fun setAdDecisions(episodeId: String, entries: List<Pair<AdDecisionKey, Boolean>>) {
    podcastDao.upsertAdDecisions(
        entries.map { (key, isAd) ->
            AdDecisionEntity(episodeId, key.startTimeSeconds, key.segmentKey, isAd)
        }
    )
}
```

and add `podcastDao.deleteAdDecisionsForPodcast(podcast.id)` / `(podcastId)` as the **first** line of both `removePodcast` and `removePodcastById`.

- [ ] **Step 7: Run the DAO test to watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.AdDecisionDaoTest"`
Expected: PASS, 5 tests.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/data/Entities.kt app/src/main/java/com/example/data/PodcastDao.kt app/src/main/java/com/example/data/PodcastDatabase.kt app/src/main/java/com/example/data/PodcastRepository.kt app/src/test/java/com/example/data/AdDecisionDaoTest.kt
git commit -m "feat: persist per-episode ad decisions in their own table"
```

---

### Task 4: ViewModel — resolved segments, toggles, skip pause

**Files:**
- Create: `app/src/main/java/com/example/player/AdDetectionGate.kt`
- Modify: `app/src/main/java/com/example/ui/PodcastViewModel.kt:91-162` (transcript flow and toggles), `:2179-2182` (detection gate), `:1723-1740` (skip toggle area)
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt:5138, :5179, :5207` (mechanical call-site rename)
- Test: `app/src/test/java/com/example/player/AdDetectionGateTest.kt`

**Interfaces:**
- Consumes: `AdDecisionResolver.apply`, `AdDecisionKey`, `PodcastRepository.getAdDecisions/setAdDecision/setAdDecisions` (Tasks 1, 3).
- Produces: `AdDetectionGate` (`var isSheetOpen: Boolean`, `fun shouldRun(autoSkipEnabled: Boolean): Boolean`); `PodcastViewModel.setTranscriptSheetOpen(open: Boolean)`, `internal fun shouldRunAdDetection(): Boolean`, `fun setAdDecision(segment: TranscriptSegment, isAd: Boolean)` (replaces `toggleSegmentAdStatus`), `markAllSuggestedAsAds()` unchanged in name.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDetectionGateTest {

    @Test
    fun `runs when auto-skip is on and the sheet is closed`() {
        assertTrue(AdDetectionGate().shouldRun(autoSkipEnabled = true))
    }

    @Test
    fun `does not run while the sheet is open`() {
        val gate = AdDetectionGate().apply { isSheetOpen = true }
        assertFalse(gate.shouldRun(autoSkipEnabled = true))
    }

    @Test
    fun `does not run when auto-skip is off`() {
        assertFalse(AdDetectionGate().shouldRun(autoSkipEnabled = false))
    }

    @Test
    fun `resumes after the sheet closes`() {
        val gate = AdDetectionGate().apply { isSheetOpen = true }
        gate.isSheetOpen = false
        assertTrue(gate.shouldRun(autoSkipEnabled = true))
    }
}
```

A plain JVM test, on purpose: the gate is the only part of the pause worth pinning, and constructing the ViewModel would drag Room and the media hub into a unit test.

- [ ] **Step 2: Run it to watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.player.AdDetectionGateTest"`
Expected: FAIL — unresolved `AdDetectionGate`.

- [ ] **Step 3: Implement the gate**

`app/src/main/java/com/example/player/AdDetectionGate.kt`:

```kotlin
package com.example.player

class AdDetectionGate {
    @Volatile
    var isSheetOpen: Boolean = false

    fun shouldRun(autoSkipEnabled: Boolean): Boolean = autoSkipEnabled && !isSheetOpen
}
```

- [ ] **Step 4: Run it to watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.player.AdDetectionGateTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Wire the gate into the ViewModel**

Next to `_isAutoAdSkipEnabled` (add `import com.example.player.AdDetectionGate` — this file imports player classes explicitly, not wildcard):

```kotlin
private val adDetectionGate = AdDetectionGate()

fun setTranscriptSheetOpen(open: Boolean) {
    adDetectionGate.isSheetOpen = open
}

internal fun shouldRunAdDetection(): Boolean =
    adDetectionGate.shouldRun(_isAutoAdSkipEnabled.value)
```

and make `checkSponsorAndAdDetection` start with:

```kotlin
private fun checkSponsorAndAdDetection(positionMs: Long) {
    val current = _currentPlayingEpisode.value ?: return
    if (!shouldRunAdDetection()) return
    ...
```

- [ ] **Step 6: Resolve decisions into `transcriptSegments`**

Replace the current `transcriptSegments` definition with a combine of the playing episode and its decision rows, and convert rows to the resolver's map:

```kotlin
private val adDecisions: Flow<List<AdDecisionEntity>> = _currentPlayingEpisode
    .flatMapLatest { ep ->
        if (ep == null) flowOf(emptyList()) else repository.getAdDecisions(ep.id)
    }

val transcriptSegments: StateFlow<List<TranscriptSegment>> =
    combine(_currentPlayingEpisode, adDecisions) { ep, decisions ->
        if (ep == null) return@combine emptyList()
        val parsed = TranscriptParser.parseOrGenerateTranscript(
            rawTranscript = ep.transcript.ifEmpty { null },
            episodeTitle = ep.title,
            episodeDescription = ep.description,
            durationSeconds = ep.durationSeconds,
            chapters = ChapterParser.parseChapters(ep.chapters, ep.description, ep.durationSeconds)
        )
        AdDecisionResolver.apply(
            parsed,
            decisions.associate {
                AdDecisionKey(it.startTimeSeconds, it.segmentKey) to it.isAd
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

(`suggestedAdChunks` keeps its `map { analyze(it) }` — Task 2 did the filtering.)

- [ ] **Step 7: Replace the toggles with decision writes**

Delete `toggleSegmentAdStatus` and write:

```kotlin
fun setAdDecision(segment: TranscriptSegment, isAd: Boolean) {
    val currentEp = _currentPlayingEpisode.value ?: return
    viewModelScope.launch {
        repository.setAdDecision(
            episodeId = currentEp.id,
            startTimeSeconds = segment.startTimeSeconds,
            segmentKey = segment.segmentKey,
            isAd = isAd
        )
        val status = if (isAd) "marked as AD" else "unmarked from ADs"
        repository.addSyncLog(
            "Transcript Ad Analyzer",
            "Segment at ${segment.formattedTime()} $status."
        )
    }
}
```

`markAllSuggestedAsAds()` keeps its shape but writes rows instead of a transcript:

```kotlin
fun markAllSuggestedAsAds() {
    val currentEp = _currentPlayingEpisode.value ?: return
    val suggestions = suggestedAdChunks.value
    if (suggestions.isEmpty()) return
    viewModelScope.launch {
        repository.setAdDecisions(
            episodeId = currentEp.id,
            entries = suggestions.map { AdDecisionKey.of(it.segment) to true }
        )
        _sponsorSkipEvent.value = "Marked ${suggestions.size} suggested transcript chunks as Ads!"
        repository.addSyncLog(
            "Transcript Ad Analyzer",
            "Marked ${suggestions.size} suggested transcript ad chunks for auto-skipping."
        )
    }
}
```

- [ ] **Step 8: Rename the three UI call sites (mechanical only)**

At `PodcastAppContent.kt:5138, :5179, :5207`, replace
`viewModel.toggleSegmentAdStatus(segment.startTimeSeconds)` with
`viewModel.setAdDecision(segment, !segment.isSponsor)`. No layout or behaviour
change in this task.

- [ ] **Step 9: Build and run the whole suite**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/player/AdDetectionGate.kt app/src/main/java/com/example/ui/PodcastViewModel.kt app/src/main/java/com/example/ui/PodcastAppContent.kt app/src/test/java/com/example/player/AdDetectionGateTest.kt
git commit -m "feat: resolve decisions in the view model and pause skipping while the sheet is open"
```

---

### Task 5: Sheet behaviour — anchor once, stay open

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt:4360-4372` (call site)
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt:4556-4604` (sheet state, pause flag, list state)
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt:5044-5051` (list modifier)

**Interfaces:**
- Consumes: `viewModel.setTranscriptSheetOpen(Boolean)` (Task 4).
- Produces: nothing other tasks use.

- [ ] **Step 1: Keep the sheet open on tap**

At the call site, drop the dismiss:

```kotlin
onSegmentSelected = { startMs -> viewModel.seekTo(startMs) },
```

- [ ] **Step 2: Report the sheet's open state to the ViewModel**

Wrap/attach beside the `ModalBottomSheet` call in `TranscriptBottomSheet`:

```kotlin
DisposableEffect(Unit) {
    viewModel.setTranscriptSheetOpen(true)
    onDispose { viewModel.setTranscriptSheetOpen(false) }
}
```

- [ ] **Step 3: Anchor once to the spoken line**

In `TranscriptBottomSheet`, add:

```kotlin
val listState = rememberLazyListState()
var hasAnchored by remember { mutableStateOf(false) }

LaunchedEffect(filteredSegments, playbackPositionMs > 0L) {
    if (hasAnchored || filteredSegments.isEmpty() || playbackPositionMs <= 0L) return@LaunchedEffect
    val currentSec = playbackPositionMs / 1000
    val index = filteredSegments.indexOfLast { it.startTimeSeconds <= currentSec }
    listState.scrollToItem((index - 2).coerceAtLeast(0))
    hasAnchored = true
}
```

The second key only distinguishes "position known yet" from "not yet", so the effect stops re-running once it anchors; `hasAnchored` is the real gate and playback advances change nothing. If a search filter removed the current line, this lands on the nearest preceding remaining chunk; if none precedes it, the list stays at the top.

- [ ] **Step 4: Give the list the state and the leftover height**

```kotlin
LazyColumn(
    state = listState,
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false)
) {
```

(removes `heightIn(max = 440.dp)`; the column that hosts the list must stay a `Column` so `weight` resolves.)

- [ ] **Step 5: Compile**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ui/PodcastAppContent.kt
git commit -m "feat: open the transcript at the spoken line and keep it open while marking"
```

---

### Task 6: Compact chunk cards

**Files:**
- Modify: `app/src/main/java/com/example/ui/PodcastAppContent.kt:5056-5229` (chunk item)

**Interfaces:**
- Consumes: `segment.isSponsor`, `segment.isRejectedAd`, `suggestion`, `viewModel.setAdDecision(segment, isAd)`.
- Produces: nothing other tasks use.

- [ ] **Step 1: Restructure the item to header + text + optional reason**

Replace the `Column(modifier = Modifier.padding(12.dp))` block with the shape below, keeping the existing `Card` colours/borders and the `testTag`. Two labels disappear on purpose: `CURRENTLY PLAYING` becomes the `▶` marker next to the time, and `AD / SPONSOR (AUTO-SKIP)` becomes the short `AD` / `AI` pill — both were costing a full header line on a narrow screen.

```kotlin
Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrentLine) {
            Text("▶", color = primaryAccent, fontSize = 9.sp)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = segment.formattedTime(),
            color = if (segment.isSponsor) adAccent else primaryAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = segment.speaker,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (segment.isSponsor || suggestion != null) {
            Text(
                text = if (segment.isSponsor) "AD" else "AI",
                color = adAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        AdChunkActions(
            segment = segment,
            suggestion = suggestion,
            onAccept = { viewModel.setAdDecision(segment, true) },
            onReject = { viewModel.setAdDecision(segment, false) }
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = segment.text,
        color = colors.textPrimary.copy(alpha = if (segment.isSponsor) 1f else 0.9f),
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    if (!segment.isSponsor && suggestion != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Suggested: ${suggestion.reason}",
            color = adAccent,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

- [ ] **Step 2: Add the compact action controls**

New private composable in the same file:

```kotlin
@Composable
private fun AdChunkActions(
    segment: TranscriptSegment,
    suggestion: SuggestedAdChunk?,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val colors = LocalCustomColors.current
    val amber = Color(0xFFD97706)

    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            segment.isSponsor -> {
                AdActionIcon(
                    icon = Icons.Default.Close,
                    description = "Unmark Ad",
                    tint = amber,
                    tag = "ad_toggle_${segment.startTimeSeconds}",
                    onClick = onReject
                )
            }
            suggestion != null -> {
                AdActionIcon(
                    icon = Icons.Default.Check,
                    description = "Mark Ad",
                    tint = amber,
                    tag = "ad_accept_${segment.startTimeSeconds}",
                    onClick = onAccept
                )
                AdActionIcon(
                    icon = Icons.Default.Close,
                    description = "Not an ad",
                    tint = colors.textMuted,
                    tag = "ad_reject_${segment.startTimeSeconds}",
                    onClick = onReject
                )
            }
            else -> {
                AdActionIcon(
                    icon = Icons.Default.Add,
                    description = "Mark as Ad",
                    tint = colors.textMuted,
                    tag = "ad_toggle_${segment.startTimeSeconds}",
                    onClick = onAccept
                )
            }
        }
    }
}

@Composable
private fun AdActionIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(26.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}
```

A suggestion therefore offers two 26 dp controls — check to accept, cross to reject — while an accepted or plain chunk keeps exactly one. No fixed heights and no `Button`/`OutlinedButton` with `height(26.dp)` remain in the item. Add `import androidx.compose.ui.graphics.vector.ImageVector` if the file does not already have it.

- [ ] **Step 3: Compile and run tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/ui/PodcastAppContent.kt
git commit -m "feat: shrink transcript chunks to their content"
```

---

### Task 7: Device verification, including the upgrade path

**Files:** none (verification only). If a check fails, fix in the owning task's file and re-run that task's steps.

**Interfaces:**
- Consumes: everything above.
- Produces: a build that is safe to install over the existing v6 install.

- [ ] **Step 1: Confirm the device**

Run: `adb devices -l`
Expected: one `device` line for the phone (USB serial or `IP:port`). If none, ask the owner to reconnect; do not install on an emulator.

- [ ] **Step 2: Install over the existing install (migration proof)**

Run: `./gradlew :app:assembleDebug && adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`, and the app still lists the podcasts and playback positions it had before. A wipe means `MIGRATION_6_7` was not registered before the destructive fallbacks.

- [ ] **Step 3: Check the transcript sheet**

On the phone, open an episode with a transcript and open the transcript sheet:
- It opens at the line being spoken, with roughly two chunks of context above it.
- Leave it open for ~30 s of playback: the list does **not** move.
- Scroll away manually: playback does not pull you back.
- Tap a chunk: playback seeks there, the sheet stays open.
- Tap a chunk marked AD: it plays instead of being skipped.
- Mark a plain chunk, unmark it again: the sheet never closes, log lines appear in Settings.
- Close the sheet while playback sits inside a marked chunk: it skips once on the next tick.

- [ ] **Step 4: Check rejection persistence**

Suggest an ad chunk (amber `AI`), reject it with the action control, close the sheet, reopen it, and reopen the episode: the chunk is still not suggested, still not skipped, and the "N suggested" count is one lower.

- [ ] **Step 5: Check chunk height**

At the 720x1600 override, compare a marked AD chunk with a plain one: both hug their text, no wrapped badge row, spacing 6 dp. Then reset with `adb -s <serial> shell wm size reset` and repeat once at native 1440x3200.

- [ ] **Step 6: Run the full suite one last time and report**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass. Report the device checks from Steps 3-5 with what you actually saw, not what should have happened.

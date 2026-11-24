package com.example.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastDatabaseMigrationTest {
    @Test
    fun migratesVersionSixEpisodesWithoutLosingRows() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "manual_ad_range_migration_test.db"
            context.deleteDatabase(name)

            val initial = Room.databaseBuilder(context, PodcastDatabase::class.java, name).build()
            initial.podcastDao().insertEpisodes(listOf(
                EpisodeEntity(
                id = "existing-episode",
                podcastId = "podcast",
                podcastTitle = "Test show",
                podcastCoverUrl = "",
                title = "Existing episode",
                description = "",
                durationSeconds = 600,
                publishDate = "2026-01-01",
                audioUrl = "https://example.invalid/audio.mp3"
                )
            ))
            initial.close()

            val legacyDatabase = SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).path,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            legacyDatabase.execSQL("ALTER TABLE episodes DROP COLUMN manualAdRanges")
            legacyDatabase.execSQL("PRAGMA user_version = 6")
            legacyDatabase.close()

            val migrated = Room.databaseBuilder(context, PodcastDatabase::class.java, name)
                .addMigrations(PodcastDatabase.MIGRATION_6_7)
                .build()
            val episode = migrated.podcastDao().getEpisodeById("existing-episode")

            assertEquals("Existing episode", episode?.title)
            assertEquals("", episode?.manualAdRanges)
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}

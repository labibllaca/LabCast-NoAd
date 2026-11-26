package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, SyncLogEntity::class, AsrWindowEntity::class],
    version = 9,
    exportSchema = false
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun asrWindowDao(): AsrWindowDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN manualAdRanges TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS asr_windows (" +
                        "episodeId TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, " +
                        "wordsJson TEXT NOT NULL, modelVersion TEXT NOT NULL, audioIdentity TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(episodeId, startMs))"
                )
            }
        }

        @Volatile
        private var INSTANCE: PodcastDatabase? = null

        fun getDatabase(context: Context): PodcastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PodcastDatabase::class.java,
                    "podcast_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

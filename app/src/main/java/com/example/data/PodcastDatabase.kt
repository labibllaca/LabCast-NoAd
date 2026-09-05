package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PodcastEntity::class, EpisodeEntity::class, SyncLogEntity::class], version = 4, exportSchema = false)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao

    companion object {
        @Volatile
        private var INSTANCE: PodcastDatabase? = null

        fun getDatabase(context: Context): PodcastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PodcastDatabase::class.java,
                    "podcast_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

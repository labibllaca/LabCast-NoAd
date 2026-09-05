package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val category: String,
    val isSubscribed: Boolean = false
)

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val podcastCoverUrl: String,
    val title: String,
    val description: String,
    val durationSeconds: Long,
    val publishDate: String,
    val audioUrl: String,
    val isDownloaded: Boolean = false,
    val downloadLocalPath: String? = null,
    val playbackPositionMs: Long = 0,
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false,
    val adTimestampsSeconds: String = "", // e.g. "45,210" for ads at 45s and 210s
    val chapters: String = "" // e.g. "0:Intro|120:Topic 1|480:Topic 2"
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis()
)

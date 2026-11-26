package com.example.data

import androidx.room.Entity

/** One completed Voz scan window, cached so later plays do not rescan it. */
@Entity(tableName = "asr_windows", primaryKeys = ["episodeId", "startMs"])
data class AsrWindowEntity(
    val episodeId: String,
    val startMs: Long,
    val endMs: Long,
    val wordsJson: String,
    val modelVersion: String,
    val audioIdentity: String,
    val updatedAt: Long = System.currentTimeMillis()
)

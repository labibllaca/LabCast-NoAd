package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AsrWindowDao {
    @Query("SELECT * FROM asr_windows WHERE episodeId = :episodeId")
    suspend fun windows(episodeId: String): List<AsrWindowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(windows: List<AsrWindowEntity>)
}

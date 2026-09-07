package com.jynx.scrollite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {

    @Insert
    suspend fun insert(entry: ReelEntry)

    @Query("SELECT * FROM reel_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ReelEntry>>
}
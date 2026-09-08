package com.jynx.scrollite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {

    @Insert
    suspend fun insert(entry: ReelEntry) : Long
    @Query("UPDATE reel_entries SET screenshotPath = :path WHERE id = :rowId")
    suspend fun updateScreenshotPath(rowId: Long, path: String)
    @Query("SELECT * FROM reel_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ReelEntry>>
}
package com.jynx.scrollite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reel_entries")
data class ReelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String?,
    val caption: String?,
    val hashtags: List<String>,
    val screenshotPath: String?,
    val platform: Platform,
    // No default: must be explicitly captured at the debounced "confirmed new
    // Reel" trigger and threaded through, even if ReelEntry construction happens later.
    val timestamp: Long,
)

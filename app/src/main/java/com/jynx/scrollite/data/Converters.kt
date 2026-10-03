package com.jynx.scrollite.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {

    // Explicit converters (instead of Room's built-in enum handling) so a
    // stored name that no longer exists reads back as OTHER instead of crashing
    @TypeConverter
    fun fromSection(section: Section): String = section.name

    @TypeConverter
    fun toSection(name: String): Section = Section.entries.firstOrNull { it.name == name } ?: Section.OTHER

    @TypeConverter
    fun fromHashtags(hashtags: List<String>): String {
        val array = JSONArray()
        hashtags.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toHashtags(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }
}
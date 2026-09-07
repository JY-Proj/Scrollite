package com.jynx.scrollite.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {

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
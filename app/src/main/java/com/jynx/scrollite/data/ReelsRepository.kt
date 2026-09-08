package com.jynx.scrollite.data

import kotlinx.coroutines.flow.Flow
import android.graphics.Bitmap
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

interface ReelsRepository {
    fun observeAll(): Flow<List<ReelEntry>>
    suspend fun insert(entry: ReelEntry): Long
    suspend fun saveScreenshot(rowId: Long, bitmap: Bitmap): String
    suspend fun updateScreenshotPath(rowId: Long, path: String)
}

class RoomReelsRepository(
    private val dao: ReelDao,
    private val context: Context,
) : ReelsRepository {
    override fun observeAll(): Flow<List<ReelEntry>> = dao.observeAll()
    override suspend fun insert(entry: ReelEntry) = dao.insert(entry)

    override suspend fun saveScreenshot(rowId: Long, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
            val file = File(dir, "reel_$rowId.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            file.absolutePath
        }

    override suspend fun updateScreenshotPath(rowId: Long, path: String) =
        dao.updateScreenshotPath(rowId, path)
}

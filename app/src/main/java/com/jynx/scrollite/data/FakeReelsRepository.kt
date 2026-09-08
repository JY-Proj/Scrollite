package com.jynx.scrollite.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class FakeReelsRepository(context: Context) : ReelsRepository {

    private val now = System.currentTimeMillis()

    private val entries = MutableStateFlow(
        listOf(
            ReelEntry(
                id = 1,
                username = "wasted",
                caption = "when the fridge is empty at 2am",
                hashtags = listOf("relatable", "fyp"),
                // Real, loadable file — exercises Coil's actual success path.
                screenshotPath = writeFakeThumbnail(context),
                platform = Platform.INSTAGRAM,
                timestamp = now,
            ),
            ReelEntry(
                id = 2,
                username = "ziyokhonov",
                // No caption or screenshot — exercises the placeholder UI path.
                caption = null,
                hashtags = emptyList(),
                screenshotPath = null,
                platform = Platform.INSTAGRAM,
                timestamp = now - 60_000L,
            ),
            ReelEntry(
                id = 3,
                username = "drewpetersend",
                caption = "day in the life of a golden retriever",
                hashtags = listOf("dogsoftiktok"),
                // Nonexistent path — exercises the load-failure/error UI path.
                screenshotPath = "/fake/path/reel3.jpg",
                platform = Platform.TIKTOK,
                timestamp = now - 120_000L,
            ),
        )
    )

    override fun observeAll(): Flow<List<ReelEntry>> = entries.asStateFlow()

    override suspend fun insert(entry: ReelEntry) {
        entries.value = listOf(entry) + entries.value
    }

    private fun writeFakeThumbnail(context: Context): String {
        val file = File(context.filesDir, "fake_thumbnail.jpg")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(90, 150, 220))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }
}

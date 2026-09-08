package com.jynx.scrollite.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jynx.scrollite.data.ReelEntry
import java.io.File

@Composable
fun ReelsListScreen(viewModel: ReelsListViewModel, modifier: Modifier = Modifier) {
    val entries by viewModel.reels.collectAsStateWithLifecycle()

    LazyColumn(modifier = modifier) {
        items(entries, key = { it.id }) { entry ->
            ReelListItem(entry)
        }
    }
}

@Composable
private fun ReelListItem(entry: ReelEntry, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(12.dp)) {
        ReelThumbnail(entry.screenshotPath)

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = entry.username ?: "Unknown user",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = entry.caption ?: "No caption",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (entry.hashtags.isNotEmpty()) {
                Text(
                    text = entry.hashtags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReelThumbnail(screenshotPath: String?, modifier: Modifier = Modifier) {
    val thumbnailModifier = modifier.size(64.dp).clip(RoundedCornerShape(8.dp))

    if (screenshotPath != null) {
        AsyncImage(
            model = File(screenshotPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Distinct from the null-path placeholder below: a load failure here
            // means capture actually ran but the file is missing/corrupt, not that
            // capture was intentionally skipped.
            error = ColorPainter(MaterialTheme.colorScheme.errorContainer),
            modifier = thumbnailModifier,
        )
    } else {
        Box(modifier = thumbnailModifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

package com.jynx.scrollite.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jynx.scrollite.data.ReelEntry
import com.jynx.scrollite.ui.theme.ScrolliteDimens
import com.jynx.scrollite.ui.theme.ScrolliteTheme
import java.io.File

/** "Recently watched" rows, added to a screen's LazyColumn. */
fun LazyListScope.recentReelItems(entries: List<ReelEntry>) {
    if (entries.isEmpty()) {
        item(key = "reels-empty") {
            Text(
                text = "Reels you watch will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(entries, key = { it.id }) { entry -> ReelListItem(entry) }
    }
}

@Composable
private fun ReelListItem(entry: ReelEntry, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        ReelThumbnail(entry.screenshotPath)

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = entry.username ?: "Unknown user",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = entry.caption ?: "No caption",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReelThumbnail(screenshotPath: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val thumbnailModifier = modifier
        .size(48.dp)
        .clip(shape)
        .border(ScrolliteDimens.CardOutline, ScrolliteTheme.colors.thumbnailOutline, shape)

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

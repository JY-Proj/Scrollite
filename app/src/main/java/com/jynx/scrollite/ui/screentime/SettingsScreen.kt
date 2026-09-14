package com.jynx.scrollite.ui.screentime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Lets the user set a daily Reels limit in minutes. The slider tracks a
 * local draft while dragging (so it stays smooth) and only persists via
 * [ScreenTimeViewModel.setDailyLimit] once the drag finishes.
 */
@Composable
fun SettingsScreen(
    viewModel: ScreenTimeViewModel,
    modifier: Modifier = Modifier,
) {
    val persistedLimit by viewModel.dailyLimitMinutes.collectAsStateWithLifecycle()

    // Local draft, seeded from the persisted value and re-synced whenever it
    // changes (e.g. first load emitting the stored/default limit).
    var draftMinutes by remember(persistedLimit) { mutableStateOf(persistedLimit.toFloat()) }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Daily Reels limit",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "${draftMinutes.toInt()} minutes",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = draftMinutes,
            onValueChange = { draftMinutes = it },
            onValueChangeFinished = { viewModel.setDailyLimit(draftMinutes.toInt()) },
            valueRange = MIN_LIMIT_MINUTES..MAX_LIMIT_MINUTES,
            // Whole-minute steps between min and max.
            steps = (MAX_LIMIT_MINUTES - MIN_LIMIT_MINUTES).toInt() - 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            text = "Between ${MIN_LIMIT_MINUTES.toInt()} and ${MAX_LIMIT_MINUTES.toInt()} minutes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MIN_LIMIT_MINUTES = 5f
private const val MAX_LIMIT_MINUTES = 180f

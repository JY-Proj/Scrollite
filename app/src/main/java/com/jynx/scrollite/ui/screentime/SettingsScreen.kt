package com.jynx.scrollite.ui.screentime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jynx.scrollite.data.Strictness

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        ExtraTimeSetting(viewModel)
    }
}

/**
 * How many "Need more time?" extensions the limit overlay offers per day.
 * Shows the user's latest pick as selected even when it's a looser one that
 * only takes effect tomorrow, with a note explaining the delay.
 */
@Composable
private fun ExtraTimeSetting(viewModel: ScreenTimeViewModel) {
    val active by viewModel.strictness.collectAsStateWithLifecycle()
    val pending by viewModel.pendingStrictness.collectAsStateWithLifecycle()
    val selected = pending ?: active

    Text(
        text = "Extra time",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "Whether you can add a few more minutes after hitting your limit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )

    Column(modifier = Modifier.padding(top = 8.dp).selectableGroup()) {
        Strictness.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        onClick = { viewModel.setStrictness(option) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 8.dp),
            ) {
                // Click handled by the row so the whole line is the touch target
                RadioButton(selected = option == selected, onClick = null)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(option.label(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = option.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pending?.let {
        Text(
            text = "${it.label()} starts tomorrow. Allowing more extra time takes effect the next day, " +
                "so it can't be used to get around today's limit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun Strictness.label(): String = when (this) {
    Strictness.LOCKED -> "Locked"
    Strictness.STRICT -> "Strict (recommended)"
    Strictness.FLEXIBLE -> "Flexible"
}

private fun Strictness.description(): String = when (this) {
    Strictness.LOCKED -> "No extra time. When time's up, it's up."
    else -> if (maxExtensionsPerDay == 1) "1 extension a day" else "Up to $maxExtensionsPerDay extensions a day"
}

private const val MIN_LIMIT_MINUTES = 5f
private const val MAX_LIMIT_MINUTES = 180f

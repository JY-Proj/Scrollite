package com.jynx.scrollite.ui.screentime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Shows today's accumulated Reels time. Turns to an error color and swaps
 * to a "limit reached" message once [ScreenTimeUiState.limitReached] flips —
 * the visible payoff of the timing + limit work, testable without needing
 * notifications set up.
 */
@Composable
fun ScreenTimeSummary(
    viewModel: ScreenTimeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val emphasisColor: Color =
        if (state.limitReached) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "You've watched ${state.minutesWatched} " +
                (if (state.minutesWatched == 1L) "minute" else "minutes") +
                " of Reels today",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = emphasisColor,
        )

        if (state.limitReached) {
            Text(
                text = "Daily limit reached (${state.limitMinutes} min)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                text = "Daily limit: ${state.limitMinutes} min",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // TEMP: simulate real timing writes before Person A's service timing
        // lands. Remove once real timing is live.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { viewModel.addDebugTime(60_000L) }) {
                Text("+1 min (debug)")
            }
            OutlinedButton(onClick = { viewModel.addDebugTime(5 * 60_000L) }) {
                Text("+5 min (debug)")
            }
        }
    }
}

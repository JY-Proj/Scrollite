package com.jynx.scrollite.ui.screentime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jynx.scrollite.R
import com.jynx.scrollite.ui.components.ScrolliteCard
import com.jynx.scrollite.ui.components.ScrolliteProgressBar
import com.jynx.scrollite.ui.dashboard.formatDuration
import com.jynx.scrollite.ui.theme.ScrolliteDimens
import com.jynx.scrollite.ui.theme.ScrolliteTheme

/**
 * Today's short-video time against the limit. Reaching the limit changes
 * the wording, not the color: it's information, not a telling-off.
 */
@Composable
fun TodayHero(state: ScreenTimeUiState, modifier: Modifier = Modifier) {
    val watchedMs = state.minutesWatched * 60_000L
    val limitMs = state.limitMinutes * 60_000L

    ScrolliteCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        containerColor = ScrolliteTheme.colors.heroFill,
        outline = ScrolliteTheme.colors.heroOutline,
        outlineWidth = ScrolliteDimens.EmphasisOutline,
        contentPadding = PaddingValues(20.dp),
    ) {
        Text(
            text = "Short videos today",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = formatDuration(watchedMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "of ${formatDuration(limitMs)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        ScrolliteProgressBar(
            fraction = if (limitMs > 0) watchedMs.toFloat() / limitMs else 0f,
            trackColor = ScrolliteTheme.colors.heroTrack,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = if (state.limitReached) "Limit reached" else "${formatDuration(state.minutesLeft * 60_000L)} left",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Gentle nudge shown when the limit is close (see [ScreenTimeUiState.showHeadsUp]). */
@Composable
fun HeadsUpBanner(minutesLeft: Long, modifier: Modifier = Modifier) {
    ScrolliteCard(
        modifier = modifier,
        outline = ScrolliteTheme.colors.headsUpOutline,
        outlineWidth = ScrolliteDimens.EmphasisOutline,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_limit_timer),
                contentDescription = null,
                tint = ScrolliteTheme.colors.headsUpIcon,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${if (minutesLeft == 1L) "1 minute" else "$minutesLeft minutes"} left today. Wrapping up soon?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Simulates short-video time so the limit can be tested without watching.
 * Only shown in debug builds.
 */
@Composable
fun DebugTimeButtons(viewModel: ScreenTimeViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

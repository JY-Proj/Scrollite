package com.jynx.scrollite.ui.screentime

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jynx.scrollite.R
import com.jynx.scrollite.data.DailyLimitOptions
import com.jynx.scrollite.data.Strictness
import com.jynx.scrollite.data.ThemeMode
import com.jynx.scrollite.ui.components.ScreenTitle
import com.jynx.scrollite.ui.components.ScrolliteCard
import com.jynx.scrollite.ui.components.SectionTitle
import com.jynx.scrollite.ui.dashboard.formatDuration
import com.jynx.scrollite.ui.theme.ScrolliteDimens
import com.jynx.scrollite.ui.theme.ScrolliteTheme
import kotlinx.coroutines.launch


/** Daily limit, extra time and appearance, each in its own card. */
@Composable
fun SettingsScreen(
    viewModel: ScreenTimeViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val limitMinutes by viewModel.dailyLimitMinutes.collectAsStateWithLifecycle()
    val today by viewModel.uiState.collectAsStateWithLifecycle()
    val looseningsToday by viewModel.looseningsToday.collectAsStateWithLifecycle()

    Column(
        verticalArrangement = Arrangement.spacedBy(ScrolliteDimens.SectionGap),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScrolliteDimens.ScreenPadding, vertical = 8.dp),
    ) {
        ScreenTitle("Settings", modifier = Modifier.padding(vertical = 8.dp))
        ScrolliteCard {
            DailyLimitSetting(
                limitMinutes = limitMinutes,
                watchedMinutes = today.minutesWatched,
                tone = LooseningTone.forCount(looseningsToday),
                onSave = viewModel::setDailyLimit,
            )
        }
        ScrolliteCard { ExtraTimeSetting(viewModel, tone = LooseningTone.forCount(looseningsToday)) }
        ScrolliteCard { AppearanceSetting(selected = themeMode, onSelect = onThemeModeChange) }
    }
}

/**
 * Shows the limit; "Change" opens [DailyLimitSheet] to edit it. Nothing is
 * saved until the user presses Save there, so tapping + a few times only
 * ever leads to one decision.
 */
@Composable
private fun DailyLimitSetting(limitMinutes: Int, watchedMinutes: Long, tone: LooseningTone, onSave: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }

    Text(
        text = "Daily limit",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "Short videos across all your apps.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(
            text = formatDuration(limitMinutes * 60_000L),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { editing = true }) { Text("Change") }
    }

    if (editing) {
        DailyLimitSheet(
            currentMinutes = limitMinutes,
            watchedMinutes = watchedMinutes,
            tone = tone,
            onSave = onSave,
            onClose = { editing = false },
        )
    }
}

/**
 * Edits a draft limit. Saving a lower (or the same) limit applies at once;
 * a higher one first asks "are you sure", with keeping the current limit as
 * the main action — the same nudge as "Leave Reels" on the limit overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyLimitSheet(
    currentMinutes: Int,
    watchedMinutes: Long,
    tone: LooseningTone,
    onSave: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var draft by rememberSaveable { mutableIntStateOf(currentMinutes) }
    var confirmingRaise by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Animate the sheet away before removing it
    val close: (save: Int?) -> Unit = { save ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            save?.let(onSave)
            onClose()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            if (confirmingRaise) {
                RaiseConfirmation(
                    currentMinutes = currentMinutes,
                    newMinutes = draft,
                    watchedMinutes = watchedMinutes,
                    tone = tone,
                    onKeep = { close(null) },
                    onRaise = { close(draft) },
                )
            } else {
                LimitEditor(
                    currentMinutes = currentMinutes,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSave = {
                        when {
                            DailyLimitOptions.isRaise(currentMinutes, draft) -> confirmingRaise = true
                            draft == currentMinutes -> close(null)
                            else -> close(draft)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LimitEditor(currentMinutes: Int, draft: Int, onDraftChange: (Int) -> Unit, onSave: () -> Unit) {
    SectionTitle("Daily limit")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        StepButton(
            icon = R.drawable.ic_minus,
            description = "Decrease limit",
            enabled = draft > DailyLimitOptions.MIN_MINUTES,
            onClick = { onDraftChange(DailyLimitOptions.stepDown(draft)) },
        )
        Text(
            text = formatDuration(draft * 60_000L),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepButton(
            icon = R.drawable.ic_plus,
            description = "Increase limit",
            enabled = draft < DailyLimitOptions.MAX_MINUTES,
            onClick = { onDraftChange(DailyLimitOptions.stepUp(draft)) },
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        DailyLimitOptions.PRESETS_MINUTES.forEach { preset ->
            FilterChip(
                selected = draft == preset,
                onClick = { onDraftChange(preset) },
                label = {
                    Text(
                        text = formatDuration(preset * 60_000L),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                shape = CircleShape,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = draft == preset,
                    borderColor = ScrolliteTheme.colors.chipOutline,
                    borderWidth = ScrolliteDimens.CardOutline,
                    selectedBorderWidth = 0.dp,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }

    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
    ) {
        Text(
            if (DailyLimitOptions.isRaise(currentMinutes, draft)) "Raise to ${formatDuration(draft * 60_000L)}"
            else "Save"
        )
    }
}

@Composable
private fun RaiseConfirmation(
    currentMinutes: Int,
    newMinutes: Int,
    watchedMinutes: Long,
    tone: LooseningTone,
    onKeep: () -> Unit,
    onRaise: () -> Unit,
) {
    val current = formatDuration(currentMinutes * 60_000L)
    val new = formatDuration(newMinutes * 60_000L)
    val watched = formatDuration(watchedMinutes * 60_000L)

    LooseningConfirmation(
        tone = tone,
        title = when (tone) {
            LooseningTone.FIRST -> "Raise your daily limit to $new?"
            LooseningTone.AGAIN -> "Raising it again?"
            LooseningTone.SAD -> "Oh… again?"
        },
        body = when (tone) {
            LooseningTone.FIRST -> "You've watched $watched of your $current today."
            LooseningTone.AGAIN -> "That's the second change today. You've watched $watched of your $current."
            LooseningTone.SAD -> "Earlier today, $current felt like enough. You've watched $watched so far."
        },
        keepLabel = "Keep $current",
        loosenLabel = "Raise to $new",
        onKeep = onKeep,
        onLoosen = onRaise,
    )
}

/**
 * Shared "are you sure?" for loosening any setting. Keeping things as they
 * are is the main action; the face and words get a little sadder with
 * [tone], but it never blocks.
 */
@Composable
private fun LooseningConfirmation(
    tone: LooseningTone,
    title: String,
    body: String,
    keepLabel: String,
    loosenLabel: String,
    onKeep: () -> Unit,
    onLoosen: () -> Unit,
) {
    Icon(
        painter = painterResource(
            when (tone) {
                LooseningTone.FIRST -> R.drawable.ic_face_neutral
                LooseningTone.AGAIN -> R.drawable.ic_face_unsure
                LooseningTone.SAD -> R.drawable.ic_face_sad
            }
        ),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(44.dp),
    )
    SectionTitle(title, modifier = Modifier.padding(top = 16.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Button(
        onClick = onKeep,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
    ) {
        Text(keepLabel)
    }
    TextButton(
        onClick = onLoosen,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(loosenLabel)
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(
            ScrolliteDimens.CardOutline,
            if (enabled) ScrolliteTheme.colors.chipOutline else ScrolliteTheme.colors.divider,
        ),
        modifier = Modifier.size(52.dp),
    ) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

/**
 * How many "Need more time?" extensions the limit overlay offers per day.
 * Changes apply immediately; a looser choice asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraTimeSetting(viewModel: ScreenTimeViewModel, tone: LooseningTone) {
    val selected by viewModel.strictness.collectAsStateWithLifecycle()
    val extensionsUsed by viewModel.extensionsUsedToday.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf<Strictness?>(null) }

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
                        onClick = {
                            if (option.isLooserThan(selected)) confirming = option
                            else viewModel.setStrictness(option)
                        },
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

    confirming?.let { requested ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val close: (apply: Boolean) -> Unit = { apply ->
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (apply) viewModel.setStrictness(requested)
                confirming = null
            }
        }
        val usedToday = when (extensionsUsed) {
            0 -> "You haven't used any extensions today."
            1 -> "You've used 1 extension today."
            else -> "You've used $extensionsUsed extensions today."
        }

        ModalBottomSheet(
            onDismissRequest = { confirming = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                LooseningConfirmation(
                    tone = tone,
                    title = when (tone) {
                        LooseningTone.FIRST -> "Allow more extra time?"
                        LooseningTone.AGAIN -> "Loosening things again?"
                        LooseningTone.SAD -> "Oh… again?"
                    },
                    body = when (tone) {
                        LooseningTone.FIRST ->
                            "${requested.shortLabel()} allows ${requested.description().lowercase()}. $usedToday"
                        LooseningTone.AGAIN ->
                            "That's the second change today. ${requested.shortLabel()} allows ${requested.description().lowercase()}."
                        LooseningTone.SAD ->
                            "${selected.shortLabel()} was keeping you on track. $usedToday"
                    },
                    keepLabel = "Keep ${selected.shortLabel()}",
                    loosenLabel = "Switch to ${requested.shortLabel()}",
                    onKeep = { close(false) },
                    onLoosen = { close(true) },
                )
            }
        }
    }
}

private fun Strictness.shortLabel(): String = when (this) {
    Strictness.LOCKED -> "Locked"
    Strictness.STRICT -> "Strict"
    Strictness.FLEXIBLE -> "Flexible"
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


/** Light, dark, or follow the phone. Applies to the app and the time's-up screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSetting(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Text(
        text = "Appearance",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "Also used for the time's-up screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        ThemeMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
            ) {
                Text(option.label())
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

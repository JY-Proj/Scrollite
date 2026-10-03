package com.jynx.scrollite.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jynx.scrollite.R
import com.jynx.scrollite.data.WeekSummary
import com.jynx.scrollite.ui.components.ScrolliteCard
import com.jynx.scrollite.ui.components.ScrolliteProgressBar
import com.jynx.scrollite.ui.theme.ChartColors
import com.jynx.scrollite.ui.theme.ScrolliteTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import java.time.format.TextStyle as DateTextStyle

private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val rangeFormat = DateTimeFormatter.ofPattern("MMM d")

/**
 * Monday–Sunday stacked bars with an app filter: "All apps" stacks by app,
 * picking one app stacks that app's sections (e.g. Reels vs everything else).
 * Tap a day for its breakdown; days without data simply have no bar.
 * [showDailyTable] adds the toggle for the exact-numbers table.
 */
@Composable
fun WeeklyDashboard(
    viewModel: WeeklyDashboardViewModel,
    modifier: Modifier = Modifier,
    showDailyTable: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val colors = ScrolliteTheme.colors.chart
    val view = current.view
    val dates = current.week.dates

    // Selection is per week and filter; changing either clears it
    var selected by rememberSaveable(current.week.weekStart, current.selectedApp) { mutableStateOf<String?>(null) }
    val selectedIndex = dates.indexOfFirst { it.toString() == selected }.takeIf { it >= 0 }
    var showTable by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        WeekHeader(
            weekOffset = current.weekOffset,
            weekStart = current.week.weekStart,
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
        )

        if (current.trackedApps.isNotEmpty()) {
            AppFilter(
                apps = current.trackedApps.map { it to it.displayName },
                selected = current.selectedApp,
                onSelect = viewModel::selectApp,
            )
        }

        if (selectedIndex == null) {
            WeekHeadline(current.summary)
        } else {
            DayHeadline(dates[selectedIndex], view.series, selectedIndex)
        }

        Legend(view.series, showLimit = view.showLimitLine, colors = colors, modifier = Modifier.padding(top = 12.dp))

        BarChart(
            series = view.series,
            dates = dates,
            today = current.today,
            limitMs = if (view.showLimitLine) current.dailyLimitMs else null,
            selectedIndex = selectedIndex,
            colors = colors,
            onSelect = { index ->
                val date = dates[index].toString()
                selected = if (date == selected) null else date
            },
            modifier = Modifier.padding(top = 8.dp),
        )

        if (showDailyTable) {
            TextButton(
                onClick = { showTable = !showTable },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (showTable) "Hide daily breakdown" else "Show daily breakdown")
            }
            if (showTable) DailyTable(view.series, dates, current.today)
        }
    }
}

@Composable
private fun WeekHeader(weekOffset: Int, weekStart: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    val title = when (weekOffset) {
        0 -> "This week"
        -1 -> "Last week"
        else -> "${weekStart.format(rangeFormat)} – ${weekStart.plusDays(6).format(rangeFormat)}"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous week")
        }
        IconButton(onClick = onNext, enabled = weekOffset < 0) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next week")
        }
    }
}

/** "All apps" plus one chip per tracked app; scrolls sideways once there are many. */
@Composable
private fun <T> AppFilter(apps: List<Pair<T, String>>, selected: T?, onSelect: (T?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
    ) {
        AppChip(selected = selected == null, label = "All apps", onClick = { onSelect(null) })
        apps.forEach { (app, name) ->
            AppChip(selected = selected == app, label = name, onClick = { onSelect(app) })
        }
    }
}

@Composable
private fun AppChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ScrolliteTheme.colors.chipOutline,
            borderWidth = 1.5.dp,
            selectedBorderWidth = 0.dp,
        ),
    )
}

@Composable
private fun WeekHeadline(summary: WeekSummary) {
    Text("Daily average", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = formatDuration(summary.dailyAverageMs),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        // Neutral ink with an arrow: the direction is information, not a verdict
        Text(
            text = when (val change = summary.changePercent) {
                null -> "No data last week"
                0 -> "Same as last week"
                else -> "${if (change < 0) "↓" else "↑"} ${abs(change)}% vs last week"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun DayHeadline(date: LocalDate, series: List<SeriesSpec>, index: Int) {
    val total = series.sumOf { it.values[index] }
    Text(date.format(dayHeaderFormat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text = formatDuration(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(
        text = series.filter { it.values[index] > 0 }
            .sortedByDescending { it.values[index] }
            .joinToString(" · ") { "${it.label} ${formatDuration(it.values[index])}" }
            .ifEmpty { "No time recorded" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(series: List<SeriesSpec>, showLimit: Boolean, colors: ChartColors, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        series.forEach { LegendSwatch(colors.forSlot(it.colorSlot), colors.outlineFor(it.colorSlot), it.label) }
        if (showLimit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(16.dp).height(10.dp)) {
                    drawLine(
                        color = colors.limit,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("Daily limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, outline: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Swatch(color, outline)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Swatch(color: Color, outline: Color?) {
    val shape = RoundedCornerShape(2.dp)
    var swatch = Modifier.size(10.dp).background(color, shape)
    if (outline != null) swatch = swatch.border(1.dp, outline, shape)
    Box(swatch)
}

@Composable
private fun BarChart(
    series: List<SeriesSpec>,
    dates: List<LocalDate>,
    today: LocalDate,
    limitMs: Long?,
    selectedIndex: Int?,
    colors: ChartColors,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val dayTotals = dates.indices.map { i -> series.sumOf { it.values[i] } }
    val (step, axisTop) = remember(dayTotals, limitMs) { niceAxis(maxOf(dayTotals.maxOrNull() ?: 0L, limitMs ?: 0L)) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val axisLabelWidth = 36.dp.toPx()
                val plotWidth = size.width - axisLabelWidth
                val plotHeight = size.height
                fun yFor(ms: Long) = plotHeight * (1f - ms.toFloat() / axisTop)

                // Recessive gridlines with labels at the right edge
                var tick = step
                while (tick <= axisTop) {
                    val y = yFor(tick)
                    drawLine(colors.grid, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1.dp.toPx())
                    val label = textMeasurer.measure(formatDuration(tick), axisLabelStyle)
                    drawText(label, topLeft = Offset(plotWidth + 6.dp.toPx(), y - label.size.height / 2f))
                    tick += step
                }

                val column = plotWidth / dates.size
                val barWidth = minOf(column * 0.5f, 28.dp.toPx())
                dates.indices.forEach { i ->
                    val segments = series.map {
                        BarSegment(colors.forSlot(it.colorSlot), colors.outlineFor(it.colorSlot), plotHeight * it.values[i] / axisTop)
                    }
                    drawStackedBar(
                        left = column * i + (column - barWidth) / 2f,
                        width = barWidth,
                        baseline = plotHeight,
                        segments = segments,
                        alpha = if (selectedIndex == null || selectedIndex == i) 1f else 0.35f,
                    )
                }

                drawLine(colors.baseline, Offset(0f, plotHeight), Offset(plotWidth, plotHeight), strokeWidth = 1.dp.toPx())

                if (limitMs != null && limitMs in 1..axisTop) {
                    val y = yFor(limitMs)
                    drawLine(
                        color = colors.limit,
                        start = Offset(0f, y),
                        end = Offset(plotWidth, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                }
            }

            // Tap targets: one full-height column per day (bigger than the bar itself)
            Row(Modifier.fillMaxSize().padding(end = 36.dp)) {
                dates.forEachIndexed { i, date ->
                    val isFuture = date.isAfter(today)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .semantics {
                                contentDescription = dayDescription(date, series, i, isFuture)
                                this.selected = selectedIndex == i
                            }
                            .clickable(enabled = !isFuture, role = Role.Button) { onSelect(i) },
                    )
                }
            }
        }

        // Day labels: today emphasised, future days muted
        val locale = LocalConfiguration.current.locales[0]
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 36.dp)) {
            dates.forEach { date ->
                val isToday = date == today
                val isFuture = date.isAfter(today)
                Text(
                    text = date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.onSurface
                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private class BarSegment(val color: Color, val outline: Color?, val height: Float)

/**
 * Stacks [segments] (bottom first) with a 2dp surface gap between them. Only
 * the topmost visible segment gets the 4dp rounded data-end; the base stays
 * square on the baseline. Non-zero segments get a hairline minimum so they
 * stay visible, and pale ones get their outline.
 */
private fun DrawScope.drawStackedBar(
    left: Float,
    width: Float,
    baseline: Float,
    segments: List<BarSegment>,
    alpha: Float,
) {
    val minVisible = 1.5.dp.toPx()
    val gap = 2.dp.toPx()
    val radius = CornerRadius(4.dp.toPx())
    val outlineWidth = 1.dp.toPx()
    val visible = segments.filter { it.height > 0f }

    var bottom = baseline
    visible.forEachIndexed { index, segment ->
        val height = maxOf(segment.height, minVisible)
        val isTop = index == visible.lastIndex
        val rect = RoundRect(
            left = left, top = bottom - height, right = left + width, bottom = bottom,
            topLeftCornerRadius = if (isTop) radius else CornerRadius.Zero,
            topRightCornerRadius = if (isTop) radius else CornerRadius.Zero,
        )
        val path = Path().apply { addRoundRect(rect) }
        drawPath(path, segment.color, alpha = alpha)
        segment.outline?.let { drawPath(path, it, alpha = alpha, style = Stroke(outlineWidth)) }
        bottom -= height + gap
    }
}

private fun dayDescription(date: LocalDate, series: List<SeriesSpec>, index: Int, isFuture: Boolean): String {
    val name = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    val parts = series.filter { it.values[index] > 0 }.map { "${it.label} ${spokenDuration(it.values[index])}" }
    return when {
        isFuture -> "$name, no data yet"
        parts.isEmpty() -> "$name, no time recorded"
        else -> "$name: ${parts.joinToString(", ")}"
    }
}

/** Table view of the same numbers — the accessible, exact alternative to the bars. */
@Composable
private fun DailyTable(series: List<SeriesSpec>, dates: List<LocalDate>, today: LocalDate) {
    val header = MaterialTheme.typography.labelSmall
    val cell = MaterialTheme.typography.bodySmall
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Day", style = header, modifier = Modifier.weight(1.4f))
            series.forEach { Text(it.label, style = header, modifier = Modifier.weight(1f)) }
            Text("Total", style = header, modifier = Modifier.weight(1f))
        }
        dates.forEachIndexed { i, date ->
            val future = date.isAfter(today)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(date.format(dayHeaderFormat), style = cell, modifier = Modifier.weight(1.4f))
                series.forEach {
                    Text(if (future) "—" else formatDuration(it.values[i]), style = cell, modifier = Modifier.weight(1f))
                }
                Text(if (future) "—" else formatDuration(series.sumOf { it.values[i] }), style = cell, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Each series' share of the week, with the exact total: a quick read of
 * where the time went, next to the day-by-day chart.
 */
@Composable
fun WeekBreakdownCard(viewModel: WeeklyDashboardViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val colors = ScrolliteTheme.colors.chart
    val totals = current.view.series.map { it to it.values.sum() }.filter { it.second > 0 }
    val weekTotal = totals.sumOf { it.second }
    if (weekTotal == 0L) return

    ScrolliteCard(modifier = modifier) {
        totals.forEachIndexed { index, (series, total) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 0.dp else 12.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Swatch(colors.forSlot(series.colorSlot), colors.outlineFor(series.colorSlot))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = series.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(72.dp),
                )
                ScrolliteProgressBar(
                    fraction = total.toFloat() / weekTotal,
                    height = 6.dp,
                    color = colors.forSlot(series.colorSlot),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDuration(total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}

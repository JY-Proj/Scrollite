package com.jynx.scrollite.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jynx.scrollite.ui.components.ScreenTitle
import com.jynx.scrollite.ui.components.ScrolliteCard
import com.jynx.scrollite.ui.theme.ScrolliteDimens

/** The week in detail: chart with the exact-numbers table, plus each series' share. */
@Composable
fun StatsScreen(viewModel: WeeklyDashboardViewModel, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ScrolliteDimens.SectionGap),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScrolliteDimens.ScreenPadding, vertical = 8.dp),
    ) {
        ScreenTitle("Stats", modifier = Modifier.padding(vertical = 8.dp))
        ScrolliteCard {
            WeeklyDashboard(viewModel, showDailyTable = true)
        }
        WeekBreakdownCard(viewModel)
    }
}

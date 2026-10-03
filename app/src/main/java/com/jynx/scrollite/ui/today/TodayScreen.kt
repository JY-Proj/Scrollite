package com.jynx.scrollite.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jynx.scrollite.BuildConfig
import com.jynx.scrollite.ui.components.BrandHeader
import com.jynx.scrollite.ui.components.ScrolliteCard
import com.jynx.scrollite.ui.components.SectionTitle
import com.jynx.scrollite.ui.dashboard.WeeklyDashboard
import com.jynx.scrollite.ui.dashboard.WeeklyDashboardViewModel
import com.jynx.scrollite.ui.reels.ReelsListViewModel
import com.jynx.scrollite.ui.reels.recentReelItems
import com.jynx.scrollite.ui.screentime.DebugTimeButtons
import com.jynx.scrollite.ui.screentime.HeadsUpBanner
import com.jynx.scrollite.ui.screentime.ScreenTimeViewModel
import com.jynx.scrollite.ui.screentime.TodayHero
import com.jynx.scrollite.ui.theme.ScrolliteDimens

/** Home tab: today against the limit, the week at a glance, and recently watched Reels. */
@Composable
fun TodayScreen(
    screenTimeViewModel: ScreenTimeViewModel,
    weeklyDashboardViewModel: WeeklyDashboardViewModel,
    reelsViewModel: ReelsListViewModel,
    modifier: Modifier = Modifier,
) {
    val today by screenTimeViewModel.uiState.collectAsStateWithLifecycle()
    val reels by reelsViewModel.reels.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScrolliteDimens.ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ScrolliteDimens.SectionGap),
    ) {
        item(key = "brand") { BrandHeader(modifier = Modifier.padding(vertical = 8.dp)) }
        item(key = "hero") { TodayHero(today) }
        if (today.showHeadsUp) {
            item(key = "heads-up") { HeadsUpBanner(today.minutesLeft) }
        }
        if (BuildConfig.DEBUG) {
            item(key = "debug") { DebugTimeButtons(screenTimeViewModel) }
        }
        item(key = "week") {
            ScrolliteCard { WeeklyDashboard(weeklyDashboardViewModel) }
        }
        item(key = "reels-title") { SectionTitle("Recently watched", modifier = Modifier.padding(top = 4.dp)) }
        recentReelItems(reels)
    }
}

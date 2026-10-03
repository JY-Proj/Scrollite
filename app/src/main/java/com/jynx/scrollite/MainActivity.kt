package com.jynx.scrollite

import android.graphics.Color
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jynx.scrollite.data.AppDatabase
import com.jynx.scrollite.data.AppearanceStore
import com.jynx.scrollite.data.RoomReelsRepository
import com.jynx.scrollite.data.ScreenTimeStore
import com.jynx.scrollite.data.ThemeMode
import com.jynx.scrollite.data.UsageRepository
import com.jynx.scrollite.ui.dashboard.StatsScreen
import com.jynx.scrollite.ui.dashboard.WeeklyDashboardViewModel
import com.jynx.scrollite.ui.reels.ReelsListViewModel
import com.jynx.scrollite.ui.screentime.ScreenTimeViewModel
import com.jynx.scrollite.ui.screentime.SettingsScreen
import com.jynx.scrollite.ui.theme.AppearanceViewModel
import com.jynx.scrollite.ui.theme.ScrolliteTheme
import com.jynx.scrollite.ui.today.TodayScreen
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private var showAccessibilityDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearanceViewModel: AppearanceViewModel = viewModel(
                factory = AppearanceViewModel.Factory(AppearanceStore(applicationContext))
            )
            // Render nothing for the moment the stored choice takes to load, rather than flash the wrong theme
            val themeMode = appearanceViewModel.themeMode.collectAsStateWithLifecycle().value ?: return@setContent
            val darkTheme = themeMode.isDark(systemIsDark = isSystemInDarkTheme())

            // Status and nav bar icons follow the app's theme, not just the phone's
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
            }

            ScrolliteTheme(darkTheme = darkTheme) {
                AppRoot(
                    themeMode = themeMode,
                    onThemeModeChange = appearanceViewModel::setThemeMode,
                )

                if (showAccessibilityDialog) {
                    AlertDialog(
                        onDismissRequest = { /* not dismissible — this permission is core to the app */ },
                        title = { Text("Enable Accessibility Access") },
                        text = {
                            Text(
                                "Scrollite needs Accessibility permission to detect when you're " +
                                        "watching videos. Tap below, find Scrollite in the list to turn it on."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { promptEnableAccessibilityService() }) {
                                Text("Open Settings")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showAccessibilityDialog = !isAccessibilityServiceEnabled()
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${ReelsAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
    }

    private fun promptEnableAccessibilityService() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    TODAY("Today", R.drawable.ic_nav_today),
    STATS("Stats", R.drawable.ic_nav_stats),
    SETTINGS("Settings", R.drawable.ic_nav_settings),
}

@Composable
private fun AppRoot(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val reelsRepository = remember {
        RoomReelsRepository(AppDatabase.getInstance(context).reelDao(), context)
    }
    val reelsViewModel: ReelsListViewModel = viewModel(
        factory = ReelsListViewModel.Factory(reelsRepository)
    )

    val screenTimeStore = remember { ScreenTimeStore(context.applicationContext) }
    val usageRepository = remember {
        UsageRepository(AppDatabase.getInstance(context).usageSessionDao())
    }
    val screenTimeViewModel: ScreenTimeViewModel = viewModel(
        factory = ScreenTimeViewModel.Factory(screenTimeStore, usageRepository)
    )
    val weeklyDashboardViewModel: WeeklyDashboardViewModel = viewModel(
        factory = WeeklyDashboardViewModel.Factory(usageRepository, screenTimeStore)
    )

    // Three fixed tabs — a navigation dependency would add little for now
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column {
                HorizontalDivider(color = ScrolliteTheme.colors.divider)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (tab) {
            Tab.TODAY -> TodayScreen(
                screenTimeViewModel = screenTimeViewModel,
                weeklyDashboardViewModel = weeklyDashboardViewModel,
                reelsViewModel = reelsViewModel,
                modifier = contentModifier,
            )
            Tab.STATS -> StatsScreen(viewModel = weeklyDashboardViewModel, modifier = contentModifier)
            Tab.SETTINGS -> SettingsScreen(
                viewModel = screenTimeViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                modifier = contentModifier,
            )
        }
    }
}

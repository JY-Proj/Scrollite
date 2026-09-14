package com.jynx.scrollite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jynx.scrollite.data.AppDatabase
import com.jynx.scrollite.data.RoomReelsRepository
import com.jynx.scrollite.data.ScreenTimeStore
import com.jynx.scrollite.ui.reels.ReelsListScreen
import com.jynx.scrollite.ui.reels.ReelsListViewModel
import com.jynx.scrollite.ui.screentime.ScreenTimeSummary
import com.jynx.scrollite.ui.screentime.ScreenTimeViewModel
import com.jynx.scrollite.ui.screentime.SettingsScreen
import com.jynx.scrollite.ui.theme.ScrolliteTheme
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
            ScrolliteTheme {
                AppRoot()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val reelsRepository = remember {
        RoomReelsRepository(AppDatabase.getInstance(context).reelDao(), context)
    }
    val reelsViewModel: ReelsListViewModel = viewModel(
        factory = ReelsListViewModel.Factory(reelsRepository)
    )

    val screenTimeStore = remember { ScreenTimeStore(context.applicationContext) }
    val screenTimeViewModel: ScreenTimeViewModel = viewModel(
        factory = ScreenTimeViewModel.Factory(screenTimeStore)
    )

    // Lightweight two-screen toggle — avoids pulling in a navigation dependency
    // for what is currently just main <-> settings.
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Settings" else "Scrollite") },
                navigationIcon = {
                    if (showSettings) {
                        TextButton(onClick = { showSettings = false }) {
                            Text("Back")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        TextButton(onClick = { showSettings = true }) {
                            Text("Settings")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showSettings) {
            SettingsScreen(
                viewModel = screenTimeViewModel,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            Column(modifier = Modifier.padding(innerPadding)) {
                ScreenTimeSummary(viewModel = screenTimeViewModel)
                HorizontalDivider()
                ReelsListScreen(
                    viewModel = reelsViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

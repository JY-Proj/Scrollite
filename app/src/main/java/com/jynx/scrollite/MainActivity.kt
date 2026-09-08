package com.jynx.scrollite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jynx.scrollite.data.AppDatabase
import com.jynx.scrollite.data.RoomReelsRepository
import com.jynx.scrollite.ui.reels.ReelsListScreen
import com.jynx.scrollite.ui.reels.ReelsListViewModel
import com.jynx.scrollite.ui.theme.ScrolliteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrolliteTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val context = LocalContext.current
                    val repository = remember {
                        RoomReelsRepository(AppDatabase.getInstance(context).reelDao(), context)
                    }
                    val viewModel: ReelsListViewModel = viewModel(
                        factory = ReelsListViewModel.Factory(repository)
                    )
                    ReelsListScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
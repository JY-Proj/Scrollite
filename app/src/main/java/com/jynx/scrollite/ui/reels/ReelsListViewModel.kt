package com.jynx.scrollite.ui.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jynx.scrollite.data.ReelEntry
import com.jynx.scrollite.data.ReelsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ReelsListViewModel(repository: ReelsRepository) : ViewModel() {

    val reels: StateFlow<List<ReelEntry>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun Factory(repository: ReelsRepository) = viewModelFactory {
            initializer { ReelsListViewModel(repository) }
        }
    }
}

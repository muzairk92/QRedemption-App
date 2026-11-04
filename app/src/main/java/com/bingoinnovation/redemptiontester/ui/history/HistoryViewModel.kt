package com.bingoinnovation.redemptiontester.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.di.AppContainer
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val attempts: List<RedemptionAttempt> = emptyList(),
    )

    val uiState: StateFlow<UiState> = history.attempts
        .map { UiState(isLoading = false, attempts = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun onClearHistory() {
        viewModelScope.launch {
            try {
                history.clear()
            } catch (e: IOException) {
                // The list stays as it is; there is nothing else to undo.
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HistoryViewModel(container.historyRepository) }
        }
    }
}

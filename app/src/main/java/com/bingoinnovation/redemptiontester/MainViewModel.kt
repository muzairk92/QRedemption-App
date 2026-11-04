package com.bingoinnovation.redemptiontester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.di.AppContainer
import com.bingoinnovation.redemptiontester.ui.navigation.CallerSetupRoute
import com.bingoinnovation.redemptiontester.ui.navigation.ScanRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Decides the first screen: Caller setup until a Caller is saved, then the scanner. */
class MainViewModel(
    private val callers: CallerRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _startRoute = MutableStateFlow<Any?>(null)

    /** Null while loading; the splash screen stays up until it is set. */
    val startRoute: StateFlow<Any?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            _startRoute.value = try {
                settings.ensureTabletId()
                if (callers.current() != null) ScanRoute else CallerSetupRoute()
            } catch (e: IOException) {
                CallerSetupRoute()
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { MainViewModel(container.callerRepository, container.settingsRepository) }
        }
    }
}

package com.bingoinnovation.redemptiontester.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bingoinnovation.redemptiontester.data.PortalRepository
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.di.AppContainer
import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.redemption.TabletIdRules
import com.bingoinnovation.redemptiontester.domain.server.BaseUrlRules
import com.bingoinnovation.redemptiontester.domain.server.ServerPreset
import com.bingoinnovation.redemptiontester.domain.server.ServerSettings
import com.bingoinnovation.redemptiontester.ui.shared.Verification
import com.bingoinnovation.redemptiontester.ui.shared.toVerification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val callers: CallerRepository,
    private val portal: PortalRepository,
    allowCleartext: Boolean,
    versionName: String,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val savedServer: ServerSettings = ServerSettings(),
        val selectedPreset: ServerPreset = ServerPreset.STAGING,
        val customUrlInput: String = "",
        /** Null while the typed address has not been checked or is valid. */
        val customUrlError: BaseUrlRules.Result? = null,
        val savedTabletId: String? = null,
        val tabletIdInput: String = "",
        val tabletIdValidation: TabletIdRules.Validation = TabletIdRules.Validation.Valid,
        val caller: CallerCredentials? = null,
        val verification: Verification = Verification.Idle,
        val callerRemoved: Boolean = false,
        val storageFailed: Boolean = false,
        val allowCleartext: Boolean = false,
        val versionName: String = "",
    ) {
        val canSaveTabletId: Boolean
            get() = tabletIdValidation == TabletIdRules.Validation.Valid && tabletIdInput != savedTabletId
    }

    private val _uiState = MutableStateFlow(UiState(allowCleartext = allowCleartext, versionName = versionName))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var first = true
            combine(settings.settings, callers.credentials) { saved, caller -> saved to caller }
                .collect { (saved, caller) ->
                    _uiState.update { state ->
                        val updated = state.copy(
                            isLoading = false,
                            savedServer = saved.server,
                            savedTabletId = saved.tabletId,
                            caller = caller,
                        )
                        if (!first) {
                            updated
                        } else {
                            // Fill the editable fields once, from what is saved.
                            val tabletId = saved.tabletId.orEmpty()
                            updated.copy(
                                selectedPreset = saved.server.preset,
                                customUrlInput = saved.server.customBaseUrl,
                                tabletIdInput = tabletId,
                                tabletIdValidation = TabletIdRules.validate(tabletId),
                            )
                        }
                    }
                    first = false
                }
        }
    }

    fun onPresetSelected(preset: ServerPreset) {
        val saved = _uiState.value.savedServer
        _uiState.update { it.copy(selectedPreset = preset, customUrlError = null, verification = Verification.Idle) }
        when {
            preset == ServerPreset.STAGING -> saveServer(saved.copy(preset = ServerPreset.STAGING))
            // Switch back to a Custom address that was already checked and saved.
            saved.customBaseUrl.isNotBlank() -> saveServer(saved.copy(preset = ServerPreset.CUSTOM))
        }
    }

    fun onCustomUrlChange(value: String) {
        _uiState.update { it.copy(customUrlInput = value, customUrlError = null) }
    }

    fun onSaveCustomUrl() {
        val state = _uiState.value
        when (val result = BaseUrlRules.validate(state.customUrlInput, state.allowCleartext)) {
            is BaseUrlRules.Result.Valid -> {
                _uiState.update {
                    it.copy(customUrlInput = result.baseUrl, customUrlError = null, verification = Verification.Idle)
                }
                saveServer(ServerSettings(preset = ServerPreset.CUSTOM, customBaseUrl = result.baseUrl))
            }
            else -> _uiState.update { it.copy(customUrlError = result) }
        }
    }

    fun onTabletIdChange(value: String) {
        _uiState.update { it.copy(tabletIdInput = value, tabletIdValidation = TabletIdRules.validate(value)) }
    }

    fun onGenerateTabletId() {
        onTabletIdChange(TabletIdRules.generate())
    }

    fun onSaveTabletId() {
        val state = _uiState.value
        if (!state.canSaveTabletId) return
        launchStorage { settings.setTabletId(state.tabletIdInput) }
    }

    fun onVerifyCaller() {
        val caller = _uiState.value.caller ?: return
        if (_uiState.value.verification == Verification.Checking) return
        _uiState.update { it.copy(verification = Verification.Checking) }
        viewModelScope.launch {
            val verification = try {
                portal.verifyCaller(caller).toVerification()
            } catch (e: IOException) {
                Verification.StorageFailed
            }
            _uiState.update { it.copy(verification = verification) }
        }
    }

    fun onRemoveCaller() {
        launchStorage {
            callers.clear()
            _uiState.update { it.copy(callerRemoved = true) }
        }
    }

    fun onStorageErrorShown() {
        _uiState.update { it.copy(storageFailed = false) }
    }

    private fun saveServer(server: ServerSettings) {
        launchStorage { settings.setServer(server) }
    }

    private fun launchStorage(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                _uiState.update { it.copy(storageFailed = true) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settings = container.settingsRepository,
                    callers = container.callerRepository,
                    portal = container.portalRepository,
                    allowCleartext = container.allowCleartextServers,
                    versionName = container.versionName,
                )
            }
        }
    }
}

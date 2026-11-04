package com.bingoinnovation.redemptiontester.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bingoinnovation.redemptiontester.data.PortalRepository
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.di.AppContainer
import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentialRules
import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.caller.CallerQrParser
import com.bingoinnovation.redemptiontester.ui.components.ScannerProblem
import com.bingoinnovation.redemptiontester.ui.shared.Verification
import com.bingoinnovation.redemptiontester.ui.shared.toVerification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class CallerSetupViewModel(
    private val callers: CallerRepository,
    settings: SettingsRepository,
    private val portal: PortalRepository,
) : ViewModel() {

    enum class Mode { SCAN, MANUAL, REVIEW }

    enum class ScanFeedback { TICKET_SCANNED, NOT_A_CALLER_LABEL, INVALID_CALLER_ID, INVALID_API_KEY, SCANNER_NOT_READY }

    data class UiState(
        val mode: Mode = Mode.SCAN,
        val feedback: ScanFeedback? = null,
        val callerIdInput: String = "",
        val apiKeyInput: String = "",
        val callerIdError: Boolean = false,
        val apiKeyError: Boolean = false,
        val candidate: CallerCredentials? = null,
        val serverBaseUrl: String? = null,
        val verification: Verification = Verification.Idle,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val isSaved: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { saved -> _uiState.update { it.copy(serverBaseUrl = saved.server.baseUrl) } }
        }
    }

    fun onCodeScanned(raw: String) {
        if (_uiState.value.mode != Mode.SCAN) return
        when (val result = CallerQrParser.parse(raw)) {
            is CallerQrParser.Result.Valid -> review(result.credentials)
            CallerQrParser.Result.TicketInsteadOfCaller -> showFeedback(ScanFeedback.TICKET_SCANNED)
            CallerQrParser.Result.NotACallerLabel -> showFeedback(ScanFeedback.NOT_A_CALLER_LABEL)
            CallerQrParser.Result.InvalidCallerId -> showFeedback(ScanFeedback.INVALID_CALLER_ID)
            CallerQrParser.Result.InvalidApiKey -> showFeedback(ScanFeedback.INVALID_API_KEY)
        }
    }

    fun onScannerProblem(problem: ScannerProblem) {
        if (problem == ScannerProblem.MODEL_NOT_READY && _uiState.value.feedback == null) {
            showFeedback(ScanFeedback.SCANNER_NOT_READY)
        }
    }

    fun onEnterManually() {
        _uiState.update { it.copy(mode = Mode.MANUAL, feedback = null) }
    }

    fun onScanAgain() {
        _uiState.update {
            it.copy(mode = Mode.SCAN, feedback = null, candidate = null, verification = Verification.Idle)
        }
    }

    fun onCallerIdChange(value: String) {
        _uiState.update { it.copy(callerIdInput = value, callerIdError = false) }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKeyInput = value, apiKeyError = false) }
    }

    fun onSubmitManual() {
        val state = _uiState.value
        when (val validation = CallerCredentialRules.validate(state.callerIdInput, state.apiKeyInput)) {
            is CallerCredentialRules.Validation.Valid -> review(validation.credentials)
            is CallerCredentialRules.Validation.Invalid -> _uiState.update {
                it.copy(callerIdError = validation.callerIdInvalid, apiKeyError = validation.apiKeyInvalid)
            }
        }
    }

    fun onVerify() {
        val candidate = _uiState.value.candidate ?: return
        if (_uiState.value.verification == Verification.Checking) return
        _uiState.update { it.copy(verification = Verification.Checking) }
        viewModelScope.launch {
            val verification = try {
                portal.verifyCaller(candidate).toVerification()
            } catch (e: IOException) {
                Verification.StorageFailed
            }
            _uiState.update { it.copy(verification = verification) }
        }
    }

    fun onSave() {
        val candidate = _uiState.value.candidate ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                callers.save(candidate)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: IOException) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    private fun review(credentials: CallerCredentials) {
        _uiState.update {
            it.copy(mode = Mode.REVIEW, candidate = credentials, feedback = null, verification = Verification.Idle)
        }
    }

    private fun showFeedback(feedback: ScanFeedback) {
        _uiState.update { it.copy(feedback = feedback) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                CallerSetupViewModel(container.callerRepository, container.settingsRepository, container.portalRepository)
            }
        }
    }
}

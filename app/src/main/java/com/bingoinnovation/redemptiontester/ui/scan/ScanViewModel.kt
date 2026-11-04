package com.bingoinnovation.redemptiontester.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.di.AppContainer
import com.bingoinnovation.redemptiontester.domain.caller.CallerQrParser
import com.bingoinnovation.redemptiontester.domain.ticket.TicketQr
import com.bingoinnovation.redemptiontester.ui.components.ScannerProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class ScanViewModel(
    callers: CallerRepository,
    settings: SettingsRepository,
    history: HistoryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    enum class Message { NOT_A_TICKET, CALLER_LABEL_SCANNED, SCANNER_NOT_READY }

    data class UiState(
        val callerId: String? = null,
        val serverBaseUrl: String? = null,
        val tabletId: String? = null,
        val unconfirmedCount: Int = 0,
        val torchOn: Boolean = false,
        /** One-off message for a snackbar; cleared with [onMessageShown]. */
        val message: Message? = null,
        /** A scanned ticket waiting to be opened; cleared with [onTicketOpened]. */
        val ticketToOpen: String? = null,
        /** False from a successful scan until this screen is shown again. */
        val scanningEnabled: Boolean = true,
    )

    private data class LocalState(
        val torchOn: Boolean = false,
        val message: Message? = null,
        val ticketToOpen: String? = null,
        val scanningEnabled: Boolean = true,
    )

    private val local = MutableStateFlow(LocalState())
    private var lastRejected: Pair<String, Long>? = null

    val uiState: StateFlow<UiState> = combine(
        callers.credentials,
        settings.settings,
        history.unconfirmedCount,
        local,
    ) { caller, saved, unconfirmed, screen ->
        UiState(
            callerId = caller?.callerId,
            serverBaseUrl = saved.server.baseUrl,
            tabletId = saved.tabletId,
            unconfirmedCount = unconfirmed,
            torchOn = screen.torchOn,
            message = screen.message,
            ticketToOpen = screen.ticketToOpen,
            scanningEnabled = screen.scanningEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun onCodeScanned(raw: String) {
        if (!local.value.scanningEnabled) return
        val ticket = TicketQr.parse(raw)
        if (ticket != null) {
            local.update { it.copy(ticketToOpen = ticket.value, scanningEnabled = false) }
            return
        }
        // The camera reports the same code many times a second: say it once per few seconds.
        val now = clock()
        val previous = lastRejected
        if (previous != null && previous.first == raw && now - previous.second < REPEAT_MESSAGE_MILLIS) return
        lastRejected = raw to now
        val message = if (CallerQrParser.parse(raw) is CallerQrParser.Result.Valid) {
            Message.CALLER_LABEL_SCANNED
        } else {
            Message.NOT_A_TICKET
        }
        local.update { it.copy(message = message) }
    }

    /** Opens a typed or pasted ticket. Returns false when it is not a valid ticket. */
    fun onManualCode(text: String): Boolean {
        // Typed text may carry spaces or a line break from pasting; a ticket never contains them.
        val ticket = TicketQr.parse(text.trim()) ?: return false
        local.update { it.copy(ticketToOpen = ticket.value, scanningEnabled = false) }
        return true
    }

    fun onScannerProblem(problem: ScannerProblem) {
        if (problem == ScannerProblem.MODEL_NOT_READY && local.value.message == null) {
            local.update { it.copy(message = Message.SCANNER_NOT_READY) }
        }
    }

    fun onTicketOpened() {
        local.update { it.copy(ticketToOpen = null) }
    }

    /** Called each time the screen is shown, including on return from a ticket. */
    fun onScreenResumed() {
        local.update { if (it.ticketToOpen == null) it.copy(scanningEnabled = true) else it }
    }

    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    fun onTorchToggle(on: Boolean) {
        local.update { it.copy(torchOn = on) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val REPEAT_MESSAGE_MILLIS = 3_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ScanViewModel(container.callerRepository, container.settingsRepository, container.historyRepository)
            }
        }
    }
}

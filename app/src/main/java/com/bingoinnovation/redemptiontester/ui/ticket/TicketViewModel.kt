package com.bingoinnovation.redemptiontester.ui.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bingoinnovation.redemptiontester.data.PortalRepository
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.di.AppContainer
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RequestIdGenerator
import com.bingoinnovation.redemptiontester.domain.redemption.TabletIdRules
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.ticket.TicketQr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * One ticket: redeem it once, then run the follow-up tests against the same request.
 *
 * The screen's "current request" is a request ID plus tablet ID. Once it has been sent it
 * can only be resent unchanged (retry) or probed (new scan, other tablet), exactly as a
 * real player tablet must behave.
 */
class TicketViewModel(
    ticketQr: String,
    private val attemptId: String?,
    private val portal: PortalRepository,
    private val history: HistoryRepository,
    callers: CallerRepository,
    private val settings: SettingsRepository,
    private val requestIds: RequestIdGenerator,
) : ViewModel() {

    enum class Problem { NO_CALLER, NO_SERVER, STORAGE_FAILED }

    data class UiState(
        val ticket: TicketQr?,
        val isLoading: Boolean = true,
        val callerId: String? = null,
        val serverBaseUrl: String? = null,
        val tabletIdInput: String = "",
        val tabletIdValidation: TabletIdRules.Validation = TabletIdRules.Validation.Valid,
        val requestId: String = "",
        /** True once [requestId] has been sent; from then on it can only be resent unchanged. */
        val requestSent: Boolean = false,
        /** Reopened from the history without a final answer: it must be retried. */
        val restoredUnconfirmed: Boolean = false,
        val runningScenario: TestScenario? = null,
        val lastAttempt: RedemptionAttempt? = null,
        val problem: Problem? = null,
    ) {
        val isRunning: Boolean
            get() = runningScenario != null

        val canRedeem: Boolean
            get() = ticket != null && !isLoading && !requestSent && !isRunning &&
                tabletIdValidation == TabletIdRules.Validation.Valid &&
                callerId != null && serverBaseUrl != null

        val canRunFollowUpTests: Boolean
            get() = ticket != null && requestSent && !isRunning && callerId != null && serverBaseUrl != null
    }

    private val local = MutableStateFlow(UiState(ticket = TicketQr.parse(ticketQr)))

    val uiState: StateFlow<UiState> = combine(local, callers.credentials, settings.settings) { screen, caller, saved ->
        screen.copy(callerId = caller?.callerId, serverBaseUrl = saved.server.baseUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), local.value)

    init {
        viewModelScope.launch { load() }
    }

    fun onTabletIdChange(value: String) {
        if (local.value.requestSent || local.value.isRunning) return
        local.update { it.copy(tabletIdInput = value, tabletIdValidation = TabletIdRules.validate(value)) }
    }

    /** Starts over with a fresh request ID; the tablet ID becomes editable again. */
    fun onNewRequestId() {
        if (local.value.isRunning) return
        local.update {
            it.copy(
                requestId = requestIds.next(),
                requestSent = false,
                restoredUnconfirmed = false,
                lastAttempt = null,
                problem = null,
            )
        }
    }

    fun onRedeem() {
        val state = local.value
        if (state.requestSent || state.tabletIdValidation != TabletIdRules.Validation.Valid) return
        run(TestScenario.FIRST_REDEMPTION, state.requestId, state.tabletIdInput)
    }

    fun onRetrySameRequest() {
        val state = local.value
        if (!state.requestSent) return
        run(TestScenario.RETRY_SAME_REQUEST, state.requestId, state.tabletIdInput)
    }

    fun onNewRequestSameTicket() {
        val state = local.value
        if (!state.requestSent) return
        run(TestScenario.NEW_REQUEST_SAME_TICKET, requestIds.next(), state.tabletIdInput)
    }

    /** Sends the current request ID from [otherTabletId]. Returns false if that ID cannot be used. */
    fun onOtherTablet(otherTabletId: String): Boolean {
        val state = local.value
        if (!state.requestSent || otherTabletId == state.tabletIdInput || !TabletIdRules.isValid(otherTabletId)) {
            return false
        }
        run(TestScenario.OTHER_TABLET_SAME_REQUEST, state.requestId, otherTabletId)
        return true
    }

    /** A different, valid tablet ID to offer for the conflict test. */
    fun suggestedOtherTabletId(): String {
        val candidate = local.value.tabletIdInput + OTHER_TABLET_SUFFIX
        return if (TabletIdRules.isValid(candidate)) candidate else FALLBACK_OTHER_TABLET
    }

    fun onProblemShown() {
        local.update { it.copy(problem = null) }
    }

    private suspend fun load() {
        val restored = attemptId?.let { id ->
            try {
                history.get(id)
            } catch (e: IOException) {
                null
            }
        }
        if (restored != null) {
            local.update {
                it.copy(
                    isLoading = false,
                    tabletIdInput = restored.tabletId,
                    tabletIdValidation = TabletIdRules.validate(restored.tabletId),
                    requestId = restored.requestId,
                    requestSent = true,
                    restoredUnconfirmed = !restored.isFinished,
                    lastAttempt = restored.takeIf { attempt -> attempt.isFinished },
                )
            }
            return
        }
        val tabletId = try {
            settings.ensureTabletId()
        } catch (e: IOException) {
            ""
        }
        local.update {
            it.copy(
                isLoading = false,
                tabletIdInput = tabletId,
                tabletIdValidation = TabletIdRules.validate(tabletId),
                requestId = requestIds.next(),
            )
        }
    }

    private fun run(scenario: TestScenario, requestId: String, tabletId: String) {
        val ticket = local.value.ticket ?: return
        if (local.value.isRunning) return
        local.update {
            it.copy(
                runningScenario = scenario,
                problem = null,
                // The attempt is saved before it is sent, so the ID is spent from here on.
                requestSent = it.requestSent || scenario == TestScenario.FIRST_REDEMPTION,
            )
        }
        viewModelScope.launch {
            val result = try {
                portal.redeem(ticket, tabletId, requestId, scenario)
            } catch (e: IOException) {
                null
            }
            local.update { state ->
                // Without a Caller or server nothing was saved or sent, so a first
                // redemption's request ID is still unused.
                val requestSentIfNothingSent =
                    if (scenario == TestScenario.FIRST_REDEMPTION) false else state.requestSent
                when (result) {
                    is PortalRepository.RedeemResult.Completed -> state.copy(
                        runningScenario = null,
                        lastAttempt = result.attempt,
                        restoredUnconfirmed = false,
                    )
                    PortalRepository.RedeemResult.NoCaller -> state.copy(
                        runningScenario = null,
                        problem = Problem.NO_CALLER,
                        requestSent = requestSentIfNothingSent,
                    )
                    PortalRepository.RedeemResult.NoServer -> state.copy(
                        runningScenario = null,
                        problem = Problem.NO_SERVER,
                        requestSent = requestSentIfNothingSent,
                    )
                    // Storage failed: the request may already have been sent, so keep its ID.
                    null -> state.copy(runningScenario = null, problem = Problem.STORAGE_FAILED)
                }
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val OTHER_TABLET_SUFFIX = "-other"
        private const val FALLBACK_OTHER_TABLET = "tester-other-tablet"

        fun factory(container: AppContainer, ticketQr: String, attemptId: String?) = viewModelFactory {
            initializer {
                TicketViewModel(
                    ticketQr = ticketQr,
                    attemptId = attemptId,
                    portal = container.portalRepository,
                    history = container.historyRepository,
                    callers = container.callerRepository,
                    settings = container.settingsRepository,
                    requestIds = container.requestIds,
                )
            }
        }
    }
}

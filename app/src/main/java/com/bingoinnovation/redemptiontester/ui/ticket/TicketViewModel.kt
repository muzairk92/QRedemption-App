package com.bingoinnovation.redemptiontester.ui.ticket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
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
import com.bingoinnovation.redemptiontester.domain.redemption.RequestOrigin
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
 * real player tablet must behave. It is kept in [SavedStateHandle], so it survives the
 * system stopping the app in the background.
 */
class TicketViewModel(
    ticketQr: String,
    private val attemptId: String?,
    private val savedState: SavedStateHandle,
    private val portal: PortalRepository,
    private val history: HistoryRepository,
    callers: CallerRepository,
    private val settings: SettingsRepository,
    private val requestIds: RequestIdGenerator,
) : ViewModel() {

    enum class Problem { NO_CALLER, NO_SERVER, ORIGIN_CHANGED, STORAGE_FAILED }

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
        /** The Caller and server [requestId] was first sent with, once known. */
        val requestOrigin: RequestOrigin? = null,
        /** Reopened without a final answer: it must be resent unchanged. */
        val restoredUnconfirmed: Boolean = false,
        /** A follow-up test reopened without a final answer; [onResendProbe] sends it again. */
        val unconfirmedProbe: RedemptionAttempt? = null,
        val runningScenario: TestScenario? = null,
        /** The attempt being sent or shown, so it can be found again after a restart. */
        val currentAttemptId: String? = null,
        val lastAttempt: RedemptionAttempt? = null,
        val problem: Problem? = null,
    ) {
        val isRunning: Boolean
            get() = runningScenario != null

        /** True when the saved Caller or server is not the one the current request was sent with. */
        val originChanged: Boolean
            get() = requestSent && requestOrigin != null && callerId != null && serverBaseUrl != null &&
                requestOrigin != RequestOrigin(callerId = callerId, serverBaseUrl = serverBaseUrl)

        val canRedeem: Boolean
            get() = ticket != null && !isLoading && !requestSent && !isRunning &&
                tabletIdValidation == TabletIdRules.Validation.Valid &&
                callerId != null && serverBaseUrl != null

        val canRunFollowUpTests: Boolean
            get() = ticket != null && requestSent && !isRunning && callerId != null && serverBaseUrl != null &&
                !originChanged

        val canResendProbe: Boolean
            get() = unconfirmedProbe != null && !isRunning && callerId != null && serverBaseUrl != null &&
                unconfirmedProbe.origin == RequestOrigin(callerId = callerId, serverBaseUrl = serverBaseUrl)
    }

    /** The request this screen works on, as restored or created when it opens. */
    private data class LoadedRequest(
        val tabletId: String,
        val requestId: String,
        val requestSent: Boolean,
        val origin: RequestOrigin?,
        val shown: RedemptionAttempt? = null,
    )

    private val local = MutableStateFlow(UiState(ticket = TicketQr.parse(ticketQr)))

    val uiState: StateFlow<UiState> = combine(local, callers.credentials, settings.settings) { screen, caller, saved ->
        screen.copy(callerId = caller?.callerId, serverBaseUrl = saved.server.baseUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), local.value)

    init {
        viewModelScope.launch {
            load()
            // From here on, keep what is needed to continue after the system stops the app.
            local.collect(::save)
        }
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
                requestOrigin = null,
                restoredUnconfirmed = false,
                unconfirmedProbe = null,
                currentAttemptId = null,
                lastAttempt = null,
                problem = null,
            )
        }
    }

    fun onRedeem() {
        val state = local.value
        if (state.requestSent || state.tabletIdValidation != TabletIdRules.Validation.Valid) return
        run(TestScenario.FIRST_REDEMPTION, state.requestId, state.tabletIdInput, origin = null)
    }

    fun onRetrySameRequest() {
        val state = local.value
        if (!state.requestSent) return
        run(TestScenario.RETRY_SAME_REQUEST, state.requestId, state.tabletIdInput, state.requestOrigin)
    }

    fun onNewRequestSameTicket() {
        val state = local.value
        if (!state.requestSent) return
        run(TestScenario.NEW_REQUEST_SAME_TICKET, requestIds.next(), state.tabletIdInput, state.requestOrigin)
    }

    /** Sends the current request ID from [otherTabletId]. Returns false if that ID cannot be used. */
    fun onOtherTablet(otherTabletId: String): Boolean {
        val state = local.value
        if (!state.requestSent || otherTabletId == state.tabletIdInput || !TabletIdRules.isValid(otherTabletId)) {
            return false
        }
        run(TestScenario.OTHER_TABLET_SAME_REQUEST, state.requestId, otherTabletId, state.requestOrigin)
        return true
    }

    /** Sends an unanswered follow-up test again, exactly as it was first sent. */
    fun onResendProbe() {
        val probe = local.value.unconfirmedProbe ?: return
        run(probe.scenario, probe.requestId, probe.tabletId, probe.origin, resendsProbe = true)
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
        val loaded = restoreSavedRequest()
            ?: attemptId?.let { reopenFromHistory(it) }
            ?: LoadedRequest(
                tabletId = readStorage { settings.ensureTabletId() }.orEmpty(),
                requestId = requestIds.next(),
                requestSent = false,
                origin = null,
            )
        local.update { state ->
            state.copy(
                isLoading = false,
                tabletIdInput = loaded.tabletId,
                tabletIdValidation = TabletIdRules.validate(loaded.tabletId),
                requestId = loaded.requestId,
                requestSent = loaded.requestSent,
                // A first send that never got an answer still recorded where it went.
                requestOrigin = loaded.origin ?: loaded.shown
                    ?.takeIf { !it.scenario.isProbe && it.requestId == loaded.requestId }
                    ?.origin,
            ).showing(loaded.shown)
        }
    }

    /** The request this screen held before the system stopped the app, if any. */
    private suspend fun restoreSavedRequest(): LoadedRequest? {
        val requestId = savedState.get<String>(KEY_REQUEST_ID) ?: return null
        val originCaller = savedState.get<String>(KEY_ORIGIN_CALLER)
        val originServer = savedState.get<String>(KEY_ORIGIN_SERVER)
        return LoadedRequest(
            tabletId = savedState.get<String>(KEY_TABLET_ID).orEmpty(),
            requestId = requestId,
            requestSent = savedState.get<Boolean>(KEY_REQUEST_SENT) ?: false,
            origin = if (originCaller != null && originServer != null) RequestOrigin(originCaller, originServer) else null,
            shown = savedState.get<String>(KEY_ATTEMPT_ID)?.let { id -> readStorage { history.get(id) } },
        )
    }

    /**
     * An entry opened from the history. A follow-up test is shown, but the screen works on
     * the request it probed; if that request is no longer in the history, a new one starts.
     */
    private suspend fun reopenFromHistory(id: String): LoadedRequest? {
        val opened = readStorage { history.get(id) } ?: return null
        val request = readStorage { history.requestUnderTest(opened) }
        return if (request != null) {
            LoadedRequest(
                tabletId = request.tabletId,
                requestId = request.requestId,
                requestSent = true,
                origin = request.origin,
                shown = opened,
            )
        } else {
            LoadedRequest(
                tabletId = readStorage { settings.ensureTabletId() }.orEmpty(),
                requestId = requestIds.next(),
                requestSent = false,
                origin = null,
                shown = opened,
            )
        }
    }

    private fun UiState.showing(attempt: RedemptionAttempt?): UiState = when {
        attempt == null -> this
        attempt.isFinished -> copy(lastAttempt = attempt, currentAttemptId = attempt.id)
        else -> copy(
            restoredUnconfirmed = true,
            unconfirmedProbe = attempt.takeIf { it.scenario.isProbe },
            currentAttemptId = attempt.id,
        )
    }

    private fun save(state: UiState) {
        if (state.isLoading) return
        savedState[KEY_REQUEST_ID] = state.requestId
        savedState[KEY_REQUEST_SENT] = state.requestSent
        savedState[KEY_TABLET_ID] = state.tabletIdInput
        savedState[KEY_ORIGIN_CALLER] = state.requestOrigin?.callerId
        savedState[KEY_ORIGIN_SERVER] = state.requestOrigin?.serverBaseUrl
        savedState[KEY_ATTEMPT_ID] = state.currentAttemptId
    }

    private fun run(
        scenario: TestScenario,
        requestId: String,
        tabletId: String,
        origin: RequestOrigin?,
        resendsProbe: Boolean = false,
    ) {
        val ticket = local.value.ticket ?: return
        if (local.value.isRunning) return
        val newAttemptId = portal.nextAttemptId()
        local.update {
            it.copy(
                runningScenario = scenario,
                problem = null,
                currentAttemptId = newAttemptId,
                // The attempt is saved before it is sent, so the ID is spent from here on.
                requestSent = it.requestSent || scenario == TestScenario.FIRST_REDEMPTION,
            )
        }
        viewModelScope.launch {
            val result = try {
                portal.redeem(ticket, tabletId, requestId, scenario, origin, newAttemptId)
            } catch (e: IOException) {
                null
            }
            local.update { state ->
                // When nothing was saved or sent, a first redemption's request ID is still unused.
                val notSent = state.copy(
                    runningScenario = null,
                    currentAttemptId = state.lastAttempt?.id,
                    requestSent = if (scenario == TestScenario.FIRST_REDEMPTION) false else state.requestSent,
                )
                when (result) {
                    is PortalRepository.RedeemResult.Completed -> {
                        // The unanswered entry is settled by resending exactly that request.
                        val settled = if (state.unconfirmedProbe != null) {
                            resendsProbe
                        } else {
                            scenario == TestScenario.RETRY_SAME_REQUEST
                        }
                        state.copy(
                            runningScenario = null,
                            lastAttempt = result.attempt,
                            restoredUnconfirmed = state.restoredUnconfirmed && !settled,
                            unconfirmedProbe = state.unconfirmedProbe.takeUnless { resendsProbe },
                            requestOrigin = if (scenario == TestScenario.FIRST_REDEMPTION) {
                                result.attempt.origin
                            } else {
                                state.requestOrigin
                            },
                        )
                    }
                    PortalRepository.RedeemResult.NoCaller -> notSent.copy(problem = Problem.NO_CALLER)
                    PortalRepository.RedeemResult.NoServer -> notSent.copy(problem = Problem.NO_SERVER)
                    PortalRepository.RedeemResult.OriginChanged -> notSent.copy(problem = Problem.ORIGIN_CHANGED)
                    // Storage failed: the request may already have been sent, so keep its ID.
                    null -> state.copy(runningScenario = null, problem = Problem.STORAGE_FAILED)
                }
            }
        }
    }

    /** Reads stored data, treating a storage failure as "not found". */
    private suspend fun <T> readStorage(block: suspend () -> T?): T? = try {
        block()
    } catch (e: IOException) {
        null
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val OTHER_TABLET_SUFFIX = "-other"
        private const val FALLBACK_OTHER_TABLET = "tester-other-tablet"

        private const val KEY_REQUEST_ID = "request_id"
        private const val KEY_REQUEST_SENT = "request_sent"
        private const val KEY_TABLET_ID = "tablet_id"
        private const val KEY_ORIGIN_CALLER = "origin_caller_id"
        private const val KEY_ORIGIN_SERVER = "origin_server"
        private const val KEY_ATTEMPT_ID = "attempt_id"

        fun factory(container: AppContainer, ticketQr: String, attemptId: String?) = viewModelFactory {
            initializer {
                TicketViewModel(
                    ticketQr = ticketQr,
                    attemptId = attemptId,
                    savedState = createSavedStateHandle(),
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

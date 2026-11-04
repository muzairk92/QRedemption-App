package com.bingoinnovation.redemptiontester.ui.ticket

import androidx.lifecycle.SavedStateHandle
import com.bingoinnovation.redemptiontester.data.PortalRepository
import com.bingoinnovation.redemptiontester.data.storage.AppSettings
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.data.storage.Vault
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.RequestIdGenerator
import com.bingoinnovation.redemptiontester.domain.redemption.RequestOrigin
import com.bingoinnovation.redemptiontester.domain.redemption.TabletIdRules
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict
import com.bingoinnovation.redemptiontester.domain.server.ServerPreset
import com.bingoinnovation.redemptiontester.domain.server.ServerSettings
import com.bingoinnovation.redemptiontester.testing.FakePortalGateway
import com.bingoinnovation.redemptiontester.testing.InMemoryDataStore
import com.bingoinnovation.redemptiontester.testing.MainDispatcherRule
import com.bingoinnovation.redemptiontester.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TicketViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gateway = FakePortalGateway()
    private val vault = InMemoryDataStore(Vault(caller = TestData.credentials))
    private val history = HistoryRepository(vault)
    private val callers = CallerRepository(vault)
    private val settings = SettingsRepository(InMemoryDataStore(AppSettings(tabletId = TestData.TABLET_ID)))
    private val requestIds = SequentialRequestIds(
        TestData.REQUEST_ID,
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222",
    )
    private var now = 1_000L

    /**
     * @param savedState what the system kept for the screen; pass a [snapshot] of an earlier
     *   one to simulate the app being stopped and restored.
     * @param store the phone's stored vault; pass a copy to simulate what was on disk.
     */
    private fun TestScope.viewModel(
        attemptId: String? = null,
        savedState: SavedStateHandle = SavedStateHandle(),
        store: InMemoryDataStore<Vault> = vault,
    ): TicketViewModel {
        val history = HistoryRepository(store)
        val callers = CallerRepository(store)
        val portal = PortalRepository(
            gateway = gateway,
            callers = callers,
            settings = settings,
            history = history,
            // The test scope itself, not backgroundScope: advanceUntilIdle() never runs
            // work queued only in backgroundScope, so requests would never be sent.
            externalScope = this,
            clock = { now++ },
        )
        val viewModel = TicketViewModel(
            ticketQr = TestData.TICKET_TEXT,
            attemptId = attemptId,
            savedState = savedState,
            portal = portal,
            history = history,
            callers = callers,
            settings = settings,
            requestIds = requestIds,
        )
        // uiState only updates while collected, as it does on screen.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun opensReadyToRedeem() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.ticket).isEqualTo(TestData.ticket)
        assertThat(state.tabletIdInput).isEqualTo(TestData.TABLET_ID)
        assertThat(state.requestId).isEqualTo(TestData.REQUEST_ID)
        assertThat(state.callerId).isEqualTo(TestData.CALLER_ID)
        assertThat(state.serverBaseUrl).isEqualTo("https://staging.bingoinnovation.com")
        assertThat(state.canRedeem).isTrue()
    }

    @Test
    fun redeem_showsResult_andLocksTheRequest() = runTest {
        gateway.enqueue(TestData.success())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRedeem()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.requestSent).isTrue()
        assertThat(state.isRunning).isFalse()
        assertThat(state.lastAttempt?.scenario).isEqualTo(TestScenario.FIRST_REDEMPTION)
        assertThat(state.lastAttempt?.verdict).isEqualTo(Verdict.AS_EXPECTED)
        assertThat(state.canRedeem).isFalse()

        // The tablet ID that sent the request can no longer change.
        viewModel.onTabletIdChange("changed")
        assertThat(viewModel.uiState.value.tabletIdInput).isEqualTo(TestData.TABLET_ID)
    }

    @Test
    fun followUpTests_reuseOrReplaceTheRequestIdCorrectly() = runTest {
        gateway.enqueue(
            TestData.success(),
            TestData.success(recovered = true),
            TestData.rejected("already_redeemed", 409),
            TestData.rejected("request_id_conflict", 409),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRedeem()
        advanceUntilIdle()
        viewModel.onRetrySameRequest()
        advanceUntilIdle()
        viewModel.onNewRequestSameTicket()
        advanceUntilIdle()
        assertThat(viewModel.onOtherTablet(TestData.OTHER_TABLET_ID)).isTrue()
        advanceUntilIdle()

        val sent = gateway.redeemRequests.map { (_, request) -> request.requestId to request.tabletId }
        assertThat(sent).containsExactly(
            TestData.REQUEST_ID to TestData.TABLET_ID,
            TestData.REQUEST_ID to TestData.TABLET_ID,
            "11111111-1111-4111-8111-111111111111" to TestData.TABLET_ID,
            TestData.REQUEST_ID to TestData.OTHER_TABLET_ID,
        ).inOrder()
        val verdicts = history.attempts.first().map { it.scenario to it.verdict }
        assertThat(verdicts).containsExactly(
            TestScenario.OTHER_TABLET_SAME_REQUEST to Verdict.AS_EXPECTED,
            TestScenario.NEW_REQUEST_SAME_TICKET to Verdict.AS_EXPECTED,
            TestScenario.RETRY_SAME_REQUEST to Verdict.AS_EXPECTED,
            TestScenario.FIRST_REDEMPTION to Verdict.AS_EXPECTED,
        ).inOrder()
        // The screen's own request is still the original one.
        assertThat(viewModel.uiState.value.requestId).isEqualTo(TestData.REQUEST_ID)
    }

    @Test
    fun otherTablet_refusesTheSameOrAnInvalidTabletId() = runTest {
        gateway.enqueue(TestData.success())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onRedeem()
        advanceUntilIdle()

        assertThat(viewModel.onOtherTablet(TestData.TABLET_ID)).isFalse()
        assertThat(viewModel.onOtherTablet("")).isFalse()
        assertThat(viewModel.suggestedOtherTabletId()).isEqualTo("${TestData.TABLET_ID}-other")
        assertThat(gateway.redeemRequests).hasSize(1)
    }

    @Test
    fun invalidTabletId_blocksRedeem() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTabletIdChange("bad\nid")
        viewModel.onRedeem()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.tabletIdValidation).isEqualTo(TabletIdRules.Validation.ControlCharacter)
        assertThat(viewModel.uiState.value.canRedeem).isFalse()
        assertThat(gateway.redeemRequests).isEmpty()
    }

    @Test
    fun startOver_givesANewUnsentRequest() = runTest {
        gateway.enqueue(TestData.success())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onRedeem()
        advanceUntilIdle()

        viewModel.onNewRequestId()

        val state = viewModel.uiState.value
        assertThat(state.requestId).isEqualTo("11111111-1111-4111-8111-111111111111")
        assertThat(state.requestSent).isFalse()
        assertThat(state.lastAttempt).isNull()
    }

    @Test
    fun reopeningAnUnconfirmedAttempt_offersOnlyARetryOfTheSameRequest() = runTest {
        history.save(
            RedemptionAttempt(
                id = "pending",
                scenario = TestScenario.FIRST_REDEMPTION,
                requestId = "33333333-3333-4333-8333-333333333333",
                ticketQr = TestData.TICKET_TEXT,
                tabletId = "tablet-7",
                callerId = TestData.CALLER_ID,
                serverBaseUrl = "https://staging.bingoinnovation.com",
                startedAtMillis = 1,
            ),
        )
        gateway.enqueue(TestData.success(recovered = true, tabletId = "tablet-7"))
        val viewModel = viewModel(attemptId = "pending")
        advanceUntilIdle()

        val restored = viewModel.uiState.value
        assertThat(restored.requestSent).isTrue()
        assertThat(restored.restoredUnconfirmed).isTrue()
        assertThat(restored.tabletIdInput).isEqualTo("tablet-7")

        viewModel.onRedeem() // ignored: the request was already sent
        viewModel.onRetrySameRequest()
        advanceUntilIdle()

        val (_, request) = gateway.redeemRequests.single()
        assertThat(request.requestId).isEqualTo("33333333-3333-4333-8333-333333333333")
        assertThat(request.tabletId).isEqualTo("tablet-7")
        assertThat(viewModel.uiState.value.restoredUnconfirmed).isFalse()
    }

    @Test
    fun withoutCaller_redeemIsDisabled() = runTest {
        callers.clear()
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canRedeem).isFalse()
    }

    @Test
    fun reopeningAnotherTabletTest_retriesTheRequestItProbed() = runTest {
        gateway.enqueue(
            TestData.success(),
            TestData.rejected("request_id_conflict", 409),
            TestData.success(recovered = true),
        )
        val first = viewModel()
        advanceUntilIdle()
        first.onRedeem()
        advanceUntilIdle()
        first.onOtherTablet(TestData.OTHER_TABLET_ID)
        advanceUntilIdle()
        val probe = history.attempts.first().first { it.scenario == TestScenario.OTHER_TABLET_SAME_REQUEST }

        val reopened = viewModel(attemptId = probe.id)
        advanceUntilIdle()
        assertThat(reopened.uiState.value.lastAttempt?.id).isEqualTo(probe.id)
        assertThat(reopened.uiState.value.tabletIdInput).isEqualTo(TestData.TABLET_ID)
        reopened.onRetrySameRequest()
        advanceUntilIdle()

        val (_, retry) = gateway.redeemRequests.last()
        assertThat(retry.requestId to retry.tabletId).isEqualTo(TestData.REQUEST_ID to TestData.TABLET_ID)
        assertThat(reopened.uiState.value.lastAttempt?.verdict).isEqualTo(Verdict.AS_EXPECTED)
    }

    @Test
    fun reopeningANewRequestTest_retriesTheOriginalRequest() = runTest {
        gateway.enqueue(
            TestData.success(),
            TestData.rejected("already_redeemed", 409),
            TestData.success(recovered = true),
        )
        val first = viewModel()
        advanceUntilIdle()
        first.onRedeem()
        advanceUntilIdle()
        first.onNewRequestSameTicket()
        advanceUntilIdle()
        val probe = history.attempts.first().first { it.scenario == TestScenario.NEW_REQUEST_SAME_TICKET }

        val reopened = viewModel(attemptId = probe.id)
        advanceUntilIdle()
        assertThat(reopened.uiState.value.requestId).isEqualTo(TestData.REQUEST_ID)
        reopened.onRetrySameRequest()
        advanceUntilIdle()

        assertThat(gateway.redeemRequests.last().second.requestId).isEqualTo(TestData.REQUEST_ID)
        assertThat(reopened.uiState.value.lastAttempt?.verdict).isEqualTo(Verdict.AS_EXPECTED)
    }

    @Test
    fun reopeningAnUnansweredTest_resendsItUnchanged() = runTest {
        gateway.enqueue(
            TestData.success(),
            RedemptionOutcome.TransportFailure(TransportFailureKind.TIMEOUT),
        )
        val first = viewModel()
        advanceUntilIdle()
        first.onRedeem()
        advanceUntilIdle()
        first.onOtherTablet(TestData.OTHER_TABLET_ID)
        advanceUntilIdle()
        // The probe's answer never arrived: on disk it is still waiting for one.
        val probe = history.attempts.first().first { it.scenario == TestScenario.OTHER_TABLET_SAME_REQUEST }
        history.save(probe.copy(outcome = null, verdict = null, finishedAtMillis = null))
        gateway.enqueue(TestData.rejected("request_id_conflict", 409))

        val reopened = viewModel(attemptId = probe.id)
        advanceUntilIdle()
        assertThat(reopened.uiState.value.restoredUnconfirmed).isTrue()
        assertThat(reopened.uiState.value.canResendProbe).isTrue()
        reopened.onResendProbe()
        advanceUntilIdle()

        val (_, resent) = gateway.redeemRequests.last()
        assertThat(resent.requestId to resent.tabletId).isEqualTo(TestData.REQUEST_ID to TestData.OTHER_TABLET_ID)
        val state = reopened.uiState.value
        assertThat(state.lastAttempt?.scenario).isEqualTo(TestScenario.OTHER_TABLET_SAME_REQUEST)
        assertThat(state.lastAttempt?.verdict).isEqualTo(Verdict.AS_EXPECTED)
        assertThat(state.restoredUnconfirmed).isFalse()
        assertThat(state.unconfirmedProbe).isNull()
    }

    @Test
    fun appStoppedAfterATimeout_keepsTheSameRequest() = runTest {
        gateway.enqueue(
            RedemptionOutcome.TransportFailure(TransportFailureKind.TIMEOUT),
            TestData.success(recovered = true),
        )
        val savedState = SavedStateHandle()
        val first = viewModel(savedState = savedState)
        advanceUntilIdle()
        first.onRedeem()
        advanceUntilIdle()

        val restored = viewModel(savedState = savedState.snapshot())
        advanceUntilIdle()
        val state = restored.uiState.value
        assertThat(state.requestId).isEqualTo(TestData.REQUEST_ID)
        assertThat(state.requestSent).isTrue()
        assertThat(state.canRedeem).isFalse()
        assertThat(state.lastAttempt?.verdict).isEqualTo(Verdict.NOT_CONFIRMED)

        restored.onRetrySameRequest()
        advanceUntilIdle()
        assertThat(gateway.redeemRequests.map { it.second.requestId })
            .containsExactly(TestData.REQUEST_ID, TestData.REQUEST_ID)
    }

    @Test
    fun appStoppedWhileSending_offersARetryOfTheSameRequest() = runTest {
        var savedWhileSending: SavedStateHandle? = null
        var vaultWhileSending: Vault? = null
        val savedState = SavedStateHandle()
        gateway.onRedeem = {
            savedWhileSending = savedState.snapshot()
            vaultWhileSending = vault.data.first()
        }
        gateway.enqueue(TestData.success())
        val first = viewModel(savedState = savedState)
        advanceUntilIdle()
        first.onRedeem()
        advanceUntilIdle()

        // Restore exactly what existed while the request was in flight.
        val restored = viewModel(
            savedState = checkNotNull(savedWhileSending),
            store = InMemoryDataStore(checkNotNull(vaultWhileSending)),
        )
        advanceUntilIdle()

        val state = restored.uiState.value
        assertThat(state.requestId).isEqualTo(TestData.REQUEST_ID)
        assertThat(state.requestSent).isTrue()
        assertThat(state.restoredUnconfirmed).isTrue()
        assertThat(state.unconfirmedProbe).isNull()
        assertThat(state.canRunFollowUpTests).isTrue()
        assertThat(state.requestOrigin)
            .isEqualTo(RequestOrigin(TestData.CALLER_ID, "https://staging.bingoinnovation.com"))
    }

    @Test
    fun changedServer_blocksFollowUpTestsOfTheRequest() = runTest {
        gateway.enqueue(TestData.success())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onRedeem()
        advanceUntilIdle()

        settings.setServer(ServerSettings(preset = ServerPreset.CUSTOM, customBaseUrl = "https://other.example"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.originChanged).isTrue()
        assertThat(viewModel.uiState.value.canRunFollowUpTests).isFalse()

        viewModel.onRetrySameRequest()
        advanceUntilIdle()
        assertThat(gateway.redeemRequests).hasSize(1)
        assertThat(viewModel.uiState.value.problem).isEqualTo(TicketViewModel.Problem.ORIGIN_CHANGED)

        // Starting over makes a new request for the new server.
        viewModel.onNewRequestId()
        assertThat(viewModel.uiState.value.originChanged).isFalse()
        assertThat(viewModel.uiState.value.canRedeem).isTrue()
    }

    /** What the system restores after stopping the app: a copy of the saved values. */
    private fun SavedStateHandle.snapshot() = SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private class SequentialRequestIds(vararg ids: String) : RequestIdGenerator {
        private val queue = ArrayDeque(ids.toList())

        override fun next(): String = queue.removeFirst()
    }
}

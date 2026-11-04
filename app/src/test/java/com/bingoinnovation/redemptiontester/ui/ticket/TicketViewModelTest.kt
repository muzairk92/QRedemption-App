package com.bingoinnovation.redemptiontester.ui.ticket

import com.bingoinnovation.redemptiontester.data.PortalRepository
import com.bingoinnovation.redemptiontester.data.storage.AppSettings
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.data.storage.Vault
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RequestIdGenerator
import com.bingoinnovation.redemptiontester.domain.redemption.TabletIdRules
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict
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

    private fun TestScope.viewModel(attemptId: String? = null): TicketViewModel {
        val portal = PortalRepository(
            gateway = gateway,
            callers = callers,
            settings = settings,
            history = history,
            externalScope = backgroundScope,
            clock = { now++ },
        )
        val viewModel = TicketViewModel(
            ticketQr = TestData.TICKET_TEXT,
            attemptId = attemptId,
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

    private class SequentialRequestIds(vararg ids: String) : RequestIdGenerator {
        private val queue = ArrayDeque(ids.toList())

        override fun next(): String = queue.removeFirst()
    }
}

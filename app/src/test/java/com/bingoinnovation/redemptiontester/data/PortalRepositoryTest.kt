package com.bingoinnovation.redemptiontester.data

import com.bingoinnovation.redemptiontester.data.storage.AppSettings
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.data.storage.Vault
import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict
import com.bingoinnovation.redemptiontester.domain.server.ServerPreset
import com.bingoinnovation.redemptiontester.domain.server.ServerSettings
import com.bingoinnovation.redemptiontester.testing.FakePortalGateway
import com.bingoinnovation.redemptiontester.testing.InMemoryDataStore
import com.bingoinnovation.redemptiontester.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PortalRepositoryTest {

    private val gateway = FakePortalGateway()
    private val vault = InMemoryDataStore(Vault(caller = TestData.credentials))
    private val settingsStore = InMemoryDataStore(AppSettings(tabletId = TestData.TABLET_ID))
    private val callers = CallerRepository(vault)
    private val history = HistoryRepository(vault)
    private val settings = SettingsRepository(settingsStore)
    private var now = 1_000L
    private var nextId = 0

    private fun TestScope.repository() = PortalRepository(
        gateway = gateway,
        callers = callers,
        settings = settings,
        history = history,
        externalScope = backgroundScope,
        clock = { now++ },
        newAttemptId = { "attempt-${nextId++}" },
    )

    private suspend fun PortalRepository.redeem(
        scenario: TestScenario,
        requestId: String = TestData.REQUEST_ID,
        tabletId: String = TestData.TABLET_ID,
    ) = (redeem(TestData.ticket, tabletId, requestId, scenario) as PortalRepository.RedeemResult.Completed).attempt

    @Test
    fun attemptIsSavedBeforeTheRequestIsSent() = runTest {
        var savedWhileSending: List<String> = emptyList()
        gateway.onRedeem = { savedWhileSending = history.attempts.first().map { "${it.requestId}:${it.outcome}" } }
        gateway.enqueue(TestData.success())

        repository().redeem(TestScenario.FIRST_REDEMPTION)

        assertThat(savedWhileSending).containsExactly("${TestData.REQUEST_ID}:null")
    }

    @Test
    fun sendsTheSavedCallerToTheSelectedServer() = runTest {
        gateway.enqueue(TestData.success())

        repository().redeem(TestScenario.FIRST_REDEMPTION)

        val (baseUrl, request) = gateway.redeemRequests.single()
        assertThat(baseUrl).isEqualTo("https://staging.bingoinnovation.com")
        assertThat(request).isEqualTo(TestData.request())
    }

    @Test
    fun recordsOutcomeAndVerdict() = runTest {
        gateway.enqueue(TestData.success())

        val attempt = repository().redeem(TestScenario.FIRST_REDEMPTION)

        assertThat(attempt.outcome).isEqualTo(TestData.success())
        assertThat(attempt.verdict).isEqualTo(Verdict.AS_EXPECTED)
        assertThat(attempt.finishedAtMillis).isNotNull()
        assertThat(history.attempts.first()).containsExactly(attempt)
    }

    @Test
    fun retryOfAnAcceptedRequest_mustMatchTheOriginal() = runTest {
        val repository = repository()
        gateway.enqueue(TestData.success(), TestData.success(recovered = true))

        repository.redeem(TestScenario.FIRST_REDEMPTION)
        val retry = repository.redeem(TestScenario.RETRY_SAME_REQUEST)

        val checks = (retry.outcome as RedemptionOutcome.Success).checks
        assertThat(checks.single { it.rule == ContractRule.RECOVERY_MATCHES_ORIGINAL }.passed).isTrue()
        assertThat(retry.verdict).isEqualTo(Verdict.AS_EXPECTED)
    }

    @Test
    fun retryReturningADifferentRedemption_isNotAsExpected() = runTest {
        val repository = repository()
        gateway.enqueue(
            TestData.success(),
            TestData.success(recovered = true, redemptionId = "00000000-0000-4000-8000-000000000000"),
        )

        repository.redeem(TestScenario.FIRST_REDEMPTION)
        val retry = repository.redeem(TestScenario.RETRY_SAME_REQUEST)

        val checks = (retry.outcome as RedemptionOutcome.Success).checks
        assertThat(checks.single { it.rule == ContractRule.RECOVERY_MATCHES_ORIGINAL }.passed).isFalse()
        assertThat(retry.verdict).isEqualTo(Verdict.NOT_AS_EXPECTED)
    }

    @Test
    fun newScanOfARedeemedTicket_expectsAlreadyRedeemed() = runTest {
        val repository = repository()
        gateway.enqueue(TestData.success(), TestData.success())

        repository.redeem(TestScenario.FIRST_REDEMPTION)
        val second = repository.redeem(
            TestScenario.NEW_REQUEST_SAME_TICKET,
            requestId = "11111111-1111-4111-8111-111111111111",
        )

        // Accepted twice: a double redemption, never "as expected".
        assertThat(second.verdict).isEqualTo(Verdict.NOT_AS_EXPECTED)
    }

    @Test
    fun timeoutLeavesTheRequestUnconfirmed() = runTest {
        gateway.enqueue(RedemptionOutcome.TransportFailure(TransportFailureKind.TIMEOUT))

        val attempt = repository().redeem(TestScenario.FIRST_REDEMPTION)

        assertThat(attempt.verdict).isEqualTo(Verdict.NOT_CONFIRMED)
        assertThat(history.unconfirmedCount.first()).isEqualTo(1)
    }

    @Test
    fun laterAnswerForTheSameRequest_clearsTheUnconfirmedCount() = runTest {
        val repository = repository()
        gateway.enqueue(RedemptionOutcome.TransportFailure(TransportFailureKind.TIMEOUT), TestData.success(recovered = true))

        repository.redeem(TestScenario.FIRST_REDEMPTION)
        repository.redeem(TestScenario.RETRY_SAME_REQUEST)

        assertThat(history.unconfirmedCount.first()).isEqualTo(0)
    }

    @Test
    fun withoutCallerOrServer_nothingIsSentOrSaved() = runTest {
        callers.clear()
        val noCaller = repository().redeem(TestData.ticket, TestData.TABLET_ID, TestData.REQUEST_ID, TestScenario.FIRST_REDEMPTION)
        assertThat(noCaller).isEqualTo(PortalRepository.RedeemResult.NoCaller)

        callers.save(TestData.credentials)
        settings.setServer(ServerSettings(preset = ServerPreset.CUSTOM, customBaseUrl = ""))
        val noServer = repository().redeem(TestData.ticket, TestData.TABLET_ID, TestData.REQUEST_ID, TestScenario.FIRST_REDEMPTION)
        assertThat(noServer).isEqualTo(PortalRepository.RedeemResult.NoServer)

        assertThat(gateway.redeemRequests).isEmpty()
        assertThat(history.attempts.first()).isEmpty()
    }
}

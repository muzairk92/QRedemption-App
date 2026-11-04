package com.bingoinnovation.redemptiontester.data

import com.bingoinnovation.redemptiontester.data.network.PortalGateway
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.domain.caller.CallerCheck
import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.redemption.ContractCheck
import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.domain.redemption.Redemption
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionRequest
import com.bingoinnovation.redemptiontester.domain.redemption.ScenarioEvaluator
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.ticket.TicketQr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import java.util.UUID

/** Runs Portal requests and records every redemption attempt in the history. */
class PortalRepository(
    private val gateway: PortalGateway,
    private val callers: CallerRepository,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    /** Application-wide scope, so leaving a screen never abandons a request half-recorded. */
    private val externalScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newAttemptId: () -> String = { UUID.randomUUID().toString() },
) {

    sealed interface RedeemResult {
        data class Completed(val attempt: RedemptionAttempt) : RedeemResult

        data object NoCaller : RedeemResult

        data object NoServer : RedeemResult
    }

    sealed interface VerifyResult {
        data class Completed(val check: CallerCheck) : VerifyResult

        data object NoServer : VerifyResult
    }

    suspend fun redeem(
        ticket: TicketQr,
        tabletId: String,
        requestId: String,
        scenario: TestScenario,
    ): RedeemResult = externalScope.async { performRedeem(ticket, tabletId, requestId, scenario) }.await()

    suspend fun verifyCaller(credentials: CallerCredentials): VerifyResult {
        val baseUrl = settings.current().server.baseUrl ?: return VerifyResult.NoServer
        return VerifyResult.Completed(gateway.verifyCaller(baseUrl, credentials))
    }

    private suspend fun performRedeem(
        ticket: TicketQr,
        tabletId: String,
        requestId: String,
        scenario: TestScenario,
    ): RedeemResult {
        val caller = callers.current() ?: return RedeemResult.NoCaller
        val baseUrl = settings.current().server.baseUrl ?: return RedeemResult.NoServer

        val originalSuccess = history.originalSuccess(requestId, tabletId)
        val ticketRedeemedBefore = history.wasTicketRedeemed(ticket.value)

        // Saved before sending: the request ID must survive a crash so it can be retried.
        val started = RedemptionAttempt(
            id = newAttemptId(),
            scenario = scenario,
            requestId = requestId,
            ticketQr = ticket.value,
            tabletId = tabletId,
            callerId = caller.callerId,
            serverBaseUrl = baseUrl,
            startedAtMillis = clock(),
        )
        history.save(started)

        val response = gateway.redeem(baseUrl, RedemptionRequest(caller, ticket, tabletId, requestId))
        val outcome = withRecoveryCheck(response, originalSuccess)
        val finished = started.copy(
            finishedAtMillis = clock(),
            outcome = outcome,
            verdict = ScenarioEvaluator.evaluate(scenario, outcome, originalSuccess, ticketRedeemedBefore),
        )
        history.save(finished)
        return RedeemResult.Completed(finished)
    }

    /**
     * When this app already saw the request accepted, a resend must be recognised as a
     * recovery and must repeat the same saved purchase.
     */
    private fun withRecoveryCheck(outcome: RedemptionOutcome, original: Redemption?): RedemptionOutcome {
        if (outcome !is RedemptionOutcome.Success || original == null) return outcome
        val matches = outcome.redemption.recovered && outcome.redemption.hasSameFactsAs(original)
        return outcome.copy(checks = outcome.checks + ContractCheck(ContractRule.RECOVERY_MATCHES_ORIGINAL, matches))
    }
}

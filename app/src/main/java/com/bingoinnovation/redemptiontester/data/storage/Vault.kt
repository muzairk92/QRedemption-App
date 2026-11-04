package com.bingoinnovation.redemptiontester.data.storage

import androidx.datastore.core.DataStore
import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.redemption.Redemption
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.redemption.needsRetryWithSameRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Everything secret, kept in one encrypted DataStore file: the Caller credentials and
 * the redemption history (which holds ticket QRs).
 */
@Serializable
data class Vault(
    val caller: CallerCredentials? = null,
    val attempts: List<RedemptionAttempt> = emptyList(),
)

class CallerRepository(private val store: DataStore<Vault>) {

    val credentials: Flow<CallerCredentials?> = store.data.map { it.caller }.distinctUntilChanged()

    suspend fun current(): CallerCredentials? = credentials.first()

    suspend fun save(credentials: CallerCredentials) {
        store.updateData { it.copy(caller = credentials) }
    }

    suspend fun clear() {
        store.updateData { it.copy(caller = null) }
    }
}

class HistoryRepository(private val store: DataStore<Vault>) {

    /** Newest first. */
    val attempts: Flow<List<RedemptionAttempt>> =
        store.data.map { vault -> vault.attempts.sortedByDescending { it.startedAtMillis } }.distinctUntilChanged()

    /**
     * Requests whose latest attempt has no final answer, so they should be retried with
     * the same request ID (for example after a timeout or an app restart).
     */
    val unconfirmedCount: Flow<Int> = attempts.map { list ->
        list.groupBy { it.requestId to it.tabletId }
            .values
            .count { group -> group.maxBy { it.startedAtMillis }.outcome.needsRetryWithSameRequest }
    }.distinctUntilChanged()

    suspend fun get(id: String): RedemptionAttempt? = all().firstOrNull { it.id == id }

    /** Adds [attempt], or replaces the entry with the same [RedemptionAttempt.id]. */
    suspend fun save(attempt: RedemptionAttempt) {
        store.updateData { vault ->
            val others = vault.attempts.filterNot { it.id == attempt.id }
            vault.copy(
                attempts = (others + attempt)
                    .sortedByDescending { it.startedAtMillis }
                    .take(MAX_ATTEMPTS),
            )
        }
    }

    suspend fun clear() {
        store.updateData { it.copy(attempts = emptyList()) }
    }

    /** The first successful answer for this request ID from this tablet, if this app saw one. */
    suspend fun originalSuccess(requestId: String, tabletId: String): Redemption? =
        all()
            .filter { it.requestId == requestId && it.tabletId == tabletId }
            .sortedBy { it.startedAtMillis }
            .firstNotNullOfOrNull { (it.outcome as? RedemptionOutcome.Success)?.redemption }

    /**
     * The attempt that sent the request [attempt] belongs to. For a probe that is the
     * request it probed, if it is still in the history:
     * - another tablet, same request: the first attempt that sent that request ID;
     * - new request, same ticket: the latest request sent for the ticket before the probe.
     */
    suspend fun requestUnderTest(attempt: RedemptionAttempt): RedemptionAttempt? {
        if (!attempt.scenario.isProbe) return attempt
        val requests = all().filter { it.ticketQr == attempt.ticketQr && !it.scenario.isProbe }
        return when (attempt.scenario) {
            TestScenario.OTHER_TABLET_SAME_REQUEST ->
                requests.filter { it.requestId == attempt.requestId }.minByOrNull { it.startedAtMillis }
            else ->
                requests.filter { it.startedAtMillis <= attempt.startedAtMillis }.maxByOrNull { it.startedAtMillis }
        }
    }

    suspend fun wasTicketRedeemed(ticketQr: String): Boolean =
        all().any { it.ticketQr == ticketQr && it.outcome is RedemptionOutcome.Success }

    private suspend fun all(): List<RedemptionAttempt> = store.data.first().attempts

    private companion object {
        const val MAX_ATTEMPTS = 300
    }
}

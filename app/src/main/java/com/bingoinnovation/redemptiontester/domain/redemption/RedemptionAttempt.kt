package com.bingoinnovation.redemptiontester.domain.redemption

import kotlinx.serialization.Serializable

/**
 * One request sent (or about to be sent) to the Portal, kept in the on-device history.
 *
 * It is saved before the request leaves the phone, so the original `request_id`
 * survives a crash or restart and can be retried, as the contract requires.
 */
@Serializable
data class RedemptionAttempt(
    /** Local identity of this history entry (several entries can share a request ID). */
    val id: String,
    val scenario: TestScenario,
    val requestId: String,
    /** The exact ticket text. Stored encrypted; needed to retry the same request. */
    val ticketQr: String,
    val tabletId: String,
    val callerId: String,
    val serverBaseUrl: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    /** Null while the request is in flight, or when the app stopped before an answer. */
    val outcome: RedemptionOutcome? = null,
    val verdict: Verdict? = null,
) {
    val isFinished: Boolean
        get() = outcome != null

    override fun toString(): String =
        "RedemptionAttempt(id=$id, scenario=$scenario, requestId=$requestId, tabletId=$tabletId, " +
            "callerId=$callerId, finished=$isFinished, verdict=$verdict)"
}

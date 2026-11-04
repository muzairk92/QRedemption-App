package com.bingoinnovation.redemptiontester.domain.redemption

import kotlinx.serialization.Serializable

/**
 * The redemption behaviours this app can exercise, each with a known expected answer.
 *
 * [isProbe] is true for scenarios that do not send the request under test itself, but a
 * deliberately different one (a new request ID, or another tablet) to check how the
 * server treats it.
 */
@Serializable
enum class TestScenario(val isProbe: Boolean) {
    /** A new scan with a new `request_id`. Expected: accepted, `recovered: false`. */
    FIRST_REDEMPTION(isProbe = false),

    /** The same `request_id` and tablet again. Expected: the saved result, `recovered: true`. */
    RETRY_SAME_REQUEST(isProbe = false),

    /** The same ticket with a new `request_id`. Expected: `409 already_redeemed`. */
    NEW_REQUEST_SAME_TICKET(isProbe = true),

    /** The same `request_id` from a different tablet. Expected: `409 request_id_conflict`. */
    OTHER_TABLET_SAME_REQUEST(isProbe = true),
}

@Serializable
enum class Verdict {
    /** The server behaved as the contract says it should for this scenario. */
    AS_EXPECTED,

    /** The server answered, but not as the contract says it should. */
    NOT_AS_EXPECTED,

    /** No final answer (timeout, rate limit, temporary failure). Retry the same request. */
    NOT_CONFIRMED,
}

object ScenarioEvaluator {

    /**
     * @param originalSuccess the earlier successful answer for the same request ID and
     *   tablet, when this app has seen one.
     * @param ticketRedeemedBefore true when this app has seen this ticket accepted before.
     */
    fun evaluate(
        scenario: TestScenario,
        outcome: RedemptionOutcome,
        originalSuccess: Redemption?,
        ticketRedeemedBefore: Boolean,
    ): Verdict {
        if (outcome.isTemporary()) return Verdict.NOT_CONFIRMED

        val asExpected = when (scenario) {
            TestScenario.FIRST_REDEMPTION ->
                outcome is RedemptionOutcome.Success && outcome.allChecksPassed && !outcome.redemption.recovered

            // When this app saw the request accepted before, the server must recognise the
            // retry. When the earlier attempt never got an answer, either answer is correct.
            TestScenario.RETRY_SAME_REQUEST ->
                outcome is RedemptionOutcome.Success &&
                    outcome.allChecksPassed &&
                    (originalSuccess == null || outcome.redemption.recovered)

            TestScenario.NEW_REQUEST_SAME_TICKET -> when {
                outcome.isRejectedWith(RedemptionErrorCode.ALREADY_REDEEMED) -> true
                // The ticket had never been accepted, so this became its first redemption.
                outcome is RedemptionOutcome.Success && !ticketRedeemedBefore ->
                    return Verdict.NOT_CONFIRMED
                else -> false
            }

            TestScenario.OTHER_TABLET_SAME_REQUEST ->
                outcome.isRejectedWith(RedemptionErrorCode.REQUEST_ID_CONFLICT)
        }
        return if (asExpected) Verdict.AS_EXPECTED else Verdict.NOT_AS_EXPECTED
    }

    private fun RedemptionOutcome.isTemporary(): Boolean = when (this) {
        is RedemptionOutcome.Success -> false
        is RedemptionOutcome.Rejected -> retryable
        is RedemptionOutcome.UnexpectedResponse -> outcomeUnknown
        is RedemptionOutcome.TransportFailure -> true
    }

    private fun RedemptionOutcome.isRejectedWith(expected: RedemptionErrorCode): Boolean =
        this is RedemptionOutcome.Rejected && code == expected.wire && httpStatus == expected.httpStatus
}

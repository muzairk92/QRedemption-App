package com.bingoinnovation.redemptiontester.domain.redemption

import com.bingoinnovation.redemptiontester.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScenarioEvaluatorTest {

    private fun evaluate(
        scenario: TestScenario,
        outcome: RedemptionOutcome,
        original: Redemption? = null,
        ticketRedeemedBefore: Boolean = false,
    ) = ScenarioEvaluator.evaluate(scenario, outcome, original, ticketRedeemedBefore)

    @Test
    fun firstRedemption() {
        assertThat(evaluate(TestScenario.FIRST_REDEMPTION, TestData.success()))
            .isEqualTo(Verdict.AS_EXPECTED)
        assertThat(evaluate(TestScenario.FIRST_REDEMPTION, TestData.success(recovered = true)))
            .isEqualTo(Verdict.NOT_AS_EXPECTED)
        assertThat(evaluate(TestScenario.FIRST_REDEMPTION, TestData.rejected("already_redeemed", 409)))
            .isEqualTo(Verdict.NOT_AS_EXPECTED)
    }

    @Test
    fun firstRedemption_failedContractCheckIsNotAsExpected() {
        val broken = TestData.success().copy(checks = listOf(ContractCheck(ContractRule.TABLET_ID_ECHOED, false)))

        assertThat(evaluate(TestScenario.FIRST_REDEMPTION, broken)).isEqualTo(Verdict.NOT_AS_EXPECTED)
    }

    @Test
    fun retrySameRequest() {
        val original = TestData.redemption()

        assertThat(evaluate(TestScenario.RETRY_SAME_REQUEST, TestData.success(recovered = true), original))
            .isEqualTo(Verdict.AS_EXPECTED)
        // Seen accepted before, so a fresh acceptance would be wrong.
        assertThat(evaluate(TestScenario.RETRY_SAME_REQUEST, TestData.success(recovered = false), original))
            .isEqualTo(Verdict.NOT_AS_EXPECTED)
        // The first attempt never got an answer, so either answer is fine.
        assertThat(evaluate(TestScenario.RETRY_SAME_REQUEST, TestData.success(recovered = false)))
            .isEqualTo(Verdict.AS_EXPECTED)
        assertThat(evaluate(TestScenario.RETRY_SAME_REQUEST, TestData.rejected("ticket_voided", 410), original))
            .isEqualTo(Verdict.NOT_AS_EXPECTED)
    }

    @Test
    fun newRequestSameTicket() {
        assertThat(evaluate(TestScenario.NEW_REQUEST_SAME_TICKET, TestData.rejected("already_redeemed", 409)))
            .isEqualTo(Verdict.AS_EXPECTED)
        // A second acceptance of a ticket seen accepted before is a double redemption.
        assertThat(
            evaluate(TestScenario.NEW_REQUEST_SAME_TICKET, TestData.success(), ticketRedeemedBefore = true),
        ).isEqualTo(Verdict.NOT_AS_EXPECTED)
        // Never accepted before: this simply became the first redemption.
        assertThat(
            evaluate(TestScenario.NEW_REQUEST_SAME_TICKET, TestData.success(), ticketRedeemedBefore = false),
        ).isEqualTo(Verdict.NOT_CONFIRMED)
        // The right code with the wrong HTTP status is not the contract.
        assertThat(evaluate(TestScenario.NEW_REQUEST_SAME_TICKET, TestData.rejected("already_redeemed", 400)))
            .isEqualTo(Verdict.NOT_AS_EXPECTED)
    }

    @Test
    fun otherTabletSameRequest() {
        assertThat(evaluate(TestScenario.OTHER_TABLET_SAME_REQUEST, TestData.rejected("request_id_conflict", 409)))
            .isEqualTo(Verdict.AS_EXPECTED)
        assertThat(evaluate(TestScenario.OTHER_TABLET_SAME_REQUEST, TestData.success(recovered = true)))
            .isEqualTo(Verdict.NOT_AS_EXPECTED)
    }

    @Test
    fun temporaryAnswersAreNeverJudged() {
        val temporary = listOf(
            TestData.rejected("rate_limited", 429, retryable = true),
            TestData.rejected("write_outcome_unknown", 503, retryable = true),
            RedemptionOutcome.UnexpectedResponse(502, UnexpectedReason.NOT_JSON, "<html>"),
            RedemptionOutcome.TransportFailure(TransportFailureKind.TIMEOUT),
            RedemptionOutcome.TransportFailure(TransportFailureKind.HOST_NOT_FOUND),
        )
        TestScenario.entries.forEach { scenario ->
            temporary.forEach { outcome ->
                assertThat(evaluate(scenario, outcome)).isEqualTo(Verdict.NOT_CONFIRMED)
            }
        }
    }

    @Test
    fun needsRetryWithSameRequest() {
        assertThat((null as RedemptionOutcome?).needsRetryWithSameRequest).isTrue()
        assertThat(TestData.success().needsRetryWithSameRequest).isFalse()
        assertThat(TestData.rejected("already_redeemed", 409).needsRetryWithSameRequest).isFalse()
        assertThat(TestData.rejected("write_outcome_unknown", 503, retryable = true).needsRetryWithSameRequest).isTrue()
        assertThat(RedemptionOutcome.TransportFailure(TransportFailureKind.TIMEOUT).needsRetryWithSameRequest).isTrue()
        assertThat(RedemptionOutcome.TransportFailure(TransportFailureKind.HOST_NOT_FOUND).needsRetryWithSameRequest)
            .isFalse()
    }

    @Test
    fun hasSameFactsAs_ignoresDisplayNamesOnly() {
        val original = TestData.redemption()
        val renamed = original.copy(
            recovered = true,
            games = original.games.map { it.copy(displayName = "Renamed") },
        )

        assertThat(renamed.hasSameFactsAs(original)).isTrue()
        assertThat(original.copy(redemptionId = "00000000-0000-4000-8000-000000000000").hasSameFactsAs(original))
            .isFalse()
        assertThat(original.copy(games = original.games.map { it.copy(faces = it.faces + 1) }).hasSameFactsAs(original))
            .isFalse()
    }
}

package com.bingoinnovation.redemptiontester.ui.ticket

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.domain.redemption.ContractCheck
import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.domain.redemption.GameTypeFaces
import com.bingoinnovation.redemptiontester.domain.redemption.Redemption
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.redemption.TicketPackage
import com.bingoinnovation.redemptiontester.domain.redemption.TicketSession
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict
import com.bingoinnovation.redemptiontester.ui.theme.RedemptionTesterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedemptionResultTest {

    // RedemptionResult is taller than a phone screen and is not scrollable on its own,
    // so these tests check that content exists rather than that it is on screen.
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val redemption = Redemption(
        redemptionId = "7f8f8c92-1916-4ee0-9455-2473ae9b46e7",
        requestId = "8dd0aac6-65fb-4ecf-9a49-786951473221",
        playerTabletId = "staging-tablet-A",
        recovered = false,
        session = TicketSession(1, "Small Frames — 17:00", "2026-11-09", "17:00:00", "America/New_York", "2026-11-09T22:00:00Z"),
        ticketPackage = TicketPackage(1, "Package 1"),
        games = listOf(
            GameTypeFaces(10, 24, "Regular"),
            GameTypeFaces(12, 18, "Late Bird"),
            GameTypeFaces(14, 4, null),
        ),
    )

    private fun attempt(outcome: RedemptionOutcome, verdict: Verdict, scenario: TestScenario) = RedemptionAttempt(
        id = "a1",
        scenario = scenario,
        requestId = redemption.requestId,
        ticketQr = "BC1.12." + "ab".repeat(32),
        tabletId = redemption.playerTabletId,
        callerId = "201-4827193",
        serverBaseUrl = "https://staging.bingoinnovation.com",
        startedAtMillis = 1_793_736_060_000L,
        finishedAtMillis = 1_793_736_061_000L,
        outcome = outcome,
        verdict = verdict,
    )

    @Test
    fun success_showsVerdictSessionAndEveryGameType() {
        val checks = ContractRule.entries.filter { it != ContractRule.RECOVERY_MATCHES_ORIGINAL }
            .map { ContractCheck(it, passed = true) }
        composeRule.setContent {
            RedemptionTesterTheme {
                RedemptionResult(
                    attempt(RedemptionOutcome.Success(redemption, checks), Verdict.AS_EXPECTED, TestScenario.FIRST_REDEMPTION),
                )
            }
        }

        composeRule.onNodeWithTag(TEST_TAG_VERDICT).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.result_redeemed_title)).assertExists()
        composeRule.onNodeWithText(redemption.redemptionId).assertExists()
        composeRule.onNodeWithText("11/09/2026 · 5:00 PM (America/New_York)").assertExists()
        composeRule.onAllNodesWithTag(TEST_TAG_GAME_ROW).assertCountEquals(3)
        composeRule.onNodeWithText("Late Bird").assertExists()
        // A missing Portal name falls back to the stable ID.
        composeRule.onNodeWithText(context.getString(R.string.game_type_unnamed, 14L)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.result_checks_summary, checks.size, checks.size))
            .assertExists()
    }

    @Test
    fun rejection_showsCodeGuidanceAndVerdict() {
        val rejected = RedemptionOutcome.Rejected(409, "already_redeemed", "Already redeemed.", retryable = false)
        composeRule.setContent {
            RedemptionTesterTheme {
                RedemptionResult(attempt(rejected, Verdict.AS_EXPECTED, TestScenario.NEW_REQUEST_SAME_TICKET))
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.error_already_redeemed_title)).assertExists()
        composeRule.onNodeWithText("Already redeemed.").assertExists()
        composeRule.onNodeWithText("already_redeemed").assertExists()
        composeRule.onNodeWithTag(TEST_TAG_VERDICT).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.verdict_as_expected), substring = true).assertExists()
    }
}

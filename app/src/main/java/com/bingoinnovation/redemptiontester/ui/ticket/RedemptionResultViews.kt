package com.bingoinnovation.redemptiontester.ui.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.domain.redemption.ContractCheck
import com.bingoinnovation.redemptiontester.domain.redemption.Redemption
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.TicketSession
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict
import com.bingoinnovation.redemptiontester.ui.components.AppIcon
import com.bingoinnovation.redemptiontester.ui.components.CopyButton
import com.bingoinnovation.redemptiontester.ui.components.DetailRow
import com.bingoinnovation.redemptiontester.ui.components.InfoPill
import com.bingoinnovation.redemptiontester.ui.components.SectionCard
import com.bingoinnovation.redemptiontester.ui.components.StatusBanner
import com.bingoinnovation.redemptiontester.ui.components.StatusTone
import com.bingoinnovation.redemptiontester.ui.text.expectationRes
import com.bingoinnovation.redemptiontester.ui.text.guidanceRes
import com.bingoinnovation.redemptiontester.ui.text.labelRes
import com.bingoinnovation.redemptiontester.ui.text.titleRes
import com.bingoinnovation.redemptiontester.ui.theme.AppTheme
import com.bingoinnovation.redemptiontester.ui.util.DisplayFormats

/** The verdict and every detail of one finished attempt. */
@Composable
fun RedemptionResult(attempt: RedemptionAttempt, modifier: Modifier = Modifier) {
    val outcome = attempt.outcome ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        attempt.verdict?.let { verdict ->
            VerdictBanner(
                verdict = verdict,
                title = stringResource(attempt.scenario.titleRes()),
                expectation = stringResource(attempt.scenario.expectationRes()),
            )
        }
        when (outcome) {
            is RedemptionOutcome.Success -> {
                RedemptionSummaryCard(outcome.redemption)
                GameTypesCard(outcome.redemption)
                ContractChecksCard(outcome.checks)
            }
            is RedemptionOutcome.Rejected -> RejectedCard(outcome)
            is RedemptionOutcome.UnexpectedResponse -> UnexpectedCard(outcome)
            is RedemptionOutcome.TransportFailure -> StatusBanner(
                tone = if (outcome.kind.outcomeUnknown) StatusTone.WARNING else StatusTone.ERROR,
                icon = R.drawable.ic_warning,
                title = stringResource(outcome.kind.titleRes()),
                body = stringResource(outcome.kind.guidanceRes()),
            )
        }
        Text(
            text = stringResource(R.string.result_sent_at, DisplayFormats.timestamp(attempt.startedAtMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerdictBanner(verdict: Verdict, title: String, expectation: String) {
    val (tone, icon) = when (verdict) {
        Verdict.AS_EXPECTED -> StatusTone.SUCCESS to R.drawable.ic_check_circle
        Verdict.NOT_AS_EXPECTED -> StatusTone.ERROR to R.drawable.ic_cancel
        Verdict.NOT_CONFIRMED -> StatusTone.WARNING to R.drawable.ic_help
    }
    StatusBanner(
        tone = tone,
        icon = icon,
        title = stringResource(R.string.verdict_title, stringResource(verdict.titleRes()), title),
        body = stringResource(R.string.verdict_expected, expectation),
        modifier = Modifier.testTag(TEST_TAG_VERDICT),
    )
}

@Composable
private fun RedemptionSummaryCard(redemption: Redemption) {
    SectionCard(
        title = stringResource(if (redemption.recovered) R.string.result_recovered_title else R.string.result_redeemed_title),
        icon = R.drawable.ic_check_circle,
    ) {
        Text(
            text = stringResource(
                if (redemption.recovered) R.string.result_recovered_body else R.string.result_redeemed_body,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DetailRow(
            label = stringResource(R.string.label_redemption_id),
            value = redemption.redemptionId,
            monospace = true,
            trailing = { CopyButton(redemption.redemptionId, stringResource(R.string.label_redemption_id)) },
        )
        HorizontalDivider()
        DetailRow(label = stringResource(R.string.label_session), value = redemption.session.name)
        DetailRow(label = stringResource(R.string.label_session_when), value = sessionWhen(redemption.session))
        DetailRow(
            label = stringResource(R.string.label_package),
            value = stringResource(
                R.string.package_with_id,
                redemption.ticketPackage.name,
                redemption.ticketPackage.commercePackageId,
            ),
        )
    }
}

@Composable
private fun sessionWhen(session: TicketSession): String {
    val date = DisplayFormats.sessionDate(session.date)
    val time = DisplayFormats.sessionTime(session.startTimeLocal)
    return when {
        time == null -> stringResource(R.string.session_date_no_time, date)
        session.timezoneName == null -> stringResource(R.string.session_date_time, date, time)
        else -> stringResource(R.string.session_date_time_zone, date, time, session.timezoneName)
    }
}

@Composable
private fun GameTypesCard(redemption: Redemption) {
    SectionCard(title = stringResource(R.string.result_games_title), icon = R.drawable.ic_grid_view) {
        redemption.games.forEach { game ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEST_TAG_GAME_ROW),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.displayName ?: stringResource(R.string.game_type_unnamed, game.portalGameTypeId),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.game_type_portal_id, game.portalGameTypeId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FacesPill(game.faces)
            }
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.result_total_faces),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            FacesPill(redemption.totalFaces)
        }
        Text(
            text = stringResource(R.string.result_games_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FacesPill(faces: Long) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = pluralStringResource(R.plurals.faces_count, faces.toPluralCount(), faces),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ContractChecksCard(checks: List<ContractCheck>) {
    val passed = checks.count { it.passed }
    SectionCard(title = stringResource(R.string.result_checks_title), icon = R.drawable.ic_verified) {
        Text(
            text = stringResource(R.string.result_checks_summary, passed, checks.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        checks.forEach { check ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    if (check.passed) R.drawable.ic_check_circle else R.drawable.ic_cancel,
                    contentDescription = stringResource(
                        if (check.passed) R.string.check_passed else R.string.check_failed,
                    ),
                    tint = if (check.passed) AppTheme.statusColors.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(check.rule.labelRes()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RejectedCard(rejected: RedemptionOutcome.Rejected) {
    val known = rejected.knownCode
    SectionCard(
        title = known?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.error_unknown_code_title),
        icon = R.drawable.ic_error,
    ) {
        Text(
            text = stringResource(known?.guidanceRes() ?: R.string.error_unknown_code_guidance),
            style = MaterialTheme.typography.bodyMedium,
        )
        DetailRow(label = stringResource(R.string.label_server_message), value = rejected.message)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(icon = R.drawable.ic_dns, text = stringResource(R.string.http_status, rejected.httpStatus))
            InfoPill(icon = R.drawable.ic_info, text = rejected.code)
            InfoPill(
                icon = if (rejected.retryable) R.drawable.ic_replay else R.drawable.ic_close,
                text = stringResource(if (rejected.retryable) R.string.retryable_yes else R.string.retryable_no),
            )
        }
        rejected.retryAfterSeconds?.let {
            Text(text = stringResource(R.string.retry_after_seconds, it), style = MaterialTheme.typography.bodyMedium)
        }
        if (known != null && !rejected.statusMatchesCode) {
            StatusBanner(
                tone = StatusTone.WARNING,
                icon = R.drawable.ic_warning,
                title = stringResource(R.string.status_mismatch, rejected.httpStatus, known.httpStatus),
            )
        }
    }
}

@Composable
private fun UnexpectedCard(unexpected: RedemptionOutcome.UnexpectedResponse) {
    SectionCard(title = stringResource(R.string.unexpected_title), icon = R.drawable.ic_warning) {
        Text(text = stringResource(unexpected.reason.titleRes()), style = MaterialTheme.typography.bodyMedium)
        DetailRow(
            label = stringResource(R.string.label_http_status),
            value = unexpected.httpStatus.toString(),
        )
        if (unexpected.bodyPreview.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = unexpected.bodyPreview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/** Android plural rules take an Int; anything above Int.MAX_VALUE is simply "many". */
private fun Long.toPluralCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal const val TEST_TAG_VERDICT = "verdict"
internal const val TEST_TAG_GAME_ROW = "game_row"

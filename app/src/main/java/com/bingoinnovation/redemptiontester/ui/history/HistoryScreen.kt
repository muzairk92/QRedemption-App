package com.bingoinnovation.redemptiontester.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict
import com.bingoinnovation.redemptiontester.domain.ticket.TicketQr
import com.bingoinnovation.redemptiontester.ui.components.AppIcon
import com.bingoinnovation.redemptiontester.ui.text.titleRes
import com.bingoinnovation.redemptiontester.ui.theme.AppTheme
import com.bingoinnovation.redemptiontester.ui.util.DisplayFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenAttempt: (RedemptionAttempt) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.attempts.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            AppIcon(R.drawable.ic_delete, contentDescription = stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.attempts.isEmpty() -> EmptyHistory(Modifier.fillMaxSize().padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                items(state.attempts, key = { it.id }) { attempt ->
                    HistoryRow(attempt = attempt, onClick = { onOpenAttempt(attempt) })
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { AppIcon(R.drawable.ic_delete, contentDescription = null) },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.onClearHistory()
                    },
                ) { Text(stringResource(R.string.history_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryRow(attempt: RedemptionAttempt, onClick: () -> Unit) {
    val (icon, tint) = statusIcon(attempt)
    val verdict = attempt.verdict
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { AppIcon(icon, contentDescription = null, tint = tint) },
        overlineContent = { Text(stringResource(attempt.scenario.titleRes())) },
        headlineContent = { Text(text = headline(attempt), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.history_row_details,
                    TicketQr.parse(attempt.ticketQr)?.maskedToken.orEmpty(),
                    attempt.tabletId,
                    DisplayFormats.timestamp(attempt.startedAtMillis),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (verdict != null) {
            { VerdictChip(verdict) }
        } else {
            null
        },
    )
}

@Composable
private fun headline(attempt: RedemptionAttempt): String = when (val outcome = attempt.outcome) {
    null -> stringResource(R.string.history_unconfirmed)
    is RedemptionOutcome.Success -> stringResource(
        if (outcome.redemption.recovered) R.string.history_recovered else R.string.history_redeemed,
        outcome.redemption.ticketPackage.name,
    )
    is RedemptionOutcome.Rejected -> outcome.knownCode?.let { stringResource(it.titleRes()) } ?: outcome.code
    is RedemptionOutcome.UnexpectedResponse -> stringResource(R.string.history_unexpected, outcome.httpStatus)
    is RedemptionOutcome.TransportFailure -> stringResource(outcome.kind.titleRes())
}

@Composable
private fun statusIcon(attempt: RedemptionAttempt): Pair<Int, Color> {
    val status = AppTheme.statusColors
    return when (attempt.outcome) {
        null -> R.drawable.ic_schedule to status.warning
        is RedemptionOutcome.Success -> R.drawable.ic_check_circle to status.success
        is RedemptionOutcome.Rejected -> R.drawable.ic_error to MaterialTheme.colorScheme.error
        is RedemptionOutcome.UnexpectedResponse, is RedemptionOutcome.TransportFailure ->
            R.drawable.ic_warning to status.warning
    }
}

@Composable
private fun VerdictChip(verdict: Verdict) {
    val status = AppTheme.statusColors
    val (container, content) = when (verdict) {
        Verdict.AS_EXPECTED -> status.successContainer to status.onSuccessContainer
        Verdict.NOT_AS_EXPECTED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Verdict.NOT_CONFIRMED -> status.warningContainer to status.onWarningContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(
            text = stringResource(verdict.titleRes()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            R.drawable.ic_history,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

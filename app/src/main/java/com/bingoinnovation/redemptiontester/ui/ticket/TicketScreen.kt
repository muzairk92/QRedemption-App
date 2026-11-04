package com.bingoinnovation.redemptiontester.ui.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.domain.redemption.TabletIdRules
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.ui.components.AppIcon
import com.bingoinnovation.redemptiontester.ui.components.CopyButton
import com.bingoinnovation.redemptiontester.ui.components.DetailRow
import com.bingoinnovation.redemptiontester.ui.components.SectionCard
import com.bingoinnovation.redemptiontester.ui.components.StatusBanner
import com.bingoinnovation.redemptiontester.ui.components.StatusTone
import com.bingoinnovation.redemptiontester.ui.text.expectationRes
import com.bingoinnovation.redemptiontester.ui.text.titleRes
import com.bingoinnovation.redemptiontester.ui.ticket.TicketViewModel.Problem
import com.bingoinnovation.redemptiontester.ui.util.DisplayFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    onBack: () -> Unit,
    onScanNext: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var resultTop by remember { mutableIntStateOf(0) }
    var otherTabletDialog by rememberSaveable { mutableStateOf<String?>(null) }

    // Bring each new result into view once it has been laid out.
    LaunchedEffect(state.lastAttempt?.id) {
        if (state.lastAttempt != null) {
            withFrameNanos { }
            scrollState.animateScrollTo(resultTop)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ticket_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.ticket == null) {
                StatusBanner(
                    tone = StatusTone.ERROR,
                    icon = R.drawable.ic_error,
                    title = stringResource(R.string.ticket_invalid),
                )
            } else {
                TicketBody(
                    state = state,
                    viewModel = viewModel,
                    onResultPositioned = { resultTop = it },
                    onOtherTablet = { otherTabletDialog = viewModel.suggestedOtherTabletId() },
                    onScanNext = onScanNext,
                )
            }
        }
    }

    otherTabletDialog?.let { initial ->
        OtherTabletDialog(
            initial = initial,
            currentTabletId = state.tabletIdInput,
            onDismiss = { otherTabletDialog = null },
            onConfirm = { other -> if (viewModel.onOtherTablet(other)) otherTabletDialog = null },
        )
    }
}

/** Everything below the app bar for a valid ticket. */
@Composable
private fun ColumnScope.TicketBody(
    state: TicketViewModel.UiState,
    viewModel: TicketViewModel,
    onResultPositioned: (Int) -> Unit,
    onOtherTablet: () -> Unit,
    onScanNext: () -> Unit,
) {
    val ticket = checkNotNull(state.ticket)

    SectionCard(title = stringResource(R.string.section_ticket), icon = R.drawable.ic_confirmation_number) {
        DetailRow(label = stringResource(R.string.label_hall_site_id), value = ticket.siteId)
        DetailRow(
            label = stringResource(R.string.label_ticket_code),
            value = stringResource(R.string.ticket_code_masked, ticket.siteId, ticket.maskedToken),
            monospace = true,
        )
        DetailRow(
            label = stringResource(R.string.label_caller_id),
            value = state.callerId ?: stringResource(R.string.caller_not_set),
        )
        DetailRow(
            label = stringResource(R.string.label_server),
            value = state.serverBaseUrl?.let(DisplayFormats::host) ?: stringResource(R.string.server_not_set),
        )
    }

    RequestCard(state = state, viewModel = viewModel)

    if (state.callerId == null || state.serverBaseUrl == null) {
        StatusBanner(
            tone = StatusTone.WARNING,
            icon = R.drawable.ic_settings,
            title = stringResource(
                if (state.callerId == null) R.string.problem_no_caller else R.string.problem_no_server,
            ),
        )
    }
    state.problem?.let { problem ->
        StatusBanner(
            tone = StatusTone.ERROR,
            icon = R.drawable.ic_error,
            title = stringResource(problem.textRes()),
            action = {
                TextButton(onClick = viewModel::onProblemShown) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
    if (state.restoredUnconfirmed) {
        StatusBanner(
            tone = StatusTone.WARNING,
            icon = R.drawable.ic_schedule,
            title = stringResource(R.string.ticket_unconfirmed_title),
            body = stringResource(R.string.ticket_unconfirmed_body),
        )
    }

    if (!state.requestSent) {
        Button(
            onClick = viewModel::onRedeem,
            enabled = state.canRedeem,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(TEST_TAG_REDEEM),
        ) {
            AppIcon(R.drawable.ic_check_circle, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_redeem), style = MaterialTheme.typography.titleMedium)
        }
    }

    state.runningScenario?.let { RunningCard(it) }

    state.lastAttempt?.let { attempt ->
        if (!state.isRunning) {
            RedemptionResult(
                attempt = attempt,
                modifier = Modifier.onGloballyPositioned { onResultPositioned(it.positionInParent().y.toInt()) },
            )
        }
    }

    if (state.requestSent && !state.isRunning) {
        NextTestsCard(
            enabled = state.canRunFollowUpTests,
            onRetry = viewModel::onRetrySameRequest,
            onNewRequest = viewModel::onNewRequestSameTicket,
            onOtherTablet = onOtherTablet,
            onStartOver = viewModel::onNewRequestId,
            onScanNext = onScanNext,
        )
    }
}

@Composable
private fun RequestCard(state: TicketViewModel.UiState, viewModel: TicketViewModel) {
    SectionCard(title = stringResource(R.string.section_request), icon = R.drawable.ic_tablet_android) {
        val validation = state.tabletIdValidation
        OutlinedTextField(
            value = state.tabletIdInput,
            onValueChange = viewModel::onTabletIdChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TEST_TAG_TABLET_ID),
            enabled = !state.requestSent && !state.isRunning && !state.isLoading,
            label = { Text(stringResource(R.string.label_player_tablet_id)) },
            supportingText = { Text(tabletIdSupportingText(validation, state.requestSent)) },
            isError = validation != TabletIdRules.Validation.Valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
        )
        DetailRow(
            label = stringResource(R.string.label_request_id),
            value = state.requestId,
            monospace = true,
            trailing = {
                Row {
                    CopyButton(state.requestId, stringResource(R.string.label_request_id))
                    IconButton(onClick = viewModel::onNewRequestId, enabled = !state.isRunning) {
                        AppIcon(R.drawable.ic_autorenew, contentDescription = stringResource(R.string.action_new_request_id))
                    }
                }
            },
        )
        Text(
            text = stringResource(R.string.request_id_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun tabletIdSupportingText(validation: TabletIdRules.Validation, requestSent: Boolean): String = when (validation) {
    TabletIdRules.Validation.Valid -> stringResource(
        if (requestSent) R.string.tablet_id_locked else R.string.tablet_id_help,
    )
    TabletIdRules.Validation.Empty -> stringResource(R.string.tablet_id_empty)
    is TabletIdRules.Validation.TooLong -> stringResource(R.string.tablet_id_too_long, validation.bytes, TabletIdRules.MAX_BYTES)
    TabletIdRules.Validation.ControlCharacter -> stringResource(R.string.tablet_id_control)
    TabletIdRules.Validation.InvalidText -> stringResource(R.string.tablet_id_invalid_text)
}

@Composable
private fun RunningCard(scenario: TestScenario) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.running_title, stringResource(scenario.titleRes())),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.verdict_expected, stringResource(scenario.expectationRes())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NextTestsCard(
    enabled: Boolean,
    onRetry: () -> Unit,
    onNewRequest: () -> Unit,
    onOtherTablet: () -> Unit,
    onStartOver: () -> Unit,
    onScanNext: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.next_tests_title), icon = R.drawable.ic_replay) {
        TestActionRow(R.drawable.ic_replay, TestScenario.RETRY_SAME_REQUEST, enabled, onRetry, TEST_TAG_RETRY)
        TestActionRow(R.drawable.ic_qr_code_2, TestScenario.NEW_REQUEST_SAME_TICKET, enabled, onNewRequest, null)
        TestActionRow(R.drawable.ic_swap_horiz, TestScenario.OTHER_TABLET_SAME_REQUEST, enabled, onOtherTablet, null)
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onStartOver, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_start_over))
            }
            FilledTonalButton(onClick = onScanNext, modifier = Modifier.weight(1f)) {
                AppIcon(R.drawable.ic_qr_code_scanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_scan_next))
            }
        }
    }
}

@Composable
private fun TestActionRow(
    icon: Int,
    scenario: TestScenario,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String?,
) {
    ListItem(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(stringResource(scenario.titleRes())) },
        supportingContent = { Text(stringResource(R.string.verdict_expected, stringResource(scenario.expectationRes()))) },
        trailingContent = { AppIcon(R.drawable.ic_chevron_right, contentDescription = null) },
    )
}

@Composable
private fun OtherTabletDialog(
    initial: String,
    currentTabletId: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val sameAsCurrent = value == currentTabletId
    val valid = TabletIdRules.isValid(value) && !sameAsCurrent

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(R.drawable.ic_swap_horiz, contentDescription = null) },
        title = { Text(stringResource(R.string.other_tablet_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.other_tablet_body))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_other_tablet_id)) },
                    isError = !valid,
                    supportingText = if (sameAsCurrent) {
                        { Text(stringResource(R.string.other_tablet_same)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = valid) { Text(stringResource(R.string.action_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun Problem.textRes(): Int = when (this) {
    Problem.NO_CALLER -> R.string.problem_no_caller
    Problem.NO_SERVER -> R.string.problem_no_server
    Problem.STORAGE_FAILED -> R.string.error_storage
}

internal const val TEST_TAG_REDEEM = "redeem"
internal const val TEST_TAG_RETRY = "retry_same_request"
internal const val TEST_TAG_TABLET_ID = "tablet_id"

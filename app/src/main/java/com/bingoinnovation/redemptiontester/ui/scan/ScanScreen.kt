package com.bingoinnovation.redemptiontester.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.ui.components.AppIcon
import com.bingoinnovation.redemptiontester.ui.components.CameraPermissionGate
import com.bingoinnovation.redemptiontester.ui.components.InfoPill
import com.bingoinnovation.redemptiontester.ui.components.QrCameraPreview
import com.bingoinnovation.redemptiontester.ui.components.ScannerOverlay
import com.bingoinnovation.redemptiontester.ui.components.StatusBanner
import com.bingoinnovation.redemptiontester.ui.components.StatusTone
import com.bingoinnovation.redemptiontester.ui.util.DisplayFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onTicketScanned: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.ticketToOpen) {
        state.ticketToOpen?.let { ticket ->
            onTicketScanned(ticket)
            viewModel.onTicketOpened()
        }
    }
    val messageText = state.message?.let { stringResource(it.textRes()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        BadgedBox(
                            badge = {
                                if (state.unconfirmedCount > 0) Badge { Text(state.unconfirmedCount.toString()) }
                            },
                        ) {
                            AppIcon(R.drawable.ic_history, contentDescription = stringResource(R.string.history_title))
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        AppIcon(R.drawable.ic_settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPermissionGate(modifier = Modifier.fillMaxSize().padding(padding)) {
                QrCameraPreview(
                    onCodeScanned = viewModel::onCodeScanned,
                    onProblem = viewModel::onScannerProblem,
                    scanningEnabled = state.scanningEnabled,
                    torchEnabled = state.torchOn,
                    modifier = Modifier.fillMaxSize(),
                )
                ScannerOverlay(modifier = Modifier.fillMaxSize(), centerYFraction = 0.38f)
            }
            BottomPanel(
                state = state,
                onTorchToggle = viewModel::onTorchToggle,
                onTypeCode = { showManualEntry = true },
                onOpenHistory = onOpenHistory,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showManualEntry) {
        ManualTicketDialog(
            onDismiss = { showManualEntry = false },
            onSubmit = { code -> viewModel.onManualCode(code).also { opened -> if (opened) showManualEntry = false } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomPanel(
    state: ScanViewModel.UiState,
    onTorchToggle: (Boolean) -> Unit,
    onTypeCode: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.scan_hint), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoPill(icon = R.drawable.ic_key, text = state.callerId ?: stringResource(R.string.caller_not_set))
                InfoPill(
                    icon = R.drawable.ic_dns,
                    text = state.serverBaseUrl?.let(DisplayFormats::host) ?: stringResource(R.string.server_not_set),
                )
                state.tabletId?.let { InfoPill(icon = R.drawable.ic_tablet_android, text = it) }
            }
            if (state.unconfirmedCount > 0) {
                StatusBanner(
                    tone = StatusTone.WARNING,
                    icon = R.drawable.ic_schedule,
                    title = pluralStringResource(
                        R.plurals.scan_unconfirmed_title,
                        state.unconfirmedCount,
                        state.unconfirmedCount,
                    ),
                    body = stringResource(R.string.scan_unconfirmed_body),
                    action = {
                        TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.action_open_history)) }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconToggleButton(checked = state.torchOn, onCheckedChange = onTorchToggle) {
                    AppIcon(
                        if (state.torchOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off,
                        contentDescription = stringResource(
                            if (state.torchOn) R.string.action_torch_off else R.string.action_torch_on,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onTypeCode) {
                    AppIcon(R.drawable.ic_keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_type_code))
                }
            }
        }
    }
}

@Composable
private fun ManualTicketDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(R.drawable.ic_confirmation_number, contentDescription = null) },
        title = { Text(stringResource(R.string.manual_ticket_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.manual_ticket_body))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        invalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_ticket_label)) },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.manual_ticket_invalid)) }
                    } else {
                        null
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!onSubmit(code)) invalid = true }) {
                Text(stringResource(R.string.action_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun ScanViewModel.Message.textRes(): Int = when (this) {
    ScanViewModel.Message.NOT_A_TICKET -> R.string.scan_message_not_ticket
    ScanViewModel.Message.CALLER_LABEL_SCANNED -> R.string.scan_message_caller_label
    ScanViewModel.Message.SCANNER_NOT_READY -> R.string.scanner_not_ready
}

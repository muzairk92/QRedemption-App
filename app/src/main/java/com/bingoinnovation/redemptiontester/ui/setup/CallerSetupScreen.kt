package com.bingoinnovation.redemptiontester.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.ui.components.AppIcon
import com.bingoinnovation.redemptiontester.ui.components.CameraPermissionGate
import com.bingoinnovation.redemptiontester.ui.components.DetailRow
import com.bingoinnovation.redemptiontester.ui.components.QrCameraPreview
import com.bingoinnovation.redemptiontester.ui.components.ScannerOverlay
import com.bingoinnovation.redemptiontester.ui.components.SectionCard
import com.bingoinnovation.redemptiontester.ui.components.StatusBanner
import com.bingoinnovation.redemptiontester.ui.components.StatusTone
import com.bingoinnovation.redemptiontester.ui.setup.CallerSetupViewModel.Mode
import com.bingoinnovation.redemptiontester.ui.setup.CallerSetupViewModel.ScanFeedback
import com.bingoinnovation.redemptiontester.ui.shared.Verification
import com.bingoinnovation.redemptiontester.ui.shared.VerificationResult
import com.bingoinnovation.redemptiontester.ui.shared.VerifyButtonLabel
import com.bingoinnovation.redemptiontester.ui.util.DisplayFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallerSetupScreen(
    viewModel: CallerSetupViewModel,
    onBack: (() -> Unit)?,
    onSaved: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            AppIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.mode) {
                Mode.SCAN -> ScanContent(state, viewModel)
                Mode.MANUAL -> ManualContent(state, viewModel)
                Mode.REVIEW -> ReviewContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun ColumnScope.ScanContent(state: CallerSetupViewModel.UiState, viewModel: CallerSetupViewModel) {
    Heading(
        title = stringResource(R.string.setup_scan_heading),
        body = stringResource(R.string.setup_scan_body),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(28.dp),
        color = Color.Black,
        contentColor = Color.White,
    ) {
        CameraPermissionGate(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                QrCameraPreview(
                    onCodeScanned = viewModel::onCodeScanned,
                    onProblem = viewModel::onScannerProblem,
                    modifier = Modifier.fillMaxSize(),
                )
                ScannerOverlay(modifier = Modifier.fillMaxSize(), frameFraction = 0.78f)
            }
        }
    }
    state.feedback?.let { feedback ->
        StatusBanner(
            tone = if (feedback == ScanFeedback.SCANNER_NOT_READY) StatusTone.INFO else StatusTone.WARNING,
            icon = if (feedback == ScanFeedback.SCANNER_NOT_READY) R.drawable.ic_schedule else R.drawable.ic_warning,
            title = stringResource(feedback.messageRes()),
        )
    }
    TextButton(onClick = viewModel::onEnterManually, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        AppIcon(R.drawable.ic_keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.setup_enter_manually))
    }
}

@Composable
private fun ColumnScope.ManualContent(state: CallerSetupViewModel.UiState, viewModel: CallerSetupViewModel) {
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    Heading(
        title = stringResource(R.string.setup_manual_heading),
        body = stringResource(R.string.setup_manual_body),
    )
    OutlinedTextField(
        value = state.callerIdInput,
        onValueChange = viewModel::onCallerIdChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.label_caller_id)) },
        supportingText = {
            Text(stringResource(if (state.callerIdError) R.string.setup_caller_id_error else R.string.setup_caller_id_hint))
        },
        isError = state.callerIdError,
        singleLine = true,
        leadingIcon = { AppIcon(R.drawable.ic_key, contentDescription = null) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        ),
    )
    OutlinedTextField(
        value = state.apiKeyInput,
        onValueChange = viewModel::onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.label_api_key)) },
        supportingText = {
            Text(stringResource(if (state.apiKeyError) R.string.setup_api_key_error else R.string.setup_api_key_hint))
        },
        isError = state.apiKeyError,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { keyVisible = !keyVisible }) {
                AppIcon(
                    if (keyVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    contentDescription = stringResource(
                        if (keyVisible) R.string.action_hide_api_key else R.string.action_show_api_key,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.onSubmitManual() }),
    )
    Button(onClick = viewModel::onSubmitManual, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_continue))
    }
    TextButton(onClick = viewModel::onScanAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.setup_scan_instead))
    }
}

@Composable
private fun ColumnScope.ReviewContent(state: CallerSetupViewModel.UiState, viewModel: CallerSetupViewModel) {
    val candidate = state.candidate ?: return

    Heading(
        title = stringResource(R.string.setup_review_heading),
        body = stringResource(R.string.setup_review_body),
    )
    SectionCard(title = stringResource(R.string.section_caller), icon = R.drawable.ic_key) {
        DetailRow(label = stringResource(R.string.label_caller_id), value = candidate.callerId, monospace = true)
        DetailRow(label = stringResource(R.string.label_api_key), value = candidate.maskedApiKey, monospace = true)
        DetailRow(
            label = stringResource(R.string.label_server),
            value = state.serverBaseUrl?.let(DisplayFormats::host) ?: stringResource(R.string.server_not_set),
        )
    }
    OutlinedButton(
        onClick = viewModel::onVerify,
        enabled = state.verification != Verification.Checking,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppIcon(R.drawable.ic_verified, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        VerifyButtonLabel(state.verification)
    }
    VerificationResult(state.verification)
    if (state.saveFailed) {
        StatusBanner(tone = StatusTone.ERROR, icon = R.drawable.ic_error, title = stringResource(R.string.error_storage))
    }
    Button(onClick = viewModel::onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_save))
    }
    TextButton(onClick = viewModel::onScanAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.setup_scan_different))
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ScanFeedback.messageRes(): Int = when (this) {
    ScanFeedback.TICKET_SCANNED -> R.string.setup_feedback_ticket
    ScanFeedback.NOT_A_CALLER_LABEL -> R.string.setup_feedback_not_caller
    ScanFeedback.INVALID_CALLER_ID -> R.string.setup_feedback_invalid_caller_id
    ScanFeedback.INVALID_API_KEY -> R.string.setup_feedback_invalid_api_key
    ScanFeedback.SCANNER_NOT_READY -> R.string.scanner_not_ready
}

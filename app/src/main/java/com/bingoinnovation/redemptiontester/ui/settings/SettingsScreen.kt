package com.bingoinnovation.redemptiontester.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.domain.redemption.TabletIdRules
import com.bingoinnovation.redemptiontester.domain.server.BaseUrlRules
import com.bingoinnovation.redemptiontester.domain.server.PortalServers
import com.bingoinnovation.redemptiontester.domain.server.ServerPreset
import com.bingoinnovation.redemptiontester.ui.components.AppIcon
import com.bingoinnovation.redemptiontester.ui.components.DetailRow
import com.bingoinnovation.redemptiontester.ui.components.SectionCard
import com.bingoinnovation.redemptiontester.ui.components.StatusBanner
import com.bingoinnovation.redemptiontester.ui.components.StatusTone
import com.bingoinnovation.redemptiontester.ui.shared.Verification
import com.bingoinnovation.redemptiontester.ui.shared.VerificationResult
import com.bingoinnovation.redemptiontester.ui.shared.VerifyButtonLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onReplaceCaller: () -> Unit,
    onCallerRemoved: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.callerRemoved) {
        if (state.callerRemoved) onCallerRemoved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.storageFailed) {
                StatusBanner(
                    tone = StatusTone.ERROR,
                    icon = R.drawable.ic_error,
                    title = stringResource(R.string.error_storage),
                    action = {
                        TextButton(onClick = viewModel::onStorageErrorShown) {
                            Text(stringResource(R.string.action_dismiss))
                        }
                    },
                )
            }
            ServerSection(state, viewModel)
            TabletSection(state, viewModel)
            CallerSection(
                state = state,
                onVerify = viewModel::onVerifyCaller,
                onReplace = onReplaceCaller,
                onRemove = { confirmRemove = true },
            )
            SectionCard(title = stringResource(R.string.section_about), icon = R.drawable.ic_info) {
                DetailRow(label = stringResource(R.string.label_version), value = state.versionName)
                Text(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { AppIcon(R.drawable.ic_key, contentDescription = null) },
            title = { Text(stringResource(R.string.remove_caller_title)) },
            text = { Text(stringResource(R.string.remove_caller_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        viewModel.onRemoveCaller()
                    },
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSection(state: SettingsViewModel.UiState, viewModel: SettingsViewModel) {
    SectionCard(title = stringResource(R.string.section_server), icon = R.drawable.ic_dns) {
        val presets = ServerPreset.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            presets.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = state.selectedPreset == preset,
                    onClick = { viewModel.onPresetSelected(preset) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = presets.size),
                    label = {
                        Text(
                            stringResource(
                                when (preset) {
                                    ServerPreset.STAGING -> R.string.server_staging
                                    ServerPreset.CUSTOM -> R.string.server_custom
                                },
                            ),
                        )
                    },
                )
            }
        }
        when (state.selectedPreset) {
            ServerPreset.STAGING -> DetailRow(
                label = stringResource(R.string.label_address),
                value = PortalServers.STAGING_BASE_URL,
                monospace = true,
            )
            ServerPreset.CUSTOM -> {
                OutlinedTextField(
                    value = state.customUrlInput,
                    onValueChange = viewModel::onCustomUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_portal_address)) },
                    placeholder = { Text(stringResource(R.string.custom_url_placeholder)) },
                    supportingText = {
                        Text(
                            state.customUrlError?.let { stringResource(it.messageRes()) }
                                ?: stringResource(
                                    if (state.allowCleartext) R.string.custom_url_hint_debug else R.string.custom_url_hint,
                                ),
                        )
                    },
                    isError = state.customUrlError != null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.onSaveCustomUrl() }),
                )
                Button(onClick = viewModel::onSaveCustomUrl) { Text(stringResource(R.string.action_save_address)) }
            }
        }
        Text(
            text = state.savedServer.baseUrl?.let { stringResource(R.string.server_active, it) }
                ?: stringResource(R.string.server_active_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TabletSection(state: SettingsViewModel.UiState, viewModel: SettingsViewModel) {
    SectionCard(title = stringResource(R.string.section_tablet), icon = R.drawable.ic_tablet_android) {
        val validation = state.tabletIdValidation
        OutlinedTextField(
            value = state.tabletIdInput,
            onValueChange = viewModel::onTabletIdChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_player_tablet_id)) },
            supportingText = {
                Text(
                    when (validation) {
                        TabletIdRules.Validation.Valid -> stringResource(R.string.tablet_id_help)
                        TabletIdRules.Validation.Empty -> stringResource(R.string.tablet_id_empty)
                        is TabletIdRules.Validation.TooLong ->
                            stringResource(R.string.tablet_id_too_long, validation.bytes, TabletIdRules.MAX_BYTES)
                        TabletIdRules.Validation.ControlCharacter -> stringResource(R.string.tablet_id_control)
                        TabletIdRules.Validation.InvalidText -> stringResource(R.string.tablet_id_invalid_text)
                    },
                )
            },
            isError = validation != TabletIdRules.Validation.Valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::onGenerateTabletId) {
                AppIcon(R.drawable.ic_autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_generate))
            }
            Button(onClick = viewModel::onSaveTabletId, enabled = state.canSaveTabletId) {
                Text(stringResource(R.string.action_save))
            }
        }
        Text(
            text = stringResource(R.string.tablet_section_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CallerSection(
    state: SettingsViewModel.UiState,
    onVerify: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_caller), icon = R.drawable.ic_key) {
        val caller = state.caller
        if (caller == null) {
            Text(stringResource(R.string.caller_not_set), style = MaterialTheme.typography.bodyMedium)
        } else {
            DetailRow(label = stringResource(R.string.label_caller_id), value = caller.callerId, monospace = true)
            DetailRow(label = stringResource(R.string.label_api_key), value = caller.maskedApiKey, monospace = true)
            OutlinedButton(
                onClick = onVerify,
                enabled = state.verification != Verification.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIcon(R.drawable.ic_verified, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                VerifyButtonLabel(state.verification)
            }
            VerificationResult(state.verification)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReplace) {
                AppIcon(R.drawable.ic_qr_code_scanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_replace_caller))
            }
            if (caller != null) {
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_remove)) }
            }
        }
    }
}

private fun BaseUrlRules.Result.messageRes(): Int = when (this) {
    is BaseUrlRules.Result.Valid -> R.string.custom_url_hint
    BaseUrlRules.Result.Empty -> R.string.url_error_empty
    BaseUrlRules.Result.Malformed -> R.string.url_error_malformed
    BaseUrlRules.Result.MissingScheme -> R.string.url_error_scheme
    BaseUrlRules.Result.CleartextNotAllowed -> R.string.url_error_cleartext
    BaseUrlRules.Result.ContainsCredentials -> R.string.url_error_credentials
    BaseUrlRules.Result.ContainsQueryOrFragment -> R.string.url_error_query
}

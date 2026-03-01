package eu.darken.octi.main.ui.settings.support

import android.text.format.Formatter
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem
import eu.darken.octi.common.settings.SettingsCategoryHeader
import eu.darken.octi.common.R as CommonR

@Composable
fun SupportScreenHost(vm: SupportVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.launchRecorderEvent.collect { context.startActivity(it) }
    }

    val state by waitForState(vm.state)
    state?.let {
        SupportScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDocumentation = { vm.openUrl("https://github.com/d4rken-org/octi/wiki") },
            onIssueTracker = { vm.openUrl("https://github.com/d4rken-org/octi") },
            onDiscord = { vm.openUrl("https://discord.gg/s7V4C6zuVy") },
            onStartDebugLog = { vm.startDebugLog() },
            onStopDebugLog = { vm.stopDebugLog() },
            onOpenPrivacyPolicy = { vm.openUrl(PrivacyPolicy.URL) },
            onContactSupport = { vm.navigateToContactSupport() },
            onDeleteAllLogs = { vm.deleteAllLogs() },
            onDismissShortRecordingWarning = { vm.dismissShortRecordingWarning() },
            onForceStopDebugLog = { vm.forceStopDebugLog() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    state: SupportVM.State,
    onNavigateUp: () -> Unit,
    onDocumentation: () -> Unit,
    onIssueTracker: () -> Unit,
    onDiscord: () -> Unit,
    onStartDebugLog: () -> Unit,
    onStopDebugLog: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onContactSupport: () -> Unit,
    onDeleteAllLogs: () -> Unit,
    onDismissShortRecordingWarning: () -> Unit,
    onForceStopDebugLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDebugLogConsent by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_support_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.support_contact_label),
                    subtitle = stringResource(R.string.support_contact_footer),
                    icon = Icons.TwoTone.Email,
                    onClick = onContactSupport,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.documentation_label),
                    iconPainter = painterResource(R.drawable.ic_card_text_onsurface),
                    onClick = onDocumentation,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.issue_tracker_label),
                    subtitle = stringResource(R.string.issue_tracker_description),
                    iconPainter = painterResource(R.drawable.ic_github_onsurface),
                    onClick = onIssueTracker,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.discord_label),
                    subtitle = stringResource(R.string.discord_description),
                    iconPainter = painterResource(R.drawable.ic_discord_onsurface),
                    onClick = onDiscord,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            item {
                val debugLogSubtitle = when {
                    state.isRecording -> state.currentLogPath?.path
                    state.sessionCount > 0 -> stringResource(
                        R.string.support_stored_logs_info,
                        state.sessionCount,
                        Formatter.formatShortFileSize(context, state.totalLogSize),
                    )
                    else -> stringResource(R.string.support_debuglog_desc)
                }

                SettingsBaseItem(
                    title = if (state.isRecording) {
                        stringResource(R.string.support_debuglog_inprogress_label)
                    } else {
                        stringResource(R.string.support_debuglog_label)
                    },
                    subtitle = debugLogSubtitle,
                    icon = if (state.isRecording) {
                        Icons.TwoTone.Cancel
                    } else {
                        Icons.TwoTone.BugReport
                    },
                    onClick = {
                        if (state.isRecording) {
                            onStopDebugLog()
                        } else {
                            showDebugLogConsent = true
                        }
                    },
                )
            }
            if (state.sessionCount > 0 && !state.isRecording) {
                item {
                    SettingsBaseItem(
                        title = stringResource(R.string.support_delete_all_logs_action),
                        icon = Icons.TwoTone.Delete,
                        onClick = { showDeleteAllConfirm = true },
                    )
                }
            }
        }
    }

    if (showDebugLogConsent) {
        AlertDialog(
            onDismissRequest = { showDebugLogConsent = false },
            title = { Text(text = stringResource(R.string.support_debuglog_label)) },
            text = { Text(text = stringResource(R.string.settings_debuglog_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showDebugLogConsent = false
                    onStartDebugLog()
                }) {
                    Text(text = stringResource(CommonR.string.general_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDebugLogConsent = false }) {
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(text = stringResource(R.string.support_delete_all_logs_action)) },
            text = { Text(text = stringResource(R.string.support_delete_all_logs_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllConfirm = false
                    onDeleteAllLogs()
                }) {
                    Text(text = stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    if (state.showShortRecordingWarning) {
        AlertDialog(
            onDismissRequest = onDismissShortRecordingWarning,
            title = { Text(text = stringResource(R.string.debug_debuglog_short_recording_title)) },
            text = { Text(text = stringResource(R.string.debug_debuglog_short_recording_message)) },
            confirmButton = {
                TextButton(onClick = onDismissShortRecordingWarning) {
                    Text(text = stringResource(R.string.debug_debuglog_short_recording_continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onForceStopDebugLog) {
                    Text(text = stringResource(R.string.debug_debuglog_short_recording_stop_action))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun SupportScreenPreview() = PreviewWrapper {
    SupportScreen(
        state = SupportVM.State(isRecording = false, currentLogPath = null),
        onNavigateUp = {},
        onDocumentation = {},
        onIssueTracker = {},
        onDiscord = {},
        onStartDebugLog = {},
        onStopDebugLog = {},
        onOpenPrivacyPolicy = {},
        onContactSupport = {},
        onDeleteAllLogs = {},
        onDismissShortRecordingWarning = {},
        onForceStopDebugLog = {},
    )
}

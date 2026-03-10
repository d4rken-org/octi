package eu.darken.octi.main.ui.settings.support

import android.text.format.Formatter
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.automirrored.twotone.ContactSupport
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.debug.recording.core.LogSession
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem
import eu.darken.octi.common.settings.SettingsCategoryHeader
import eu.darken.octi.common.R as CommonR
import kotlinx.coroutines.launch

@Composable
fun SupportScreenHost(vm: SupportVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.launchRecorderEvent.collect { context.startActivity(it) }
    }

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        SupportScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDocumentation = { vm.openUrl("https://github.com/d4rken-org/octi/wiki") },
            onIssueTracker = { vm.openUrl("https://github.com/d4rken-org/octi") },
            onDiscord = { vm.openUrl("https://discord.gg/s7V4C6zuVy") },
            onStartDebugLog = { vm.startDebugLog() },
            onStopDebugLog = { vm.stopDebugLog() },
            onContactDeveloper = { vm.navigateToContactSupport() },
            onDeleteAllLogs = { vm.deleteAllLogs() },
            onDeleteSession = { vm.deleteSession(it) },
            onOpenSession = { vm.openSession(it) },
            onDismissShortRecordingWarning = { vm.dismissShortRecordingWarning() },
            onForceStopDebugLog = { vm.forceStopDebugLog() },
            onOpenPrivacyPolicy = { vm.openPrivacyPolicy() },
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
    onContactDeveloper: () -> Unit,
    onDeleteAllLogs: () -> Unit,
    onDeleteSession: (LogSession) -> Unit,
    onOpenSession: (LogSession) -> Unit,
    onDismissShortRecordingWarning: () -> Unit,
    onForceStopDebugLog: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebugLogConsent by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }

    val folderEmptyMessage = stringResource(R.string.support_debuglog_folder_empty_desc)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // 1. Documentation
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.documentation_label),
                    iconPainter = painterResource(R.drawable.ic_card_text_onsurface),
                    onClick = onDocumentation,
                )
            }
            // 2. Issue tracker
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.issue_tracker_label),
                    subtitle = stringResource(R.string.issue_tracker_description),
                    iconPainter = painterResource(R.drawable.ic_github_onsurface),
                    onClick = onIssueTracker,
                )
            }
            // 3. Discord
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.discord_label),
                    subtitle = stringResource(R.string.discord_description),
                    iconPainter = painterResource(R.drawable.ic_discord_onsurface),
                    onClick = onDiscord,
                )
            }
            // 4. Contact developer
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.support_contact_label),
                    subtitle = stringResource(R.string.support_contact_desc),
                    icon = Icons.AutoMirrored.TwoTone.ContactSupport,
                    onClick = onContactDeveloper,
                )
            }
            // Category: Other
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            // 5. Debug log — always shows description
            item {
                SettingsBaseItem(
                    title = if (state.isRecording) {
                        stringResource(R.string.support_debuglog_inprogress_label)
                    } else {
                        stringResource(R.string.support_debuglog_label)
                    },
                    subtitle = stringResource(R.string.support_debuglog_desc),
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
            // 6. Debug log folder — always visible
            item {
                val folderSubtitle = if (state.sessionCount > 0) {
                    pluralStringResource(
                        R.plurals.support_debuglog_folder_desc,
                        state.sessionCount,
                        state.sessionCount,
                        Formatter.formatShortFileSize(context, state.totalLogSize),
                    )
                } else {
                    stringResource(R.string.support_debuglog_folder_empty_desc)
                }

                SettingsBaseItem(
                    title = stringResource(R.string.support_debuglog_folder_label),
                    subtitle = folderSubtitle,
                    icon = Icons.TwoTone.Folder,
                    onClick = {
                        if (state.sessionCount > 0 || state.isRecording) {
                            showSessionsSheet = true
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(folderEmptyMessage) }
                        }
                    },
                )
            }
        }
    }

    if (showDebugLogConsent) {
        RecordingConsentDialog(
            onConfirm = {
                showDebugLogConsent = false
                onStartDebugLog()
            },
            onDismiss = { showDebugLogConsent = false },
            onPrivacyPolicy = onOpenPrivacyPolicy,
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(text = stringResource(R.string.support_debuglog_folder_delete_confirmation_title)) },
            text = { Text(text = stringResource(R.string.support_debuglog_folder_delete_confirmation_message)) },
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

    if (showSessionsSheet) {
        // Auto-dismiss when list becomes empty
        if (state.debugSessions.isEmpty()) {
            LaunchedEffect(Unit) { showSessionsSheet = false }
        } else {
            DebugSessionsSheet(
                sessions = state.debugSessions,
                onDismiss = { showSessionsSheet = false },
                onDeleteSession = onDeleteSession,
                onOpenSession = { session ->
                    showSessionsSheet = false
                    onOpenSession(session)
                },
                onStopRecording = onStopDebugLog,
                onDeleteAll = {
                    showSessionsSheet = false
                    showDeleteAllConfirm = true
                },
            )
        }
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
        onContactDeveloper = {},
        onDeleteAllLogs = {},
        onDeleteSession = {},
        onOpenSession = {},
        onDismissShortRecordingWarning = {},
        onForceStopDebugLog = {},
        onOpenPrivacyPolicy = {},
    )
}

package eu.darken.octi.main.ui.settings.support

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material.icons.twotone.FiberManualRecord
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.debug.recording.core.LogSession
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.R as CommonR

@Composable
fun ContactSupportScreenHost(vm: ContactSupportVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        ContactSupportScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onCategorySelected = { category -> vm.setCategory(category) },
            onDescriptionChanged = { text -> vm.setDescription(text) },
            onExpectedBehaviorChanged = { text -> vm.setExpectedBehavior(text) },
            onShowRecordingConsent = { vm.showRecordingConsent() },
            onDismissRecordingConsent = { vm.dismissRecordingConsent() },
            onOpenPrivacyPolicy = { vm.openPrivacyPolicy() },
            onStartRecording = { vm.startRecording() },
            onStopRecording = { vm.stopRecording() },
            onDismissShortRecordingWarning = { vm.dismissShortRecordingWarning() },
            onForceStopRecording = { vm.forceStopRecording() },
            onSelectSession = { session -> vm.selectSession(session) },
            onDeleteSession = { session -> vm.deleteSession(session) },
            onSendEmail = { vm.sendEmail() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactSupportScreen(
    state: ContactSupportVM.State,
    onNavigateUp: () -> Unit,
    onCategorySelected: (ContactSupportVM.Category) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onExpectedBehaviorChanged: (String) -> Unit,
    onShowRecordingConsent: () -> Unit,
    onDismissRecordingConsent: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDismissShortRecordingWarning: () -> Unit,
    onForceStopRecording: () -> Unit,
    onSelectSession: (LogSession) -> Unit,
    onDeleteSession: (LogSession) -> Unit,
    onSendEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.support_contact_label)) },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Category card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.TwoTone.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.support_contact_category_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.category == ContactSupportVM.Category.QUESTION,
                            onClick = { onCategorySelected(ContactSupportVM.Category.QUESTION) },
                            label = { Text(stringResource(R.string.support_contact_category_question_label)) },
                        )
                        FilterChip(
                            selected = state.category == ContactSupportVM.Category.FEATURE_REQUEST,
                            onClick = { onCategorySelected(ContactSupportVM.Category.FEATURE_REQUEST) },
                            label = { Text(stringResource(R.string.support_contact_category_feature_label)) },
                        )
                        FilterChip(
                            selected = state.category == ContactSupportVM.Category.BUG_REPORT,
                            onClick = { onCategorySelected(ContactSupportVM.Category.BUG_REPORT) },
                            label = { Text(stringResource(R.string.support_contact_category_bug_label)) },
                        )
                    }
                }
            }

            // Debug log card (only for BUG_REPORT)
            if (state.category == ContactSupportVM.Category.BUG_REPORT) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.TwoTone.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.support_contact_debuglog_label),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.support_contact_debuglog_picker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.sessions.isEmpty() && !state.isRecording) {
                            Text(
                                text = stringResource(R.string.support_contact_debuglog_picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }

                        state.sessions.forEach { sessionInfo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.selectedSession?.sessionDir == sessionInfo.session.sessionDir,
                                    onClick = { onSelectSession(sessionInfo.session) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = DateUtils.getRelativeTimeSpanString(
                                            sessionInfo.lastModified,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS,
                                        ).toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = Formatter.formatShortFileSize(context, sessionInfo.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onDeleteSession(sessionInfo.session) }) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (state.isRecording) {
                                TextButton(onClick = onStopRecording) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.debug_debuglog_stop_action))
                                }
                            } else {
                                TextButton(onClick = onShowRecordingConsent) {
                                    Icon(
                                        imageVector = Icons.TwoTone.FiberManualRecord,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.debug_debuglog_record_action))
                                }
                            }
                        }
                    }
                }
            }

            // Description card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChanged,
                        label = { Text(stringResource(R.string.support_contact_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        supportingText = {
                            val hintText = when (state.category) {
                                ContactSupportVM.Category.QUESTION ->
                                    stringResource(R.string.support_contact_description_question_hint)
                                ContactSupportVM.Category.FEATURE_REQUEST ->
                                    stringResource(R.string.support_contact_description_feature_hint)
                                ContactSupportVM.Category.BUG_REPORT ->
                                    stringResource(R.string.support_contact_description_bug_hint)
                            }
                            Column {
                                Text(
                                    text = hintText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.support_contact_word_count,
                                        state.descriptionWordCount,
                                        state.descriptionWordCount,
                                        20,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.descriptionWordCount >= 20) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        },
                    )
                }
            }

            // Expected behavior card (only for BUG_REPORT)
            if (state.category == ContactSupportVM.Category.BUG_REPORT) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = state.expectedBehavior,
                            onValueChange = onExpectedBehaviorChanged,
                            label = { Text(stringResource(R.string.support_contact_expected_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            supportingText = {
                                Column {
                                    Text(
                                        text = stringResource(R.string.support_contact_expected_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.support_contact_word_count,
                                            state.expectedBehaviorWordCount,
                                            state.expectedBehaviorWordCount,
                                            10,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (state.expectedBehaviorWordCount >= 10) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // Welcome/info card
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Info,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.support_contact_welcome),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Send button
            Button(
                onClick = onSendEmail,
                enabled = state.isSendEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Email,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.support_contact_send_action))
            }

            // Footer
            Text(
                text = stringResource(R.string.support_contact_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Recording consent dialog
    if (state.showRecordingConsent) {
        RecordingConsentDialog(
            onConfirm = onStartRecording,
            onDismiss = onDismissRecordingConsent,
            onPrivacyPolicy = onOpenPrivacyPolicy,
        )
    }

    // Short recording warning dialog
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
                TextButton(onClick = onForceStopRecording) {
                    Text(text = stringResource(R.string.debug_debuglog_short_recording_stop_action))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun ContactSupportScreenPreview() = PreviewWrapper {
    ContactSupportScreen(
        state = ContactSupportVM.State(),
        onNavigateUp = {},
        onCategorySelected = {},
        onDescriptionChanged = {},
        onExpectedBehaviorChanged = {},
        onShowRecordingConsent = {},
        onDismissRecordingConsent = {},
        onOpenPrivacyPolicy = {},
        onStartRecording = {},
        onStopRecording = {},
        onDismissShortRecordingWarning = {},
        onForceStopRecording = {},
        onSelectSession = {},
        onDeleteSession = {},
        onSendEmail = {},
    )
}

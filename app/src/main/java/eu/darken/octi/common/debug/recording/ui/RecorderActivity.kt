package eu.darken.octi.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.uix.Activity2

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private val vm: RecorderActivityVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeState by vm.themeState.collectAsState()
            OctiTheme(state = themeState) {
                LaunchedEffect(Unit) {
                    vm.shareEvent.collect { startActivity(it) }
                }
                LaunchedEffect(Unit) {
                    vm.finishEvent.collect { finish() }
                }

                val state by waitForState(vm.state)
                state?.let {
                    RecorderScreen(
                        state = it,
                        onShare = { vm.share() },
                        onKeep = { vm.keep() },
                        onDiscard = { vm.discard() },
                        onPrivacyPolicy = { vm.goPrivacyPolicy() },
                    )
                }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val SESSION_DIR = "sessionDir"

        fun getLaunchIntent(context: Context, sessionDirPath: String): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(SESSION_DIR, sessionDirPath)
            return intent
        }
    }
}

@Composable
fun RecorderScreen(
    state: RecorderActivityVM.State,
    onShare: () -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val buttonsEnabled = !state.loading && !state.actionInProgress

    OutlinedCard(
        modifier = Modifier.padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.debug_debuglog_recorded_file_label),
                style = MaterialTheme.typography.labelMedium,
            )

            if (state.sessionName != null) {
                Text(
                    text = state.sessionName,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (state.sessionPath != null) {
                Text(
                    text = state.sessionPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.debug_log_files_count_label, state.fileCount),
                    style = MaterialTheme.typography.labelMedium,
                )

                Spacer(modifier = Modifier.height(4.dp))

                state.files.forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Formatter.formatShortFileSize(context, file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index < state.files.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_debuglog_size_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (state.totalUncompressedSize != -1L) {
                        Text(
                            text = Formatter.formatShortFileSize(context, state.totalUncompressedSize),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_debuglog_size_compressed_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (state.compressedSize != -1L) {
                        Text(
                            text = Formatter.formatShortFileSize(context, state.compressedSize),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.debug_debuglog_sensitive_information_message),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_privacy_policy_label),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onPrivacyPolicy),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDiscard,
                    enabled = buttonsEnabled,
                ) {
                    Text(text = stringResource(R.string.debug_log_discard_action))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (state.actionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else {
                    OutlinedButton(
                        onClick = onKeep,
                        enabled = buttonsEnabled,
                    ) {
                        Text(text = stringResource(R.string.debug_log_keep_action))
                    }

                    Button(
                        onClick = onShare,
                        enabled = buttonsEnabled,
                    ) {
                        Text(text = stringResource(CommonR.string.general_share_action))
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun RecorderScreenLoadingPreview() = PreviewWrapper {
    RecorderScreen(
        state = RecorderActivityVM.State(),
        onShare = {},
        onKeep = {},
        onDiscard = {},
        onPrivacyPolicy = {},
    )
}

@Preview2
@Composable
private fun RecorderScreenPreview() = PreviewWrapper {
    RecorderScreen(
        state = RecorderActivityVM.State(
            sessionName = "eu.darken.octi_0.14.0_1709312400000",
            sessionPath = "/storage/emulated/0/Android/data/eu.darken.octi/files/debug/logs/eu.darken.octi_0.14.0_1709312400000",
            files = listOf(
                RecorderActivityVM.LogFileInfo("core.log", 524288L),
                RecorderActivityVM.LogFileInfo("device_info.txt", 256L),
            ),
            fileCount = 2,
            totalUncompressedSize = 524544L,
            compressedSize = 131072L,
            loading = false,
        ),
        onShare = {},
        onKeep = {},
        onDiscard = {},
        onPrivacyPolicy = {},
    )
}

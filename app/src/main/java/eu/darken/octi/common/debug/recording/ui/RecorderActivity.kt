package eu.darken.octi.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.theming.ThemeState
import eu.darken.octi.common.uix.Activity2

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private val vm: RecorderActivityVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OctiTheme(state = ThemeState()) {
                LaunchedEffect(Unit) {
                    vm.shareEvent.collect { startActivity(it) }
                }

                val state by waitForState(vm.state)
                state?.let {
                    RecorderScreen(
                        state = it,
                        onShare = { vm.share() },
                        onPrivacyPolicy = { vm.goPrivacyPolicy() },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val RECORD_PATH = "logPath"

        fun getLaunchIntent(context: Context, path: String): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(RECORD_PATH, path)
            return intent
        }
    }
}

@Composable
fun RecorderScreen(
    state: RecorderActivityVM.State,
    onShare: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    OutlinedCard(
        modifier = Modifier.padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Recorded file",
                style = MaterialTheme.typography.labelMedium,
            )

            Text(
                text = state.normalPath ?: "",
                style = MaterialTheme.typography.bodyMedium,
            )

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

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_debuglog_size_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (state.normalSize != -1L) {
                        Text(
                            text = Formatter.formatShortFileSize(context, state.normalSize),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Button(onClick = onShare) {
                        Text(text = stringResource(CommonR.string.general_share_action))
                    }
                }
            }
        }
    }
}

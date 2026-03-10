package eu.darken.octi.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.uix.Activity2
import eu.darken.octi.common.R as CommonR

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private val vm: RecorderActivityVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeState by vm.themeState.collectAsState()
            OctiTheme(state = themeState) {
                LaunchedEffect(Unit) {
                    vm.shareEvent.collect { startActivity(it) }
                }
                LaunchedEffect(Unit) {
                    vm.finishEvent.collect { finish() }
                }

                var showDeleteConfirm by remember { mutableStateOf(false) }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text(stringResource(R.string.debug_debuglog_discard_confirmation_title)) },
                        text = { Text(stringResource(R.string.debug_debuglog_discard_confirmation_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirm = false
                                vm.discard()
                            }) {
                                Text(stringResource(CommonR.string.general_delete_action))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text(stringResource(CommonR.string.general_cancel_action))
                            }
                        },
                    )
                }

                val state by waitForState(vm.state)
                state?.let {
                    RecorderScreen(
                        state = it,
                        onShare = { vm.share() },
                        onSave = { vm.keep() },
                        onDiscard = { showDeleteConfirm = true },
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

package eu.darken.octi.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.uix.Activity2

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

                val state by waitForState(vm.state)
                state?.let {
                    RecorderScreen(
                        state = it,
                        onShare = { vm.share() },
                        onSave = { vm.keep() },
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

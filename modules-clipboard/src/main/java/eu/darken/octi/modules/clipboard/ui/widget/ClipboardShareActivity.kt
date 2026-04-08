package eu.darken.octi.modules.clipboard.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.clipboard.ClipboardHandler
import eu.darken.octi.modules.clipboard.R
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardShareActivity : ComponentActivity() {

    @Inject lateinit var clipboardHandler: ClipboardHandler

    private val shared = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log(TAG, VERBOSE) { "onCreate()" }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        log(TAG, VERBOSE) { "onWindowFocusChanged(hasFocus=$hasFocus)" }
        if (!hasFocus) return
        if (!shared.compareAndSet(false, true)) return
        shareAndFinish()
    }

    private fun shareAndFinish() {
        val appContext = applicationContext
        lifecycleScope.launch {
            val result = try {
                clipboardHandler.shareCurrentOSClipboard()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Share failed: ${e.asLog()}" }
                null
            }
            val messageRes = when (result) {
                ClipboardHandler.ShareResult.OK -> R.string.module_clipboard_widget_share_success
                ClipboardHandler.ShareResult.EMPTY -> R.string.module_clipboard_widget_share_empty
                ClipboardHandler.ShareResult.BLOCKED -> R.string.module_clipboard_widget_share_blocked
                ClipboardHandler.ShareResult.UNSUPPORTED -> R.string.module_clipboard_widget_share_unsupported
                null -> R.string.module_clipboard_widget_share_failed
            }
            Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show()
            try {
                ClipboardGlanceWidget().updateAll(appContext)
            } catch (e: Exception) {
                log(TAG, ERROR) { "updateAll failed: ${e.asLog()}" }
            }
            finish()
        }
    }

    companion object {
        private val TAG = logTag("Module", "Clipboard", "Widget", "ShareActivity")

        fun createIntent(context: Context): Intent = Intent(context, ClipboardShareActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
    }
}

package eu.darken.octi.main.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.main.core.SomeRepo
import javax.inject.Inject

@AndroidEntryPoint
class ExampleReceiver : BroadcastReceiver() {

    @Inject lateinit var someRepo: SomeRepo

    override fun onReceive(context: Context, intent: Intent) {
        log { "onReceive($context, $intent)" }
        if (intent.action != Intent.ACTION_HEADSET_PLUG) {
            log(WARN) { "Unknown action: $intent.action" }
            return
        }
    }
}

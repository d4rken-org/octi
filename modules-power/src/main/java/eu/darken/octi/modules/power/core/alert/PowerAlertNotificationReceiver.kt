package eu.darken.octi.modules.power.core.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PowerAlertNotificationReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var alertManager: PowerAlertManager

    init {
        log(TAG) { "init()" }
    }

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }

        val asyncPi = goAsync()

        appScope.launch {
            if (intent.action == PowerAlertNotifications.ACTION_DISMISS) {
                val alertId = intent.getStringExtra(PowerAlertNotifications.ARG_ALERT_ID)
                if (alertId != null) {
                    log(TAG, INFO) { "Processing dismissed alert $alertId" }
                    alertManager.dismissAlert(alertId)
                } else {
                    log(TAG, ERROR) { "AlertId was missing from $intent" }
                }
            } else {
                log(TAG, ERROR) { "Unknown action: ${intent.action}" }
            }

            delay(1000)
            log(TAG) { "Finished notification receiver" }
            asyncPi.finish()
        }
    }

    companion object {
        val TAG = logTag("Module", "Power", "Alert", "Notifications", "Receiver")
    }
}
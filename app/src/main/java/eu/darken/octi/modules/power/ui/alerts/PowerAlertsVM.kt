package eu.darken.octi.modules.power.ui.alerts

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.power.core.alerts.BatteryLowAlert
import eu.darken.octi.modules.power.core.alerts.PowerAlertManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class PowerAlertsVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    metaRepo: MetaRepo,
    private val alertsManager: PowerAlertManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: PowerAlertsFragmentArgs by handle.navArgs()

    val events = SingleLiveEvent<PowerAlertsAction>()

    init {
        launch { alertsManager.dismissBatteryLowAlert(navArgs.deviceId) }
    }

    data class State(
        val deviceLabel: String = "",
        val batteryLowAlert: BatteryLowAlert? = null,
    )

    val state = combine(
        alertsManager.alerts.map { alerts -> alerts.filter { it.deviceId == navArgs.deviceId } },
        metaRepo.state,
    ) { alerts, metaState ->
        val metaData = metaState.all.firstOrNull { it.deviceId == navArgs.deviceId }

        if (metaData == null) {
            log(TAG, ERROR) { "No meta data found for ${navArgs.deviceId}" }
            popNavStack()
            return@combine State()
        }

        State(
            deviceLabel = metaData.data.deviceLabel ?: metaData.data.deviceName,
            batteryLowAlert = alerts.find { it is BatteryLowAlert } as BatteryLowAlert?
        )
    }.asLiveData2()

    fun setBatteryLowAlert(threshold: Float) = launch {
        log(TAG) { "setBatteryLowAlert($threshold)" }
        val cleanThreshold = String.format(Locale.ROOT, "%.2f", threshold.coerceIn(0f, 95f)).toFloat()
        alertsManager.setBatteryLowAlert(navArgs.deviceId, cleanThreshold.takeIf { it > 0f })
    }

    companion object {
        private val TAG = logTag("Module", "Power", "Alerts", "Fragment", "VM")
    }
}
package eu.darken.octi.modules.power.ui.alerts

import android.annotation.SuppressLint
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.isPro
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.modules.power.core.alert.BatteryHighAlertRule
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class PowerAlertsVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    metaRepo: MetaRepo,
    private val alertsManager: PowerAlertManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val deviceIdFlow = MutableStateFlow<DeviceId?>(null)

    val events = SingleEventFlow<PowerAlertsAction>()

    data class State(
        val deviceLabel: String = "",
        val batteryLowAlert: PowerAlert<BatteryLowAlertRule>? = null,
        val batteryHighAlert: PowerAlert<BatteryHighAlertRule>? = null,
    )

    val state = deviceIdFlow
        .filterNotNull()
        .flatMapLatest { deviceId ->
            combine(
                alertsManager.alerts.map { alerts -> alerts.filter { it.deviceId == deviceId } },
                metaRepo.state,
            ) { alerts, metaState ->
                val metaData = metaState.all.firstOrNull { it.deviceId == deviceId }

                if (metaData == null) {
                    log(TAG, ERROR) { "No meta data found for $deviceId" }
                    navUp()
                    return@combine State()
                }

                @Suppress("UNCHECKED_CAST")
                val lowAlert = alerts.find { it.rule is BatteryLowAlertRule } as PowerAlert<BatteryLowAlertRule>?
                @Suppress("UNCHECKED_CAST")
                val highAlert = alerts.find { it.rule is BatteryHighAlertRule } as PowerAlert<BatteryHighAlertRule>?

                State(
                    deviceLabel = metaData.data.deviceLabel ?: metaData.data.deviceName,
                    batteryLowAlert = lowAlert,
                    batteryHighAlert = highAlert,
                )
            }
        }
        .asStateFlow()

    fun initialize(deviceId: String) {
        log(TAG) { "initialize($deviceId)" }
        val id = DeviceId(deviceId)
        deviceIdFlow.value = id
        launch { alertsManager.dismissAlerts(id) }
    }

    fun setBatteryLowAlert(threshold: Float) = launch {
        val deviceId = deviceIdFlow.value ?: return@launch
        log(TAG) { "setBatteryLowAlert($threshold)" }
        if (!upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade())
            return@launch
        }
        val cleanThreshold = String.format(Locale.ROOT, "%.2f", threshold.coerceIn(0f, 95f)).toFloat()
        alertsManager.setBatteryLowAlert(deviceId, cleanThreshold.takeIf { it > 0f })
    }

    fun setBatteryHighAlert(threshold: Float) = launch {
        val deviceId = deviceIdFlow.value ?: return@launch
        log(TAG) { "setBatteryHighAlert($threshold)" }
        if (!upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade())
            return@launch
        }
        val cleanThreshold = String.format(Locale.ROOT, "%.2f", threshold.coerceIn(0f, 100f)).toFloat()
        alertsManager.setBatteryHighAlert(deviceId, cleanThreshold.takeIf { it > 0f })
    }

    companion object {
        private val TAG = logTag("Module", "Power", "Alerts", "VM")
    }
}

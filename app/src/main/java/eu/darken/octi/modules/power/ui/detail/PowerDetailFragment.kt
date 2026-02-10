package eu.darken.octi.modules.power.ui.detail

import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.PowerDetailSheetBinding
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.ui.PowerEstimationFormatter

@AndroidEntryPoint
class PowerDetailFragment : BottomSheetDialogFragment2() {

    override val vm: PowerDetailVM by viewModels()
    override lateinit var ui: PowerDetailSheetBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = PowerDetailSheetBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val temperatureFormatter = TemperatureFormatter(requireContext())
        val estimationFormatter = PowerEstimationFormatter(requireContext())

        vm.state.observe2(ui) { state ->
            val power = state.powerData?.data ?: return@observe2

            levelValue.text = "${(power.battery.percent * 100).toInt()}%"

            statusValue.text = when (power.status) {
                Status.FULL -> getString(PowerR.string.module_power_battery_status_full)
                Status.CHARGING -> getString(PowerR.string.module_power_battery_status_charging)
                Status.DISCHARGING -> getString(PowerR.string.module_power_battery_status_discharging)
                Status.UNKNOWN -> getString(PowerR.string.module_power_battery_status_unknown)
            }

            speedValue.text = when (power.status) {
                Status.CHARGING -> when (power.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> getString(PowerR.string.module_power_battery_status_charging_slow)
                    ChargeIO.Speed.FAST -> getString(PowerR.string.module_power_battery_status_charging_fast)
                    ChargeIO.Speed.NORMAL -> getString(PowerR.string.module_power_battery_status_charging)
                }

                Status.DISCHARGING -> when (power.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> getString(PowerR.string.module_power_battery_status_discharging_slow)
                    ChargeIO.Speed.FAST -> getString(PowerR.string.module_power_battery_status_discharging_fast)
                    ChargeIO.Speed.NORMAL -> getString(PowerR.string.module_power_battery_status_discharging)
                }

                else -> getString(eu.darken.octi.common.R.string.general_na_label)
            }

            temperatureValue.text = power.battery.temp?.let {
                temperatureFormatter.formatTemperature(it)
            } ?: getString(eu.darken.octi.common.R.string.general_na_label)

            estimationValue.text = estimationFormatter.format(power)

            healthValue.text = when (power.battery.health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> getString(PowerR.string.module_power_detail_health_good)
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> getString(PowerR.string.module_power_detail_health_overheat)
                BatteryManager.BATTERY_HEALTH_DEAD -> getString(PowerR.string.module_power_detail_health_dead)
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> getString(PowerR.string.module_power_detail_health_over_voltage)
                BatteryManager.BATTERY_HEALTH_COLD -> getString(PowerR.string.module_power_detail_health_cold)
                else -> getString(PowerR.string.module_power_detail_health_unknown)
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}

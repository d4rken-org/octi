package eu.darken.octi.modules.power.ui.alerts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.EdgeToEdgeHelper
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.common.isBold
import eu.darken.octi.common.observe2
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.ModulePowerAlertsFragmentBinding


@AndroidEntryPoint
class PowerAlertsFragment : Fragment3(R.layout.module_power_alerts_fragment) {

    override val vm: PowerAlertsVM by viewModels()
    override val ui: ModulePowerAlertsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.toolbar, top = true, left = true, right = true)
            insetsPadding(ui.list, left = true, right = true, bottom = true)
        }
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        ui.lowbatteryThresholdSlider.apply {
            addOnChangeListener { _, value, _ ->
                ui.lowbatteryThresholdSliderCaption.text = when (value) {
                    0f -> getString(PowerR.string.module_power_alerts_lowbattery_disabled_caption)
                    else -> getString(
                        PowerR.string.module_power_alerts_lowbattery_slider_value_caption,
                        "${(value * 100).toInt()}%"
                    )
                }
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    vm.setBatteryLowAlert(slider.value)
                }
            })
            stepSize = 0.05f
            valueFrom = 0f
            valueTo = 0.95f
        }

        ui.highbatteryThresholdSlider.apply {
            addOnChangeListener { _, value, _ ->
                ui.highbatteryThresholdSliderCaption.text = when (value) {
                    0f -> getString(PowerR.string.module_power_alerts_highbattery_disabled_caption)
                    else -> getString(
                        PowerR.string.module_power_alerts_highbattery_slider_value_caption,
                        "${(value * 100).toInt()}%"
                    )
                }
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    vm.setBatteryHighAlert(slider.value)
                }
            })
            stepSize = 0.05f
            valueFrom = 0f
            valueTo = 1f
        }

        vm.state.observe2(this@PowerAlertsFragment, ui) { state ->
            ui.toolbar.subtitle = getString(R.string.device_x_label, state.deviceLabel)
            lowbatteryThresholdSlider.value = state.batteryLowAlert?.rule?.threshold ?: 0f
            lowbatteryTitle.isBold = state.batteryLowAlert != null
            highbatteryThresholdSlider.value = state.batteryHighAlert?.rule?.threshold ?: 0f
            highbatteryTitle.isBold = state.batteryHighAlert != null
        }

        vm.events.observe2 { event ->

        }

        super.onViewCreated(view, savedInstanceState)
    }
}

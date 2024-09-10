package eu.darken.octi.modules.power.ui.alerts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
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
                    0f -> getString(R.string.module_power_alerts_lowbattery_disabled_caption)
                    else -> getString(
                        R.string.module_power_alerts_lowbattery_slider_value_caption,
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

        vm.state.observe2(this@PowerAlertsFragment, ui) { state ->
            ui.toolbar.subtitle = getString(R.string.device_x_label, state.deviceLabel)
            lowbatteryThresholdSlider.value = state.batteryLowAlert?.threshold ?: 0f
            lowbatteryTitle.isBold = state.batteryLowAlert != null
        }

        vm.events.observe2 { event ->

        }

        super.onViewCreated(view, savedInstanceState)
    }
}

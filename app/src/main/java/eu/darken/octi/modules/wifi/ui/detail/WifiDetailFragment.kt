package eu.darken.octi.modules.wifi.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.WifiDetailSheetBinding
import eu.darken.octi.modules.wifi.R as WifiR
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.modules.wifi.core.WifiInfo

@AndroidEntryPoint
class WifiDetailFragment : BottomSheetDialogFragment2() {

    override val vm: WifiDetailVM by viewModels()
    override lateinit var ui: WifiDetailSheetBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = WifiDetailSheetBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(ui) { state ->
            val wifi = state.wifiData?.data ?: return@observe2

            ssidValue.text = wifi.currentWifi?.ssid
                ?: getString(WifiR.string.module_wifi_unknown_ssid_label)

            frequencyValue.text = when (wifi.currentWifi?.freqType) {
                WifiInfo.Wifi.Type.FIVE_GHZ -> getString(WifiR.string.module_wifi_freq_5ghz)
                WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ -> getString(WifiR.string.module_wifi_freq_2_4ghz)
                else -> getString(CommonR.string.general_na_label)
            }

            val sig = wifi.currentWifi?.reception ?: 0f
            signalValue.text = when {
                sig > 0.65f -> getString(WifiR.string.module_wifi_reception_good_label)
                sig > 0.3f -> getString(WifiR.string.module_wifi_reception_okay_label)
                sig > 0.0f -> getString(WifiR.string.module_wifi_reception_bad_label)
                else -> getString(CommonR.string.general_na_label)
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}

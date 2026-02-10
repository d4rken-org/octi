package eu.darken.octi.modules.connectivity.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.ConnectivityDetailSheetBinding
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.R as ConnectivityR

@AndroidEntryPoint
class ConnectivityDetailFragment : BottomSheetDialogFragment2() {

    override val vm: ConnectivityDetailVM by viewModels()
    override lateinit var ui: ConnectivityDetailSheetBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = ConnectivityDetailSheetBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(ui) { state ->
            val info = state.connectivityInfo

            connectionTypeValue.text = when (info?.connectionType) {
                ConnectivityInfo.ConnectionType.WIFI -> getString(ConnectivityR.string.module_connectivity_type_wifi_label)
                ConnectivityInfo.ConnectionType.CELLULAR -> getString(ConnectivityR.string.module_connectivity_type_cellular_label)
                ConnectivityInfo.ConnectionType.ETHERNET -> getString(ConnectivityR.string.module_connectivity_type_ethernet_label)
                ConnectivityInfo.ConnectionType.NONE, null -> getString(ConnectivityR.string.module_connectivity_type_none_label)
            }

            val unknownLocal = getString(ConnectivityR.string.module_connectivity_unknown_local_ip_label)

            publicIpValue.text = info?.publicIp
                ?: getString(ConnectivityR.string.module_connectivity_unknown_public_ip_label)
            publicIpCopy.isVisible = info?.publicIp != null
            publicIpCopy.setOnClickListener { copyToClipboard(info?.publicIp) }

            localIpv4Value.text = info?.localAddressIpv4 ?: unknownLocal
            localIpv4Copy.isVisible = info?.localAddressIpv4 != null
            localIpv4Copy.setOnClickListener { copyToClipboard(info?.localAddressIpv4) }

            localIpv6Value.text = info?.localAddressIpv6 ?: unknownLocal
            localIpv6Copy.isVisible = info?.localAddressIpv6 != null
            localIpv6Copy.setOnClickListener { copyToClipboard(info?.localAddressIpv6) }

            gatewayValue.text = info?.gatewayIp ?: unknownLocal
            dnsValue.text = info?.dnsServers
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: unknownLocal
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun copyToClipboard(text: String?) {
        if (text == null) return
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("IP", text))
        Toast.makeText(requireContext(), getString(eu.darken.octi.common.R.string.general_copy_action), Toast.LENGTH_SHORT).show()
    }
}

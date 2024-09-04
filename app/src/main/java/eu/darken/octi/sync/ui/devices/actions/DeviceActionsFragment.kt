package eu.darken.octi.sync.ui.devices.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.SyncDevicesDeviceActionsBinding

@AndroidEntryPoint
class DeviceActionsFragment : BottomSheetDialogFragment2() {

    override val vm: DeviceActionsVM by viewModels()
    override lateinit var ui: SyncDevicesDeviceActionsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = SyncDevicesDeviceActionsBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.deleteAction.setOnClickListener { vm.deleteDevice() }

        vm.state.observe2(ui) { state ->
            title.text = state.metaInfo?.labelOrFallback ?: "?"
            subtitle.text = state.deviceId.id
            deleteHint.apply {
                text = when (state.removeIsRevoke) {
                    true -> getString(R.string.sync_delete_device_revokeaccess_caveat)
                    false -> getString(R.string.sync_delete_device_keepaccess_caveat)
                    null -> ""
                }
                isGone = state.removeIsRevoke == null
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
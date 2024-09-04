package eu.darken.octi.syncs.gdrive.ui.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.setChecked2
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.SyncActionsGdriveFragmentBinding

@AndroidEntryPoint
class GDriveActionsFragment : BottomSheetDialogFragment2() {

    override val vm: GDriveActionsVM by viewModels()
    override lateinit var ui: SyncActionsGdriveFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = SyncActionsGdriveFragmentBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.pausedToggle.setOnClickListener { vm.togglePause() }
        ui.syncAction.setOnClickListener { vm.forceSync() }
        ui.devicesAction.apply {
            isEnabled = false
            setOnClickListener { vm.viewDevices() }
        }
        ui.disconnectAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(R.string.sync_gdrive_disconnect_confirmation_desc)
                setPositiveButton(R.string.general_disconnect_action) { _, _ ->
                    vm.disconnct()
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ -> }
            }.show()
        }
        ui.resetAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(R.string.sync_gdrive_reset_confirmation_desc)
                setPositiveButton(R.string.general_reset_action) { _, _ ->
                    vm.reset()
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ -> }
            }.show()
        }
        vm.state.observe2(ui) {
            title.apply {
                text = getString(R.string.sync_gdrive_type_label)
                if (it.account.isAppDataScope) append(" (${getString(R.string.sync_gdrive_appdata_label)})")
            }
            subtitle.text = it.account.email
            pausedToggle.setChecked2(it.isPaused, false)
            devicesAction.isEnabled = !it.isPaused
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
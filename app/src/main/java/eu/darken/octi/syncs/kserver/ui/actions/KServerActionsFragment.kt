package eu.darken.octi.syncs.kserver.ui.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.SyncActionsKserverFragmentBinding

@AndroidEntryPoint
class KServerActionsFragment : BottomSheetDialogFragment2() {

    override val vm: KServerActionsVM by viewModels()
    override lateinit var ui: SyncActionsKserverFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = SyncActionsKserverFragmentBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.pausedToggle.setOnClickListener { vm.togglePause() }
        ui.syncAction.setOnClickListener { vm.forceSync() }
        ui.linkAction.setOnClickListener { vm.linkNewDevice() }
        ui.disconnectAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(R.string.sync_kserver_disconnect_confirmation_desc)
                setPositiveButton(R.string.general_disconnect_action) { _, _ ->
                    vm.disconnct()
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }
        ui.resetAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(R.string.sync_kserver_reset_confirmation_desc)
                setPositiveButton(R.string.general_reset_action) { _, _ ->
                    vm.reset()
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }

        vm.state.observe2(ui) {
            title.text = "${getString(R.string.sync_kserver_type_label)} (${it.credentials.serverAdress.domain})"
            subtitle.text = it.credentials.accountId.id
            pausedToggle.isChecked = it.isPaused
        }

        vm.actionEvents.observe2(ui) {

        }
        super.onViewCreated(view, savedInstanceState)
    }
}
package eu.darken.octi.syncs.jserver.ui.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.uix.BottomSheetDialogFragment2
import eu.darken.octi.databinding.SyncActionsJserverFragmentBinding

@AndroidEntryPoint
class JServerActionsFragment : BottomSheetDialogFragment2() {

    override val vm: JServerActionsVM by viewModels()
    override lateinit var ui: SyncActionsJserverFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = SyncActionsJserverFragmentBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.linkAction.setOnClickListener { vm.linkNewDevice() }
        ui.disconnectAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(R.string.sync_jserver_disconnect_confirmation_desc)
                setPositiveButton(R.string.general_disconnect_action) { _, _ ->
                    vm.disconnct()
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }
        ui.wipeAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(R.string.sync_jserver_wipe_confirmation_desc)
                setPositiveButton(R.string.general_wipe_action) { _, _ ->
                    vm.wipe()
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }

        vm.state.observe2(ui) {
            title.text = "${getString(R.string.sync_jserver_type_label)} (${it.credentials.serverAdress.domain})"
            subtitle.text = it.credentials.accountId.id
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
package eu.darken.octi.sync.ui.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.EdgeToEdgeHelper
import eu.darken.octi.common.lists.differ.update
import eu.darken.octi.common.lists.setupDefaults
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncDevicesFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class SyncDevicesFragment : Fragment3(R.layout.sync_devices_fragment) {

    override val vm: SyncDevicesVM by viewModels()
    override val ui: SyncDevicesFragmentBinding by viewBinding()

    @Inject lateinit var adapter: SyncDevicesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.toolbar, top = true)
            insetsPadding(ui.list, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.list.setupDefaults(adapter)
        vm.state.observe2(ui) { state ->
            adapter.update(state.items)
        }
        super.onViewCreated(view, savedInstanceState)
    }

}

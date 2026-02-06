package eu.darken.octi.sync.ui.list

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
import eu.darken.octi.databinding.SyncListFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class SyncListFragment : Fragment3(R.layout.sync_list_fragment) {

    override val vm: SyncListVM by viewModels()
    override val ui: SyncListFragmentBinding by viewBinding()

    @Inject lateinit var adapter: SyncListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.toolbar, top = true, left = true, right = true)
            insetsPadding(ui.list, left = true, right = true, bottom = true)
            insetsPadding(ui.fabContainer, right = true, bottom = true)
        }
        ui.toolbar.setupWithNavController(findNavController())
        ui.fab.setOnClickListener { vm.addConnector() }

        ui.list.setupDefaults(adapter)
        vm.state.observe2(ui) {
            adapter.update(it.connectors)
        }
        super.onViewCreated(view, savedInstanceState)
    }

}

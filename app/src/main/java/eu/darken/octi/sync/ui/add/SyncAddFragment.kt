package eu.darken.octi.sync.ui.add

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.lists.differ.update
import eu.darken.octi.common.lists.setupDefaults
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.SyncAddNewFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class SyncAddFragment : Fragment3(R.layout.sync_add_new_fragment) {

    override val vm: SyncAddVM by viewModels()
    override val ui: SyncAddNewFragmentBinding by viewBinding()
    @Inject lateinit var syncAddAdapter: SyncAddAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_help -> {
                        showHelpDialog()
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        ui.list.setupDefaults(syncAddAdapter)

        vm.addItems.observe2(ui) {
            syncAddAdapter.update(it)
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun showHelpDialog() = MaterialAlertDialogBuilder(requireContext()).apply {
        setMessage(R.string.sync_add_help_desc)
        setPositiveButton(R.string.general_gotit_action) { _, _ ->

        }
    }.show()
}

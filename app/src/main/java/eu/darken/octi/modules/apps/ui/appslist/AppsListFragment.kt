package eu.darken.octi.modules.apps.ui.appslist

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.error.asErrorDialogBuilder
import eu.darken.octi.common.lists.differ.update
import eu.darken.octi.common.lists.setupDefaults
import eu.darken.octi.common.observe2
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.ModuleAppsListFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class AppsListFragment : Fragment3(R.layout.module_apps_list_fragment) {

    override val vm: AppsListVM by viewModels()
    override val ui: ModuleAppsListFragmentBinding by viewBinding()

    @Inject lateinit var appsAdapter: AppsListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        ui.list.setupDefaults(appsAdapter, dividers = false)

        vm.listItems.observe2(this@AppsListFragment, ui) {
            ui.toolbar.subtitle = getString(R.string.device_x_label, it.deviceLabel)
            appsAdapter.update(it.items)
        }

        vm.events.observe2 { event ->
            when (event) {
                is AppListAction.OpenAppOrStore -> {
                    try {
                        startActivity(event.intent)
                    } catch (e: Exception) {
                        try {
                            startActivity(event.fallback)
                        } catch (e: Exception) {
                            e.asErrorDialogBuilder(requireActivity()).show()
                        }
                    }

                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}

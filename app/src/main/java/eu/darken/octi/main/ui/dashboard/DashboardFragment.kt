package eu.darken.octi.main.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.lists.differ.update
import eu.darken.octi.common.lists.setupDefaults
import eu.darken.octi.common.navigation.doNavigate
import eu.darken.octi.common.observe2
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.DashboardFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class DashboardFragment : Fragment3(R.layout.dashboard_fragment) {

    override val vm: DashboardVM by viewModels()
    override val ui: DashboardFragmentBinding by viewBinding()

    @Inject
    lateinit var dashboardAdapter: DashboardAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_sync_services -> {
                        vm.goToSyncServices()
                        true
                    }
                    R.id.action_settings -> {
                        doNavigate(DashboardFragmentDirections.actionDashFragmentToSettingsContainerFragment())
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }
            subtitle = "Buildtype: ${BuildConfigWrap.BUILD_TYPE}"
        }

        ui.refreshAction.setOnClickListener { vm.refresh() }

        ui.list.setupDefaults(dashboardAdapter)

        vm.listItems.observe2(this@DashboardFragment, ui) {
            dashboardAdapter.update(it.items)
        }

        super.onViewCreated(view, savedInstanceState)
    }
}

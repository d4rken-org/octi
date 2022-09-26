package eu.darken.octi.main.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.lists.differ.update
import eu.darken.octi.common.lists.setupDefaults
import eu.darken.octi.common.navigation.doNavigate
import eu.darken.octi.common.observe2
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.uix.Fragment3
import eu.darken.octi.common.viewbinding.viewBinding
import eu.darken.octi.databinding.DashboardFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class DashboardFragment : Fragment3(R.layout.dashboard_fragment) {

    override val vm: DashboardVM by viewModels()
    override val ui: DashboardFragmentBinding by viewBinding()

    @Inject lateinit var dashboardAdapter: DashboardAdapter

    lateinit var permissionLauncher: ActivityResultLauncher<String>
    var awaitingPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awaitingPermission = savedInstanceState?.getBoolean("awaitingPermission") ?: false

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            log { "Request for $id was granted=$granted" }
            vm.onPermissionResult(granted)
        }
    }

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
            subtitle = BuildConfigWrap.VERSION_DESCRIPTION
        }

        vm.dashboardEvents.observe2(ui) {
            when (it) {
                is DashboardEvent.RequestPermissionEvent -> {
                    when (it.permission) {
                        Permission.IGNORE_BATTERY_OPTIMIZATION -> {
                            awaitingPermission = true
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${requireContext().packageName}")
                                )
                            )
                        }
                        else -> permissionLauncher.launch(it.permission.permissionId)
                    }
                }
                is DashboardEvent.ShowPermissionDismissHint -> {
                    Snackbar
                        .make(view, R.string.permission_dismiss_hint, Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
        }

        ui.refreshAction.setOnClickListener { vm.refresh() }

        ui.list.setupDefaults(dashboardAdapter, dividers = false)

        vm.listItems.observe2(this@DashboardFragment, ui) {
            dashboardAdapter.update(it.items)
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaitingPermission", awaitingPermission)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        if (awaitingPermission) {
            awaitingPermission = false
            log { "awaitingPermission=true" }
            vm.onPermissionResult(true)
        }
    }

}

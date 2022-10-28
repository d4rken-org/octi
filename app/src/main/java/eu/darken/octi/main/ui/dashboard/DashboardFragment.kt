package eu.darken.octi.main.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import eu.darken.octi.databinding.DashboardPermissionItemBinding
import eu.darken.octi.main.ui.dashboard.items.bindItem
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

    private var offlineSnackbar: Snackbar? = null

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

        vm.dashboardEvents.observe2(ui) { event ->
            when (event) {
                is DashboardEvent.RequestPermissionEvent -> {
                    when (event.permission) {
                        Permission.IGNORE_BATTERY_OPTIMIZATION -> {
                            awaitingPermission = true
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${requireContext().packageName}")
                                )
                            )
                        }
                        else -> permissionLauncher.launch(event.permission.permissionId)
                    }
                }
                is DashboardEvent.ShowPermissionDismissHint -> {
                    Snackbar
                        .make(view, R.string.permission_dismiss_hint, Snackbar.LENGTH_SHORT)
                        .show()
                }
                is DashboardEvent.ShowPermissionPopup -> {
                    val binding = DashboardPermissionItemBinding.inflate(layoutInflater)
                    val dialog = MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()
                    binding.bindItem(
                        permission = event.permission,
                        onDismiss = {
                            event.onDismiss(it)
                            dialog.dismiss()
                        },
                        onGrant = {
                            event.onGrant(it)
                            dialog.dismiss()
                        }
                    )
                    dialog.show()
                }
                is DashboardEvent.LaunchUpgradeFlow -> vm.launchUpgradeFlow(requireActivity())
            }
        }

        ui.list.setupDefaults(dashboardAdapter, dividers = false)

        ui.refreshAction.setOnClickListener { vm.refresh() }
        ui.refreshSwipe.setOnRefreshListener { vm.refresh() }

        vm.state.observe2(this@DashboardFragment, ui) { state ->
            dashboardAdapter.update(state.items)
            refreshSwipe.isRefreshing = state.isRefreshing
            refreshAction.isGone = state.isRefreshing

            if (offlineSnackbar != null && !state.isOffline) {
                offlineSnackbar?.dismiss()
                offlineSnackbar = null
            } else if (offlineSnackbar == null && state.isOffline) {
                offlineSnackbar = Snackbar.make(
                    ui.coordinator,
                    getString(R.string.general_internal_not_available_msg),
                    Snackbar.LENGTH_INDEFINITE
                )
                offlineSnackbar?.show()
            }
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

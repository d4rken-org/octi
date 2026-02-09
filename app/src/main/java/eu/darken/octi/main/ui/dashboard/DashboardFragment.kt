package eu.darken.octi.main.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.EdgeToEdgeHelper
import eu.darken.octi.common.colorString
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.error.asErrorDialogBuilder
import eu.darken.octi.common.getQuantityString2
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
import eu.darken.octi.main.ui.dashboard.items.perdevice.DeviceVH
import java.util.Collections
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

        if (savedInstanceState == null) vm.refresh()
    }

    private var offlineSnackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.toolbar, top = true, left = true, right = true)
            insetsPadding(ui.refreshSwipe, left = true, right = true, bottom = true)
            insetsPadding(ui.refreshActionContainer, right = true, bottom = true)
        }

        ui.toolbar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_sync_services -> {
                        vm.goToSyncServices()
                        true
                    }

                    R.id.action_upgrade -> {
                        DashboardFragmentDirections.goToUpgradeFragment().navigate()
                        true
                    }

                    R.id.action_settings -> {
                        doNavigate(DashboardFragmentDirections.actionDashFragmentToSettingsContainerFragment())
                        true
                    }

                    else -> super.onOptionsItemSelected(it)
                }
            }
            subtitle = if (BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE) {
                "v${BuildConfigWrap.VERSION_NAME}"
            } else {
                BuildConfigWrap.VERSION_DESCRIPTION
            }
        }

        vm.upgradeStatus.observe2(ui) { state ->
            toolbar.menu.findItem(R.id.action_upgrade)?.isVisible = !state.isPro

            val baseTitle = when {
                state.isPro -> getString(R.string.app_name_upgraded)
                else -> getString(CommonR.string.app_name)
            }.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            toolbar.title = if (baseTitle.size == 2) {
                val builder = SpannableStringBuilder(baseTitle[0] + " ")
                builder.append(colorString(requireContext().getColor(R.color.colorUpgraded), baseTitle[1]))
            } else {
                getString(CommonR.string.app_name)
            }
        }

        vm.dashboardEvents.observe2(ui) { event ->
            when (event) {
                is DashboardEvent.RequestPermissionEvent -> {
                    when (event.permission) {
                        Permission.IGNORE_BATTERY_OPTIMIZATION -> {
                            awaitingPermission = true
                            try {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (e: ActivityNotFoundException) {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${requireContext().packageName}")
                                    )
                                )
                            }
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
                is DashboardEvent.OpenAppOrStore -> {
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

        ui.list.setupDefaults(dashboardAdapter, dividers = false)
        
        // Setup drag-and-drop functionality
        val itemTouchHelper = ItemTouchHelper(DragCallback())
        itemTouchHelper.attachToRecyclerView(ui.list)

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
                    getString(CommonR.string.general_internal_not_available_msg),
                    Snackbar.LENGTH_INDEFINITE
                )
                offlineSnackbar?.show()
            }

            if (state.deviceCount > 0) {
                val deviceQuantity = requireContext().getQuantityString2(
                    R.plurals.general_devices_count_label,
                    state.deviceCount
                )

                val lastSyncAt = state.lastSyncAt?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilli()) }
                val deviceInfo = if (lastSyncAt != null) "$deviceQuantity ($lastSyncAt)" else deviceQuantity

                toolbar.subtitle = if (BuildConfigWrap.DEBUG) {
                    "$deviceInfo ${BuildConfigWrap.GIT_SHA}"
                } else {
                    deviceInfo
                }
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
        if (awaitingPermission) {
            awaitingPermission = false
            log { "awaitingPermission=true" }
            vm.onPermissionResult(true)
        }
    }

    private inner class DragCallback : ItemTouchHelper.Callback() {
        
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            // Allow drag for all DeviceVH items, prevent moving the limit card itself
            val item = dashboardAdapter.data[viewHolder.adapterPosition]
            if (item !is DeviceVH.Item) {
                return makeMovementFlags(0, 0)
            }
            
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition
            
            // Only allow moving between DeviceVH items
            val fromItem = dashboardAdapter.data.getOrNull(fromPosition)
            val toItem = dashboardAdapter.data.getOrNull(toPosition)
            
            if (fromItem !is DeviceVH.Item || toItem !is DeviceVH.Item) {
                return false
            }
            
            // Update the adapter data temporarily for visual feedback
            val data = dashboardAdapter.data.toMutableList()
            Collections.swap(data, fromPosition, toPosition)
            dashboardAdapter.update(data)
            
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Not implemented - we don't support swipe actions
        }
        
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            
            // Update the device order in settings when drag ends
            val deviceItems = dashboardAdapter.data.filterIsInstance<DeviceVH.Item>()
            val newOrder = deviceItems.map { it.meta.deviceId.id }
            
            vm.updateDeviceOrder(newOrder)
        }
        
        override fun isLongPressDragEnabled(): Boolean = true
        
        override fun isItemViewSwipeEnabled(): Boolean = false
    }

}

package eu.darken.octi.main.ui.dashboard

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hasApiLevel
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.modules.wifi.core.WifiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val wifiSettings: WifiSettings,
) {
    private val permissionCheckTrigger = MutableStateFlow(UUID.randomUUID())

    fun recheck() {
        log(TAG) { "recheck()" }
        permissionCheckTrigger.value = UUID.randomUUID()
    }

    val missingPermissions: Flow<Set<Permission>> = combine(
        permissionCheckTrigger,
        generalSettings.dismissedPermissions.flow,
        wifiSettings.isEnabled.flow,
    ) { _, dismissedPermissions, isNetworkEnabled ->
        val missingPermissions = mutableSetOf<Permission>()

        Permission.IGNORE_BATTERY_OPTIMIZATION
            .takeIf { !dismissedPermissions.contains(it) }
            ?.takeIf { !it.isGranted(context) }
            ?.run { missingPermissions.add(this) }

        Permission.ACCESS_COARSE_LOCATION
            .takeIf { !dismissedPermissions.contains(it) }
            ?.takeIf { isNetworkEnabled }
            ?.takeIf { hasApiLevel(27) && !hasApiLevel(29) }
            ?.takeIf { !it.isGranted(context) }
            ?.run { missingPermissions.add(this) }

        Permission.ACCESS_FINE_LOCATION
            .takeIf { !dismissedPermissions.contains(it) }
            ?.takeIf { isNetworkEnabled }
            ?.takeIf { hasApiLevel(29) }
            ?.takeIf { !it.isGranted(context) }
            ?.run { missingPermissions.add(this) }

        missingPermissions
    }
        .onEach { log(TAG) { "Missing permission: $it" } }

    companion object {
        private val TAG = logTag("PermissionTool")
    }
}
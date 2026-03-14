package eu.darken.octi.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.theming.ThemeColor
import eu.darken.octi.common.theming.ThemeMode
import eu.darken.octi.common.theming.ThemeSettings
import eu.darken.octi.common.theming.ThemeState
import eu.darken.octi.common.theming.ThemeStyle
import eu.darken.octi.main.core.updater.UpdateChecker
import eu.darken.octi.main.ui.dashboard.DashboardConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
    updateChecker: UpdateChecker,
) : ThemeSettings {

    private val Context.dataStore by preferencesDataStore(name = "core_settings")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val isOnboardingDone = dataStore.createValue("onboarding.finished", false)

    val isUpdateCheckEnabled = dataStore.createValue("updater.check.enabled", updateChecker.isEnabledByDefault())

    val isSyncSetupDismissed = dataStore.createValue("onboarding.syncsetup.dismissed", false)

    val themeMode = dataStore.createValue("core.ui.theme.mode", ThemeMode.SYSTEM, json)
    val themeStyle = dataStore.createValue("core.ui.theme.style", ThemeStyle.DEFAULT, json)
    val themeColor = dataStore.createValue("core.ui.theme.color", ThemeColor.GREEN, json)

    override val themeState: Flow<ThemeState>
        get() = combine(themeMode.flow, themeStyle.flow, themeColor.flow) { mode, style, color ->
            ThemeState(mode, style, color)
        }

    val dismissedPermissions: DataStoreValue<Set<Permission>> = dataStore.createValue(
        stringPreferencesKey("core.permission.dismissed"),
        reader = { raw ->
            (raw as? String)
                ?.split(",")
                ?.mapNotNull { elem -> Permission.values().firstOrNull { it.permissionId == elem } }
                ?.toSet()
                ?: emptySet()
        },
        writer = { perms -> perms.joinToString(",") { it.permissionId } }
    )

    val isLegacyLogCleanupDone = dataStore.createValue("debug.legacy.cleanup.done", false)

    val dashboardConfig = dataStore.createValue(
        "dashboard.ui.config",
        DashboardConfig(),
        json
    )

    suspend fun addDismissedPermission(permission: Permission) {
        log(TAG) { "addDismissedPermission(permission=$permission)" }
        dismissedPermissions.update { it + permission }
    }

    suspend fun toggleDeviceCollapsed(deviceId: String) {
        log(TAG) { "toggleDeviceCollapsed(deviceId=$deviceId)" }
        dashboardConfig.update { config ->
            config.toToggledCollapsed(deviceId)
        }
    }

    suspend fun updateDeviceOrder(newOrder: List<String>) {
        log(TAG) { "updateDeviceOrder(newOrder=$newOrder)" }
        dashboardConfig.update { config ->
            config.toUpdatedOrder(newOrder)
        }
    }

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}

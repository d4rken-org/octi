package eu.darken.octi.main.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.preferences.FlowPreference
import eu.darken.octi.common.preferences.PreferenceStoreMapper
import eu.darken.octi.common.preferences.Settings
import eu.darken.octi.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("core_settings", Context.MODE_PRIVATE)

    val isBugTrackingEnabled = preferences.createFlowPreference("core.bugtracking.enabled", true)

    val isWelcomeDismissed = preferences.createFlowPreference("onboarding.welcome.dismissed", false)

    val isSyncSetupDismissed = preferences.createFlowPreference("onboarding.syncsetup.dismissed", false)

    val themeType = preferences.createFlowPreference("core.ui.theme.type", ThemeType.SYSTEM.identifier)

    val dismissedPermissions: FlowPreference<Set<Permission>> = preferences.createFlowPreference(
        "core.permission.dismissed",
        reader = { raw ->
            (raw as? String)
                ?.split(",")
                ?.mapNotNull { elem -> Permission.values().firstOrNull { it.permissionId == elem } }
                ?.toSet()
                ?: emptySet()
        },
        writer = { perms -> perms.joinToString(",") { it.permissionId } }
    )

    fun addDismissedPermission(permission: Permission) {
        log(TAG) { "addDismissedPermission(permission=$permission)" }
        dismissedPermissions.update {
            it + permission
        }
    }

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        isBugTrackingEnabled,
        themeType
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}
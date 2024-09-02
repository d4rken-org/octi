package eu.darken.octi.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.datastore.PreferenceScreenData
import eu.darken.octi.common.datastore.PreferenceStoreMapper
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.autoreport.DebugSettings
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.theming.ThemeMode
import eu.darken.octi.common.theming.ThemeStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val debugSettings: DebugSettings,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "core_settings")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val isOnboardingDone = dataStore.createValue("onboarding.finished", false)

    val isWelcomeDismissed = dataStore.createValue("onboarding.welcome.dismissed", false)

    val isSyncSetupDismissed = dataStore.createValue("onboarding.syncsetup.dismissed", false)

    val themeMode = dataStore.createValue("core.ui.theme.mode", ThemeMode.SYSTEM, moshi)
    val themeStyle = dataStore.createValue("core.ui.theme.style", ThemeStyle.DEFAULT, moshi)

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

    suspend fun addDismissedPermission(permission: Permission) {
        log(TAG) { "addDismissedPermission(permission=$permission)" }
        dismissedPermissions.update { it + permission }
    }

    override val mapper = PreferenceStoreMapper(
        debugSettings.isAutoReportingEnabled,
        themeMode,
        themeStyle,
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}
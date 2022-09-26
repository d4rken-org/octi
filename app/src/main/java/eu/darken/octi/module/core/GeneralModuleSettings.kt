package eu.darken.octi.module.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.preferences.PreferenceStoreMapper
import eu.darken.octi.common.preferences.Settings
import eu.darken.octi.modules.power.core.PowerSettings
import eu.darken.octi.modules.wifi.core.WifiSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralModuleSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val powerSettings: PowerSettings,
    private val wifiSettings: WifiSettings,
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("module_settings", Context.MODE_PRIVATE)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        powerSettings.isEnabled,
        wifiSettings.isEnabled,
    )

    companion object {
        internal val TAG = logTag("Module", "Settings")
    }
}
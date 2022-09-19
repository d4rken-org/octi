package eu.darken.octi.modules.power.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.preferences.PreferenceStoreMapper
import eu.darken.octi.common.preferences.Settings
import eu.darken.octi.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : Settings() {

    override val preferences: SharedPreferences =
        context.getSharedPreferences("module_power_settings", Context.MODE_PRIVATE)

    val isEnabled = preferences.createFlowPreference("module.power.enabled", true)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        isEnabled
    )

    companion object {
        internal val TAG = logTag("Module", "Power", "Settings")
    }
}
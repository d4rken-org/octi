package eu.darken.octi.module.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.PreferenceScreenData
import eu.darken.octi.common.datastore.PreferenceStoreMapper
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.wifi.core.WifiSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralModuleSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val powerSettings: eu.darken.octi.modules.power.core.PowerSettings,
    private val wifiSettings: WifiSettings,
) : PreferenceScreenData {
    private val Context.dataStore by preferencesDataStore(name = "module_settings")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val mapper = PreferenceStoreMapper(
        powerSettings.isEnabled,
        wifiSettings.isEnabled,
    )

    companion object {
        internal val TAG = logTag("Module", "Settings")
    }
}
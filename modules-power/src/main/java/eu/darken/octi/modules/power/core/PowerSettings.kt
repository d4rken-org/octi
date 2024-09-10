package eu.darken.octi.modules.power.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.PreferenceScreenData
import eu.darken.octi.common.datastore.PreferenceStoreMapper
import eu.darken.octi.common.datastore.createSetValue
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleSettings
import eu.darken.octi.modules.power.core.alerts.BatteryLowAlert
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : PreferenceScreenData, ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_power_settings")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.power.enabled", true)

    override val mapper: PreferenceDataStore = PreferenceStoreMapper(
        isEnabled
    )

    val chargedFullAt = dataStore.createValue<Instant?>("module.power.status.full.at", null, moshi)

    val lowBatteryAlerts =
        dataStore.createSetValue<BatteryLowAlert>("module.power.alerts.lowbattery", emptySet(), moshi)

    companion object {
        internal val TAG = logTag("Module", "Power", "Settings")
    }
}
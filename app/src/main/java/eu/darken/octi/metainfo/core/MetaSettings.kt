package eu.darken.octi.metainfo.core

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
class MetaSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : Settings() {

    override val preferences: SharedPreferences =
        context.getSharedPreferences("settings_module_time", Context.MODE_PRIVATE)

    val isSyncEnabled = preferences.createFlowPreference("module.time.sync.enabled", true)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        isSyncEnabled
    )

    companion object {
        internal val TAG = logTag("Module", "Time", "Settings")
    }
}
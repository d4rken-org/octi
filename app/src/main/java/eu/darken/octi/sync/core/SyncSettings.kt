package eu.darken.octi.sync.core

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
class SyncSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("settings_sync", Context.MODE_PRIVATE)

    val syncOnMobile = preferences.createFlowPreference("sync.connection.mobile.enabled", true)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        syncOnMobile
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}
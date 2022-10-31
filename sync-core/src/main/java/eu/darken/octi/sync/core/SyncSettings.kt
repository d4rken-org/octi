package eu.darken.octi.sync.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.preferences.PreferenceStoreMapper
import eu.darken.octi.common.preferences.Settings
import eu.darken.octi.common.preferences.createFlowPreference
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    val deviceLabel = preferences.createFlowPreference<String?>("sync.device.self.label", null)

    val backgroundSyncEnabled = preferences.createFlowPreference("sync.background.enabled", true)

    val backgroundSyncInterval = preferences.createFlowPreference("sync.background.interval.minutes", 60)

    val backgroundSyncOnMobile = preferences.createFlowPreference("sync.background.mobile.enabled", true)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        deviceLabel,
        backgroundSyncEnabled,
        backgroundSyncInterval,
        backgroundSyncOnMobile,
    )

    val deviceId by lazy {
        val key = "sync.identifier.device"
        val rawId = preferences.getString(key, null) ?: kotlin.run {
            UUID.randomUUID().toString().also {
                preferences.edit().putString(key, it).commit()
            }
        }
        DeviceId(rawId).also {
            log(TAG, INFO) { "Our DeviceId is $it" }
        }
    }

    companion object {
        internal val TAG = logTag("Sync", "Settings")
    }
}
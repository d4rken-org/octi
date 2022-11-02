package eu.darken.octi.sync.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.PreferenceScreenData
import eu.darken.octi.common.datastore.PreferenceStoreMapper
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.runBlocking
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "sync_settings")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val deviceLabel = dataStore.createValue("sync.device.self.label", null as String?)

    val backgroundSyncEnabled = dataStore.createValue("sync.background.enabled", true)

    val backgroundSyncInterval = dataStore.createValue("sync.background.interval.minutes", 60)

    val backgroundSyncOnMobile = dataStore.createValue("sync.background.mobile.enabled", true)

    override val mapper: PreferenceStoreMapper = PreferenceStoreMapper(
        deviceLabel,
        backgroundSyncEnabled,
        backgroundSyncInterval,
        backgroundSyncOnMobile,
    )

    val deviceId by lazy {
        val key = stringPreferencesKey("sync.identifier.device")
        val rawId = runBlocking {
            dataStore.edit { prefs ->
                prefs[key] ?: kotlin.run {
                    UUID.randomUUID().toString().also {
                        prefs[key] = it
                    }
                }
            }[key]!!
        }
        DeviceId(rawId).also { log(TAG, INFO) { "Our DeviceId is $it" } }
    }

    companion object {
        internal val TAG = logTag("Sync", "Settings")
    }
}
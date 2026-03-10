package eu.darken.octi.main.core.release

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_release")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val releasePartyAt = dataStore.createValue("release.party.date", null as Instant?, json)
    val wantsBeta = dataStore.createValue("release.prerelease.consent", false)
    val earlyAdopter = dataStore.createValue("release.earlyadopter", null as Boolean?)

    companion object {
        internal val TAG = logTag("Release", "Settings")
    }
}
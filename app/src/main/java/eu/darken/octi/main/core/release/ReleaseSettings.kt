package eu.darken.octi.main.core.release

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.PreferenceScreenData
import eu.darken.octi.common.datastore.PreferenceStoreMapper
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_release")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val releasePartyAt = dataStore.createValue("release.party.date", null as Instant?, moshi)
    val wantsBeta = dataStore.createValue("release.prerelease.consent", false)
    val earlyAdopter = dataStore.createValue("release.earlyadopter", null as Boolean?)

    override val mapper = PreferenceStoreMapper(

    )

    companion object {
        internal val TAG = logTag("Release", "Settings")
    }
}
package eu.darken.octi.common.review

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
class ReviewSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_review_gplay")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val lastDismissed = dataStore.createValue<Instant?>("review.dismissedAt", null, json)
    val reviewedAt = dataStore.createValue<Instant?>("review.reviewedAt", null, json)

    companion object {
        internal val TAG = logTag("Review", "Settings", "Gplay")
    }
}

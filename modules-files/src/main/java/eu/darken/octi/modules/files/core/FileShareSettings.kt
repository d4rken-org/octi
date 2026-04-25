package eu.darken.octi.modules.files.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.datastore.createValue
import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.module.core.ModuleSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Tombstone for a file that's pending deletion across unavailable connectors.
 * [remainingConnectors] = empty means "unknown, try all configured connectors" (e.g., from migration).
 * Non-empty means "only try these specific connectors".
 */
@Serializable
data class PendingDelete(
    @SerialName("blobKey") val blobKey: String,
    @SerialName("remainingConnectors") val remainingConnectors: Set<String> = emptySet(),
    @Serializable(with = InstantSerializer::class)
    @SerialName("createdAt") val createdAt: Instant,
)

@Singleton
class FileShareSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) : ModuleSettings {

    private val Context.dataStore by preferencesDataStore(name = "module_files_settings")
    val dataStore: DataStore<Preferences> get() = context.dataStore

    override val isEnabled = dataStore.createValue("module.files.enabled", true)

    /**
     * Post an Android notification when a peer device shares a new file. Per-device diffs are
     * tracked in memory by [IncomingFileNotifier]; this setting only gates the notification
     * post — file syncing continues regardless.
     */
    val notifyOnIncoming = dataStore.createValue("module.files.notify.incoming", true)

    val isUsageHintDismissed = dataStore.createValue("module.files.hint.usage.dismissed", false)

    /**
     * Keyed by blobKey. Each entry is a tombstone recording which connectors still need the blob deleted.
     */
    val pendingDeletes = dataStore.createValue(
        key = "module.files.pending_deletes",
        defaultValue = emptyMap<String, PendingDelete>(),
        json = json,
        onErrorFallbackToDefault = true,
    )

    /**
     * Per-remote-device set of `blobKey`s the notifier has already observed. Persisted so that
     * notifications survive process death — without this, an app killed between a sync arrival and
     * the first emission after restart would silently re-seed and the missed share would never
     * notify. Keyed by `DeviceId.id`. Mutated only by [IncomingFileNotifier].
     */
    val seenByDevice = dataStore.createValue(
        key = "module.files.notifier.seen_by_device",
        defaultValue = emptyMap<String, Set<String>>(),
        json = json,
        onErrorFallbackToDefault = true,
    )
}

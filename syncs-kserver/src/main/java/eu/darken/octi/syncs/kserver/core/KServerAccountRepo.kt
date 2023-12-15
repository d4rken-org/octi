package eu.darken.octi.syncs.kserver.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KServerAccountRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val adapterCredentials by lazy { moshi.adapter<KServer.Credentials>() }

    private val Context.dataStore by preferencesDataStore(name = "syncs_kserver_credentials")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    private val _accounts = DynamicStateFlow(parentScope = scope + dispatcherProvider.Default) {
        dataStore.data.first()
            .asMap()
            .filter {
                if (!it.key.name.startsWith(KEY_PREFIX)) {
                    log(TAG, ERROR) { "Unknown entry: $it" }
                    return@filter false
                }
                if (it.value !is String) {
                    log(TAG, ERROR) { "Unknown data: $it" }
                    return@filter false
                }
                true
            }
            .map { it.value as String }
            .map { adapterCredentials.fromJson(it)!! }
            .toSet()
    }

    val accounts: Flow<Collection<KServer.Credentials>> = _accounts.flow


    suspend fun add(acc: KServer.Credentials): Boolean {
        log(TAG) { "add(acc=$acc)" }
        var added = false

        _accounts.updateBlocking {
            if (any { it.accountId == acc.accountId }) {
                log(TAG, WARN) { "Account $acc is already added" }
                return@updateBlocking this
            }

            added = true

            dataStore.edit {
                it[stringPreferencesKey("$KEY_PREFIX.${acc.accountId.id}")] = adapterCredentials.toJson(acc)
            }

            this + acc
        }
        return added
    }

    suspend fun remove(id: KServer.Credentials.AccountId) {
        log(TAG) { "remove(id=$id)" }
        _accounts.updateBlocking {
            val toRemove = firstOrNull { it.accountId == id }

            if (toRemove == null) {
                log(TAG, WARN) { "Account $id is unknown" }
                return@updateBlocking this
            }

            dataStore.edit {
                it.remove(stringPreferencesKey("$KEY_PREFIX.${id.id}"))
            }

            this - toRemove
        }
    }

    companion object {
        private const val KEY_PREFIX = "credentials"
        private val TAG = logTag("Sync", "KServer", "AccountRepo")
    }
}
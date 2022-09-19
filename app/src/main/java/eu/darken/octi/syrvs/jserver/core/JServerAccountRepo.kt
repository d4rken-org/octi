package eu.darken.octi.syrvs.jserver.core

import android.content.Context
import androidx.core.content.edit
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
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JServerAccountRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val adapterCredentials by lazy { moshi.adapter<JServer.Credentials>() }
    private val prefs = context.getSharedPreferences("syrv_jserver_credentials", Context.MODE_PRIVATE)

    private val _accounts = DynamicStateFlow(parentScope = scope + dispatcherProvider.Default) {
        prefs.all
            .filter {
                if (!it.key.startsWith(KEY_PREFIX)) {
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

    val accounts: Flow<Collection<JServer.Credentials>> = _accounts.flow


    suspend fun add(acc: JServer.Credentials): Boolean {
        log(TAG) { "add(acc=$acc)" }
        var added = false

        _accounts.updateBlocking {
            if (any { it.accountId == acc.accountId }) {
                log(TAG, WARN) { "Account $acc is already added" }
                return@updateBlocking this
            }

            added = true

            prefs.edit(commit = true) {
                putString(
                    "$KEY_PREFIX.${acc.accountId.id}",
                    adapterCredentials.toJson(acc)
                )
            }

            this + acc
        }
        return added
    }

    suspend fun remove(id: JServer.Credentials.AccountId) {
        log(TAG) { "remove(id=$id)" }
        _accounts.updateBlocking {
            val toRemove = firstOrNull { it.accountId == id }

            if (toRemove == null) {
                log(TAG, WARN) { "Account $id is unknown" }
                return@updateBlocking this
            }

            prefs.edit(commit = true) {
                remove("$KEY_PREFIX.${id.id}")
            }

            this - toRemove
        }
    }

    companion object {
        private const val KEY_PREFIX = "credentials"
        private val TAG = logTag("Sync", "JServer", "AccountRepo")
    }
}
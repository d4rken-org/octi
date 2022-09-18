package eu.darken.octi.sync.core.provider.gdrive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.collections.mutate
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAccountRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
) {

    private val defaultOptions: GoogleSignInOptions
        get() = GoogleSignInOptions.Builder().apply {
            requestId()
            requestEmail()
            requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        }.build()

    private fun createClient(account: GoogleAccount?): GoogleSignInClient {
        return GoogleSignIn.getClient(context, defaultOptions).also {
            log(TAG) { "Client created for $account: $it" }
        }
    }

    private val _accounts = DynamicStateFlow<Map<GoogleAccount.Id, GoogleClient>>(
        parentScope = scope + dispatcherProvider.IO, loggingTag = TAG
    ) {
        val cached = mutableMapOf<GoogleAccount.Id, GoogleClient>()
        GoogleSignIn.getLastSignedInAccount(context)
            ?.let { GoogleAccount(it) }
            ?.let { cached[it.id] = GoogleClient(account = it, client = createClient(it)) }
        cached
    }

    val accounts: Flow<Collection<GoogleClient>> = _accounts.flow.map { it.values }

    suspend fun add(acc: GoogleAccount): Boolean {
        log(TAG) { "add(account=$acc)" }
        var added = false

        _accounts.updateBlocking {
            if (containsKey(acc.id)) {
                log(TAG, WARN) { "Account $acc is already added" }
                return@updateBlocking this
            }

            added = true

            mutate {
                this[acc.id] = GoogleClient(
                    account = acc,
                    client = createClient(acc)
                )
            }
        }
        return added
    }

    suspend fun remove(id: GoogleAccount.Id) {
        log(TAG) { "remove(id=$id)" }
        _accounts.updateBlocking {

            val toRemove = get(id)

            if (toRemove == null) {
                log(TAG, WARN) { "Account $id is unknown" }
                return@updateBlocking this
            }

            toRemove.signOut()

            mutate { remove(toRemove.id) }
        }
    }

    fun startNewAuth(): Intent {
        log(TAG) { "startAuthFlow()" }
//        account.value?.let {
//            client.apply {
//                revokeAccess()
//                signOut()
//            }
//        }
//        account.value = null
        return createClient(account = null).signInIntent
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "AccountRepo")
    }
}
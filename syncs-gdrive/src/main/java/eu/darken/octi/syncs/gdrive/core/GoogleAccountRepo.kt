package eu.darken.octi.syncs.gdrive.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAccountRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    private val _accounts = DynamicStateFlow(parentScope = scope + dispatcherProvider.Default) {
        log(TAG, INFO) { "Initializing account store..." }

        val persisted = dataStore.data.first()
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
            .map { json.decodeFromString<GoogleAccount>(it) }
            .toSet()

        if (persisted.isNotEmpty()) {
            log(TAG, INFO) { "Loaded ${persisted.size} account(s) from DataStore" }
            return@DynamicStateFlow persisted
        }

        log(TAG, INFO) { "No persisted accounts, attempting migration from GoogleSignIn..." }

        // One-time migration from deprecated GoogleSignIn
        val migrated = migrateFromGoogleSignIn()
        if (migrated != null) {
            dataStore.edit {
                it[stringPreferencesKey("$KEY_PREFIX.${migrated.accountId}")] = json.encodeToString(migrated)
            }
            log(TAG, INFO) { "Migration successful: accountId=${migrated.accountId}" }
            setOf(migrated)
        } else {
            log(TAG, INFO) { "No legacy GoogleSignIn account found, starting fresh" }
            emptySet()
        }
    }

    val accounts: Flow<Collection<GoogleAccount>> = _accounts.flow

    sealed class AuthAction {
        data class AlreadyAuthorized(val account: GoogleAccount) : AuthAction()
        data class NeedsConsent(val pendingIntent: PendingIntent) : AuthAction()
    }

    suspend fun startNewAuth(): AuthAction {
        log(TAG) { "startNewAuth()" }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(SCOPE_DRIVE_APPDATA, SCOPE_EMAIL, SCOPE_OPENID))
            .build()

        val result = Identity.getAuthorizationClient(context).authorize(request).await()

        return if (result.hasResolution()) {
            log(TAG) { "startNewAuth(): Needs user consent" }
            val pendingIntent = result.pendingIntent
                ?: throw IllegalStateException("hasResolution() is true but pendingIntent is null")
            AuthAction.NeedsConsent(pendingIntent)
        } else {
            log(TAG) { "startNewAuth(): Already authorized" }
            val account = resolveAccountIdentity(result)
            AuthAction.AlreadyAuthorized(account)
        }
    }

    suspend fun handleAuthResult(intent: Intent): GoogleAccount {
        log(TAG) { "handleAuthResult()" }
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        return resolveAccountIdentity(result)
    }

    private suspend fun resolveAccountIdentity(result: AuthorizationResult): GoogleAccount {
        val accessToken = result.accessToken
            ?: throw IllegalStateException("No access token in AuthorizationResult")

        val hasAppDataScope = result.grantedScopes.any { it.toString() == DriveScopes.DRIVE_APPDATA }
        if (!hasAppDataScope) {
            throw ScopeNotGrantedException("DRIVE_APPDATA")
        }

        return withContext(dispatcherProvider.IO) {
            log(TAG) { "resolveAccountIdentity(): Calling userinfo API" }
            val url = URL(USERINFO_URL)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                if (conn.responseCode != 200) {
                    val errorBody = try {
                        conn.errorStream?.bufferedReader()?.readText()
                    } catch (_: Exception) {
                        null
                    }
                    throw IllegalStateException("Userinfo API returned ${conn.responseCode}: $errorBody")
                }

                val body = conn.inputStream.use { it.bufferedReader().readText() }
                val jsonObj = json.parseToJsonElement(body).jsonObject
                val sub = jsonObj["sub"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("No 'sub' in userinfo response")
                val email = jsonObj["email"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("No 'email' in userinfo response")

                GoogleAccount(accountId = sub, email = email).also {
                    log(TAG) { "resolveAccountIdentity(): Resolved accountId=$sub" }
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun add(acc: GoogleAccount): Boolean {
        log(TAG) { "add(accountId=${acc.accountId})" }
        var added = false

        _accounts.updateBlocking {
            if (any { it.accountId == acc.accountId }) {
                log(TAG, WARN) { "Account ${acc.accountId} is already added" }
                return@updateBlocking this
            }

            added = true

            dataStore.edit {
                it[stringPreferencesKey("$KEY_PREFIX.${acc.accountId}")] = json.encodeToString(acc)
            }

            this + acc
        }
        return added
    }

    suspend fun remove(id: GoogleAccount.Id) {
        log(TAG) { "remove(id=$id)" }
        _accounts.updateBlocking {
            val toRemove = firstOrNull { it.id == id }

            if (toRemove == null) {
                log(TAG, WARN) { "Account $id is unknown" }
                return@updateBlocking this
            }

            // Drive scope revocation is not critical — the user can revoke via Google Account settings.
            // AuthorizationClient doesn't provide a revoke method; we just clean up locally.

            dataStore.edit {
                it.remove(stringPreferencesKey("$KEY_PREFIX.${toRemove.accountId}"))
            }

            this - toRemove
        }
    }

    @Suppress("DEPRECATION")
    private fun migrateFromGoogleSignIn(): GoogleAccount? {
        return try {
            val oldAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return null
            val accountId = oldAccount.id ?: return null
            val email = oldAccount.email ?: return null
            log(TAG, INFO) { "Found legacy GoogleSignIn account: accountId=$accountId" }
            GoogleAccount(accountId = accountId, email = email)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Migration from GoogleSignIn failed: ${e.asLog()}" }
            null
        }
    }

    companion object {
        private const val DATASTORE_NAME = "syncs_gdrive_accounts"
        private const val KEY_PREFIX = "credentials"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        private val SCOPE_DRIVE_APPDATA = Scope(DriveScopes.DRIVE_APPDATA)
        private val SCOPE_EMAIL = Scope("email")
        private val SCOPE_OPENID = Scope("openid")
        private val TAG = logTag("Sync", "GDrive", "AccountRepo")
    }
}

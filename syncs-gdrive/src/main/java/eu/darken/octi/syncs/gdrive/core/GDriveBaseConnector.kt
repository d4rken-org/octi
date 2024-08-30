package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.withContext

abstract class GDriveBaseConnector(
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
    private val client: GoogleClient,
    private val scopes: List<String> = listOf(DriveScopes.DRIVE_APPDATA),
) {

    private val gdrive: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes).apply {
            selectedAccount = client.account.signInAccount.account
        }
        Drive.Builder(NetHttpTransport(), GsonFactory(), credential).apply {
            applicationName = context.getString(eu.darken.octi.common.R.string.app_name)
        }.build()
    }

    val account: GoogleAccount
        get() = client.account

    internal suspend fun <R> withDrive(action: suspend GDriveEnvironment.() -> R): R {
        val env = object : GDriveEnvironment {
            override val drive: Drive
                get() = gdrive
        }
        return withContext(dispatcherProvider.IO) { action(env) }
    }

    companion object {
        internal val TAG = logTag("Sync", "GDrive", "Connector", "Base")
    }
}


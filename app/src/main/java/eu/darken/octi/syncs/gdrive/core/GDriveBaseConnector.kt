package eu.darken.octi.syncs.gdrive.core

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import eu.darken.octi.R
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import com.google.api.services.drive.model.File as GDriveFile


@Suppress("BlockingMethodInNonBlockingContext")
abstract class GDriveBaseConnector constructor(
    private val context: Context,
    private val client: GoogleClient,
    private val scopes: List<String> = listOf(DriveScopes.DRIVE_APPDATA),
) {

    internal val gdrive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes).apply {
            selectedAccount = client.account.signInAccount.account
        }
        Drive.Builder(NetHttpTransport(), GsonFactory(), credential).apply {
            applicationName = context.getString(R.string.app_name)
        }.build()
    }

    val account: GoogleAccount
        get() = client.account

    fun appDataRoot(): GDriveFile = gdrive.files()
        .get(APPDATAFOLDER).execute()

    val GDriveFile.isDirectory: Boolean
        get() = mimeType == MIME_FOLDER

    fun GDriveFile.listFiles(): Collection<GDriveFile> = gdrive.files()
        .list().apply {
            spaces = APPDATAFOLDER
            q = " '${id}' in parents "
            fields = "files(id,name,mimeType,createdTime,modifiedTime,size)"
        }
        .execute().files

    fun GDriveFile.child(name: String): GDriveFile? = gdrive.files()
        .list().apply {
            spaces = APPDATAFOLDER
            q = " '${id}' in parents and name = '$name' "
            fields = "files(id,name,mimeType,createdTime,modifiedTime,size)"
        }
        .execute().files
        .let {
            when {
                it.size > 1 -> throw IllegalStateException("Multiple folders with the same name.")
                it.isEmpty() -> null
                else -> it.single()
            }
        }

    fun GDriveFile.createDir(folderName: String): GDriveFile {
        log(TAG, VERBOSE) { "createDir(): $name/$folderName" }
        val metaData = GDriveFile().apply {
            name = folderName
            mimeType = MIME_FOLDER
            parents = listOf(this@createDir.id)
        }
        return gdrive.files().create(metaData).execute()
    }

    fun GDriveFile.createFile(fileName: String): GDriveFile {
        log(TAG, VERBOSE) { "createFile(): $name/$fileName" }
        val metaData = GDriveFile().apply {
            name = fileName
            parents = listOf(this@createFile.id)
            modifiedTime = DateTime(System.currentTimeMillis())
        }
        return gdrive.files().create(metaData).execute()
    }

    fun GDriveFile.writeData(toWrite: ByteString) {
        log(TAG, VERBOSE) { "writeData($name): $toWrite" }

        val payload = ByteArrayContent("application/octet-stream", toWrite.toByteArray())
        val writeMetaData = GDriveFile().apply {
            mimeType = "application/octet-stream"
            modifiedTime = DateTime(System.currentTimeMillis())
        }
        gdrive.files().update(id, writeMetaData, payload).execute().also {
            log(TAG, VERBOSE) { "writeData($name): done: $it" }
        }
    }

    fun GDriveFile.readData(): ByteString? {
        log(TAG, VERBOSE) { "readData($name)" }

        val readData = gdrive.files().get(id)
            ?.let { get ->
                val buffer = ByteArrayOutputStream()
                buffer.use { get.executeMediaAndDownloadTo(it) }
                buffer.toByteArray().toByteString()
            }

        log(TAG, VERBOSE) { "readData($name) done: $readData" }
        return readData
    }

    fun GDriveFile.deleteAll() {
        log(TAG, VERBOSE) { "deleteAll(): $name ($id)" }

        gdrive.files().delete(id).execute().also {
            log(TAG, VERBOSE) { "deleteAll(): $name ($id) done!" }
        }
    }

    companion object {
        internal const val APPDATAFOLDER = "appDataFolder"
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
        private val TAG = logTag("Sync", "GDrive", "Connector", "Browser")
    }
}
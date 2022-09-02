package eu.darken.octi.sync.core.provider.gdrive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.R
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.sync.core.Sync
import eu.darken.octi.sync.core.SyncDeviceId
import eu.darken.octi.sync.core.SyncOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import com.google.api.services.drive.model.File as GDriveFile


@Suppress("BlockingMethodInNonBlockingContext") class GDriveAppDataConnector @AssistedInject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    @Assisted private val client: GoogleClient,
    private val syncOptions: SyncOptions,
) : Sync.Connector {

    private val gdrive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA)).apply {
            selectedAccount = client.account.signInAccount.account
        }
        Drive.Builder(NetHttpTransport(), GsonFactory(), credential).apply {
            applicationName = context.getString(R.string.app_name)
        }.build()
    }

    data class State(
        override val isSyncing: Boolean = false,
        override val lastSyncAt: Instant? = null,
        override val devices: Collection<Sync.Data.Device> = emptyList(),
    ) : Sync.Data, Sync.Connector.State

    private val _state = DynamicStateFlow<State>(
        parentScope = scope + dispatcherProvider.IO,
        loggingTag = TAG,
    ) {
        State()
    }

    override val state = _state.flow

    val account: GoogleAccount
        get() = client.account

    override suspend fun sync() {
        log(TAG) { "sync()" }
        var wasAlreadySyncing = false
        _state.updateBlocking {
            wasAlreadySyncing = isSyncing
            State(isSyncing = true)
        }
        if (wasAlreadySyncing) {
            log(TAG, WARN) { "Sync already in progress, skipping." }
            return
        }

        write(object : Sync.Data.Device {
            override val deviceId: SyncDeviceId
                get() = syncOptions.syncDeviceId
            override val lastUpdatedAt: Instant
                get() = Instant.now()
            override val payload: String
                get() = "Test123ABC"

        })
        val devices = read()

        _state.updateBlocking {
            log(TAG) { "sync() finished" }
            State(
                isSyncing = false,
                lastSyncAt = Instant.now(),
                devices = devices,
            )
        }
    }

    private suspend fun read(): Collection<GDriveDeviceData> = withContext(dispatcherProvider.IO) {
        val userDir = getOrCreateUserDir()

        val deviceFiles = userDir.listFiles()
        log(TAG) { "read(): GDrive files are:\n${deviceFiles.joinToString("\n")}" }

        deviceFiles
            .filter { !it.isDirectory }
            .mapNotNull { deviceFile ->
                if (deviceFile.isDirectory) return@mapNotNull null
                val payload = gdrive.files().get(deviceFile.id)?.let { get ->
                    ByteArrayOutputStream()
                        .apply { use { get.executeMediaAndDownloadTo(it) } }
                        .let { String(it.toByteArray()) }
                }
                if (payload == null) {
                    log(TAG, WARN) { "read(): Device file is empty: ${deviceFile.name}" }
                    return@mapNotNull null
                } else {
                    log(TAG) { "read(): ${deviceFile.name} has ${payload.length} bytes" }
                }

                GDriveDeviceData(
                    deviceId = SyncDeviceId(deviceFile.name),
                    lastUpdatedAt = Instant.ofEpochMilli(deviceFile.modifiedTime.value),
                    payload = payload,
                )
            }
            .also {
                log(TAG) { "read(): Device data is:\n${it.joinToString("\n")}" }
            }
    }

    private suspend fun write(data: Sync.Data.Device) {
        log(TAG) { "write(): $data)" }

        val userDir = getOrCreateUserDir()

        val deviceFile = userDir.child(data.deviceId.id) ?: userDir.createFile(data.deviceId.id).also {
            log(TAG) { "write(): Created device file $it" }
        }

        deviceFile.writeData(data.payload)
        log(TAG) { "write(): Done" }
    }

    private fun getOrCreateUserDir(): GDriveFile {
        val userDir = gdrive.appDataRoot().child(syncOptions.syncUserId.id)
        if (userDir != null) return userDir
        return gdrive.appDataRoot().createDir(folderName = syncOptions.syncUserId.id).also {
            log(TAG) { "write(): Created user dir $it" }
        }
    }

    private fun Drive.appDataRoot() = files()
        .get(APPDATAFOLDER).execute()

    private val GDriveFile.isDirectory: Boolean
        get() = mimeType == MIME_FOLDER

    private fun GDriveFile.listFiles(): Collection<GDriveFile> = gdrive.files()
        .list().apply {
            spaces = APPDATAFOLDER
            q = "'${id}' in parents"
            fields = "files(id,name,createdTime,modifiedTime,size)"
        }
        .execute().files

    private fun GDriveFile.child(name: String): GDriveFile? = gdrive.files()
        .list().apply {
            spaces = APPDATAFOLDER
            q = "'${id}' in parents and name = '$name'"
            fields = "files(id,name,createdTime,modifiedTime,size)"
        }
        .execute().files
        .singleOrNull()

    private fun GDriveFile.createDir(folderName: String): GDriveFile {
        val metaData = GDriveFile().apply {
            name = folderName
            mimeType = MIME_FOLDER
            parents = listOf(this@createDir.id)
        }
        return gdrive.files().create(metaData).execute()
    }

    private fun GDriveFile.createFile(fileName: String): GDriveFile {
        val metaData = GDriveFile().apply {
            name = fileName
            parents = listOf(this@createFile.id)
            modifiedTime = DateTime(System.currentTimeMillis())
        }
        return gdrive.files().create(metaData).execute()
    }

    private fun GDriveFile.writeData(toWrite: String) {
        log(TAG, VERBOSE) { "writeData($name): $toWrite" }
        val payload = ByteArrayContent.fromString("text/plain", toWrite)
        val writeMetaData = GDriveFile().apply {
            mimeType = "text/plain"
            modifiedTime = DateTime(System.currentTimeMillis())
        }
        gdrive.files().update(id, writeMetaData, payload).execute().also {
            log(TAG, VERBOSE) { "writeData($name): done: $it" }
        }
    }

    private fun GDriveFile.readData(): String {
        log(TAG) { "readData($name)" }
        val readData = ByteArrayOutputStream()
            .apply { use { gdrive.files().get(id).executeMediaAndDownloadTo(it) } }
            .let { String(it.toByteArray()) }
        log(TAG) { "readData($name) done: $readData" }
        return readData
    }

    @AssistedFactory
    interface Factory {
        fun create(client: GoogleClient): GDriveAppDataConnector
    }

    companion object {
        private const val APPDATAFOLDER = "appDataFolder"
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
        private val TAG = logTag("Sync", "GDrive", "Connector")
    }
}
package eu.darken.octi.syncs.gdrive.core

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import com.google.api.services.drive.model.File as GDriveFile

interface GDriveEnvironment {
    val drive: Drive

    val GDriveEnvironment.appDataRoot: GDriveFile
        get() = drive.files().get(APPDATAFOLDER).execute()

    val GDriveFile.isDirectory: Boolean
        get() = mimeType == MIME_FOLDER

    suspend fun GDriveFile.listFiles(): Collection<GDriveFile> {
        return drive.files()
            .list().apply {
                spaces = APPDATAFOLDER
                q = " '${id}' in parents "
                fields = "files(id,name,mimeType,createdTime,modifiedTime,size)"
            }
            .execute().files
    }

    suspend fun GDriveFile.child(name: String): GDriveFile? {
        return drive.files()
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
    }

    suspend fun GDriveFile.createDir(folderName: String): GDriveFile {
        log(TAG, VERBOSE) { "createDir(): $name/$folderName" }
        val metaData = GDriveFile().apply {
            name = folderName
            mimeType = MIME_FOLDER
            parents = listOf(this@createDir.id)
        }
        return drive.files().create(metaData).execute()
    }

    suspend fun GDriveFile.createFile(fileName: String): GDriveFile {
        log(TAG, VERBOSE) { "createFile(): $name/$fileName" }
        val metaData = GDriveFile().apply {
            name = fileName
            parents = listOf(this@createFile.id)
            modifiedTime = DateTime(System.currentTimeMillis())
        }
        return drive.files().create(metaData).execute()
    }

    suspend fun GDriveFile.writeData(toWrite: ByteString) {
        log(TAG, VERBOSE) { "writeData($name): $toWrite" }

        val payload = ByteArrayContent("application/octet-stream", toWrite.toByteArray())
        val writeMetaData = GDriveFile().apply {
            mimeType = "application/octet-stream"
            modifiedTime = DateTime(System.currentTimeMillis())
        }
        drive.files().update(id, writeMetaData, payload).execute().also {
            log(TAG, VERBOSE) { "writeData($name): done: $it" }
        }
    }

    suspend fun GDriveFile.readData(): ByteString? {
        log(TAG, VERBOSE) { "readData($name)" }

        val readData = drive.files().get(id)
            ?.let { get ->
                val buffer = ByteArrayOutputStream()
                buffer.use { get.executeMediaAndDownloadTo(it) }
                buffer.toByteArray().toByteString()
            }

        log(TAG, VERBOSE) { "readData($name) done: $readData" }
        return readData
    }

    suspend fun GDriveFile.deleteAll() {
        log(TAG, VERBOSE) { "deleteAll(): $name ($id)" }

        drive.files().delete(id).execute().also {
            log(TAG, VERBOSE) { "deleteAll(): $name ($id) done!" }
        }
    }

    companion object {
        internal const val APPDATAFOLDER = "appDataFolder"
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
        internal val TAG = logTag("Sync", "GDrive", "Connector", "Env")
    }
}
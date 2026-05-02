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
import kotlin.time.Clock
import com.google.api.services.drive.model.File as GDriveFile

interface GDriveEnvironment {
    val drive: Drive

    val GDriveEnvironment.appDataRoot: GDriveFile
        get() = drive.files().get(APPDATAFOLDER)
            .setFields("id,name,mimeType")
            .execute()

    val GDriveFile.isDirectory: Boolean
        get() = mimeType == MIME_FOLDER

    suspend fun GDriveFile.listFiles(): Collection<GDriveFile> {
        val files = mutableListOf<GDriveFile>()
        var pageToken: String? = null
        do {
            val result = drive.files()
                .list().apply {
                    spaces = APPDATAFOLDER
                    q = " '${id}' in parents "
                    fields = FILE_LIST_FIELDS
                    this.pageToken = pageToken
                }
                .execute()
            files += result.files ?: emptyList()
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return files
    }

    suspend fun GDriveFile.child(name: String): GDriveFile? {
        return drive.files()
            .list().apply {
                spaces = APPDATAFOLDER
                q = " '${id}' in parents and name = '$name' "
                fields = "files($FILE_FIELDS)"
            }
            .execute().files.orEmpty()
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
        return drive.files().create(metaData)
            .setFields(FILE_FIELDS)
            .execute()
    }

    suspend fun GDriveFile.createFile(fileName: String): GDriveFile {
        log(TAG, VERBOSE) { "createFile(): $name/$fileName" }
        val metaData = GDriveFile().apply {
            name = fileName
            parents = listOf(this@createFile.id)
            modifiedTime = DateTime(Clock.System.now().toEpochMilliseconds())
        }
        return drive.files().create(metaData)
            .setFields(FILE_FIELDS)
            .execute()
    }

    suspend fun GDriveFile.writeData(toWrite: ByteString) {
        log(TAG, VERBOSE) { "writeData($name): $toWrite" }

        val payload = ByteArrayContent("application/octet-stream", toWrite.toByteArray())
        val writeMetaData = GDriveFile().apply {
            mimeType = "application/octet-stream"
            modifiedTime = DateTime(Clock.System.now().toEpochMilliseconds())
        }
        drive.files().update(id, writeMetaData, payload)
            .setFields(FILE_FIELDS)
            .execute()
            .also {
                log(TAG, VERBOSE) { "writeData($name): done: $it" }
            }
    }

    suspend fun listAppDataFiles(): List<GDriveFile> {
        val files = mutableListOf<GDriveFile>()
        var pageToken: String? = null
        do {
            val result = drive.files().list().apply {
                spaces = APPDATAFOLDER
                fields = FILE_LIST_FIELDS
                this.pageToken = pageToken
            }.execute()
            files += result.files ?: emptyList()
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return files
    }

    suspend fun getFileMetadata(
        fileId: String,
        fields: String = "id,name,parents,modifiedTime",
    ): GDriveFile {
        log(TAG, VERBOSE) { "getFileMetadata($fileId)" }
        return drive.files().get(fileId).setFields(fields).execute()
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

    suspend fun GDriveFile.writeStreamed(
        input: java.io.InputStream,
        sizeBytes: Long,
        mimeType: String,
        properties: Map<String, String> = emptyMap(),
    ) {
        log(TAG, VERBOSE) { "writeStreamed($name, $sizeBytes bytes, $mimeType)" }
        val content = com.google.api.client.http.InputStreamContent(mimeType, input).apply {
            length = sizeBytes
        }
        val metadata = GDriveFile().apply {
            this.mimeType = mimeType
            modifiedTime = DateTime(Clock.System.now().toEpochMilliseconds())
            if (properties.isNotEmpty()) {
                this.properties = properties
            }
        }
        drive.files().update(id, metadata, content)
            .setFields(FILE_FIELDS)
            .execute()
    }

    suspend fun GDriveFile.readStreamedTo(output: java.io.OutputStream) {
        log(TAG, VERBOSE) { "readStreamedTo($name)" }
        drive.files().get(id).executeMediaAndDownloadTo(output)
    }

    suspend fun GDriveFile.fetchBlobMetadata(): GDriveFile {
        return drive.files().get(id).setFields("size,createdTime").execute()
    }

    companion object {
        internal const val APPDATAFOLDER = "appDataFolder"
        internal const val MIME_FOLDER = "application/vnd.google-apps.folder"
        private const val FILE_FIELDS = "id,name,mimeType,parents,createdTime,modifiedTime,size"
        private const val FILE_LIST_FIELDS = "nextPageToken,files($FILE_FIELDS)"
        internal val TAG = logTag("Sync", "GDrive", "Connector", "Env")
    }
}

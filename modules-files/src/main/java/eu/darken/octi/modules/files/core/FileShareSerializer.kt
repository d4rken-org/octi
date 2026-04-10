package eu.darken.octi.modules.files.core

import dagger.Reusable
import eu.darken.octi.module.core.ModuleSerializer
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.toByteString
import javax.inject.Inject

@Reusable
class FileShareSerializer @Inject constructor(
    private val json: Json,
) : ModuleSerializer<FileShareInfo> {

    override fun serialize(item: FileShareInfo): ByteString {
        val jsonString = json.encodeToString(FileShareInfo.serializer(), item)
        return jsonString.encodeToByteArray().toByteString()
    }

    override fun deserialize(raw: ByteString): FileShareInfo {
        return json.decodeFromString(FileShareInfo.serializer(), raw.utf8())
    }
}

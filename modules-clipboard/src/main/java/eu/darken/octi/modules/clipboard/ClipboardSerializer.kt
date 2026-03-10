package eu.darken.octi.modules.clipboard

import dagger.Reusable
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleSerializer
import kotlinx.serialization.json.Json
import okio.ByteString
import javax.inject.Inject

@Reusable
class ClipboardSerializer @Inject constructor(
    private val json: Json,
) : ModuleSerializer<ClipboardInfo> {

    override fun serialize(item: ClipboardInfo): ByteString = json.toByteString(item)

    override fun deserialize(raw: ByteString): ClipboardInfo = json.fromJson(raw)

    companion object {
        val TAG = logTag("Module", "Clipboard", "Serializer")
    }
}

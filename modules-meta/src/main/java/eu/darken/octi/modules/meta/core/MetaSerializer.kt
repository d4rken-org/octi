package eu.darken.octi.modules.meta.core

import dagger.Reusable
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleSerializer
import kotlinx.serialization.json.Json
import okio.ByteString
import javax.inject.Inject

@Reusable
class MetaSerializer @Inject constructor(
    private val json: Json,
) : ModuleSerializer<MetaInfo> {

    override fun serialize(item: MetaInfo): ByteString = json.toByteString(item)

    override fun deserialize(raw: ByteString): MetaInfo = json.fromJson(raw)

    companion object {
        val TAG = logTag("Module", "Meta", "Serializer")
    }
}

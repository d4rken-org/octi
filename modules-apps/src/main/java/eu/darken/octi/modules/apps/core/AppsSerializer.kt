package eu.darken.octi.modules.apps.core

import dagger.Reusable
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleSerializer
import kotlinx.serialization.json.Json
import okio.ByteString
import javax.inject.Inject

@Reusable
class AppsSerializer @Inject constructor(
    private val json: Json,
) : ModuleSerializer<AppsInfo> {

    override fun serialize(item: AppsInfo): ByteString = json.toByteString(item)

    override fun deserialize(raw: ByteString): AppsInfo = json.fromJson(raw)

    companion object {
        val TAG = logTag("Module", "Apps", "Serializer")
    }
}

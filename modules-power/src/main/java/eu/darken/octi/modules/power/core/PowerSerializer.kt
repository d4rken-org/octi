package eu.darken.octi.modules.power.core

import dagger.Reusable
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleSerializer
import kotlinx.serialization.json.Json
import okio.ByteString
import javax.inject.Inject

@Reusable
class PowerSerializer @Inject constructor(
    private val json: Json,
) : ModuleSerializer<PowerInfo> {

    override fun serialize(item: PowerInfo): ByteString = json.toByteString(item)

    override fun deserialize(raw: ByteString): PowerInfo = json.fromJson(raw)

    companion object {
        val TAG = logTag("Module", "Power", "Serializer")
    }
}

package eu.darken.octi.modules.wifi.core

import dagger.Reusable
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleSerializer
import kotlinx.serialization.json.Json
import okio.ByteString
import javax.inject.Inject

@Reusable
class WifiSerializer @Inject constructor(
    private val json: Json,
) : ModuleSerializer<WifiInfo> {

    override fun serialize(item: WifiInfo): ByteString = json.toByteString(item)

    override fun deserialize(raw: ByteString): WifiInfo = json.fromJson(raw)

    companion object {
        val TAG = logTag("Module", "Wifi", "Serializer")
    }
}

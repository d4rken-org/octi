package eu.darken.octi.modules.connectivity.core

import dagger.Reusable
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleSerializer
import kotlinx.serialization.json.Json
import okio.ByteString
import javax.inject.Inject

@Reusable
class ConnectivitySerializer @Inject constructor(
    private val json: Json,
) : ModuleSerializer<ConnectivityInfo> {

    override fun serialize(item: ConnectivityInfo): ByteString = json.toByteString(item)

    override fun deserialize(raw: ByteString): ConnectivityInfo = json.fromJson(raw)

    companion object {
        val TAG = logTag("Module", "Connectivity", "Serializer")
    }
}

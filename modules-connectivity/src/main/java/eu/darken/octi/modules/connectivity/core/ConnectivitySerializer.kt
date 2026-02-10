package eu.darken.octi.modules.connectivity.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.Reusable
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.common.serialization.toByteString
import eu.darken.octi.module.core.ModuleSerializer
import okio.ByteString
import javax.inject.Inject

@Reusable
class ConnectivitySerializer @Inject constructor(
    private val moshi: Moshi,
) : ModuleSerializer<ConnectivityInfo> {

    private val adapter by lazy { moshi.adapter<ConnectivityInfo>() }

    override fun serialize(item: ConnectivityInfo): ByteString = adapter.toByteString(item)

    override fun deserialize(raw: ByteString): ConnectivityInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Connectivity", "Serializer")
    }
}

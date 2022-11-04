package eu.darken.octi.modules.wifi.core

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
class WifiSerializer @Inject constructor(
    private val moshi: Moshi,
) : ModuleSerializer<WifiInfo> {

    private val adapter by lazy { moshi.adapter<WifiInfo>() }

    override fun serialize(item: WifiInfo): ByteString = adapter.toByteString(item)

    override fun deserialize(raw: ByteString): WifiInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Wifi", "Serializer")
    }
}
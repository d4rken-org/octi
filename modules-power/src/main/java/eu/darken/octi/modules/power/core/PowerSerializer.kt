package eu.darken.octi.modules.power.core

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
class PowerSerializer @Inject constructor(
    private val moshi: Moshi,
) : ModuleSerializer<PowerInfo> {

    private val adapter by lazy { moshi.adapter<PowerInfo>() }

    override fun serialize(item: PowerInfo): ByteString = adapter.toByteString(item)

    override fun deserialize(raw: ByteString): PowerInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Power", "Serializer")
    }
}
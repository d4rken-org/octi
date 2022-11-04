package eu.darken.octi.modules.meta.core

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
class MetaSerializer @Inject constructor(
    private val moshi: Moshi,
) : ModuleSerializer<MetaInfo> {

    private val adapter by lazy { moshi.adapter<MetaInfo>() }

    override fun serialize(item: MetaInfo): ByteString = adapter.toByteString(item)

    override fun deserialize(raw: ByteString): MetaInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Meta", "Serializer")
    }
}
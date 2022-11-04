package eu.darken.octi.modules.apps.core

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
class AppsSerializer @Inject constructor(
    private val moshi: Moshi,
) : ModuleSerializer<AppsInfo> {

    private val adapter by lazy { moshi.adapter<AppsInfo>() }

    override fun serialize(item: AppsInfo): ByteString = adapter.toByteString(item)

    override fun deserialize(raw: ByteString): AppsInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Apps", "Serializer")
    }
}
package eu.darken.octi.modules.clipboard

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
class ClipboardSerializer @Inject constructor(
    private val moshi: Moshi,
) : ModuleSerializer<ClipboardInfo> {

    private val adapter by lazy { moshi.adapter<ClipboardInfo>() }

    override fun serialize(item: ClipboardInfo): ByteString = adapter.toByteString(item)

    override fun deserialize(raw: ByteString): ClipboardInfo = adapter.fromJson(raw)!!

    companion object {
        val TAG = logTag("Module", "Clipboard", "Serializer")
    }
}
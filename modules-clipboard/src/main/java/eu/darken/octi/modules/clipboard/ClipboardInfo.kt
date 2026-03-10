@file:UseSerializers(ByteStringSerializer::class)

package eu.darken.octi.modules.clipboard

import eu.darken.octi.common.serialization.serializer.ByteStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import okio.ByteString

@Serializable
data class ClipboardInfo(
    @SerialName("type") val type: Type = Type.EMPTY,
    @SerialName("data") val data: ByteString = ByteString.EMPTY,
) {

    init {
        if (data.size > 32 * 1024) throw java.lang.IllegalArgumentException("Size limit exceeded (>32KB)")
    }

    @Serializable
    enum class Type {
        @SerialName("EMPTY") EMPTY,
        @SerialName("SIMPLE_TEXT") SIMPLE_TEXT,
        ;
    }

}

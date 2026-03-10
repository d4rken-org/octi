package eu.darken.octi.common.serialization.serializer

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("android.net.Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Uri = Uri.parse(decoder.decodeString())
}

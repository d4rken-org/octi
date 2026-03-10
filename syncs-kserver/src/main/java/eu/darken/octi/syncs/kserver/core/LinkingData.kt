@file:UseSerializers(ByteStringSerializer::class)

package eu.darken.octi.syncs.kserver.core

import android.os.Parcelable
import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toGzip
import eu.darken.octi.common.serialization.serializer.ByteStringSerializer
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

@Serializable
@Parcelize
data class LinkingData(
    @SerialName("serverAddress") val serverAdress: KServer.Address,
    @SerialName("shareCode") val linkCode: KServer.Credentials.LinkCode,
    @SerialName("encryptionKeySet") val encryptionKeyset: PayloadEncryption.KeySet,
) : Parcelable {

    fun toEncodedString(json: Json): String = json.encodeToString(this)
        .toByteArray()
        .toByteString()
        .toGzip()
        .base64()

    companion object {
        fun fromEncodedString(json: Json, encoded: String): LinkingData = encoded
            .decodeBase64()!!
            .fromGzip()
            .let { json.decodeFromString<LinkingData>(it.utf8()) }
    }
}

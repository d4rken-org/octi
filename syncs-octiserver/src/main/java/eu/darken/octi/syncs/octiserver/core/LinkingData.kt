package eu.darken.octi.syncs.octiserver.core

import android.os.Parcelable
import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toGzip
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

@Serializable
@Parcelize
data class LinkingData(
    @SerialName("serverAddress") val serverAdress: OctiServer.Address,
    @SerialName("shareCode") val linkCode: OctiServer.Credentials.LinkCode,
    @SerialName("encryptionKeySet") val encryptionKeyset: PayloadEncryption.KeySet,
) : Parcelable {

    fun toEncodedString(json: Json): String = json.encodeToString(this)
        .toByteArray()
        .toByteString()
        .toGzip()
        .base64()

    companion object {
        /**
         * @throws InvalidLinkCodeException if [encoded] is not a complete, unaltered link code.
         * Surrounding whitespace is tolerated: share targets and clipboards routinely add it.
         */
        fun fromEncodedString(json: Json, encoded: String): LinkingData {
            // Staged rather than one try/catch: which stage failed is the whole diagnostic, and it
            // is the only part of the failure that is safe to log.
            val compressed = try {
                encoded.trim().decodeBase64() ?: throw IllegalArgumentException("Not valid base64")
            } catch (e: Exception) {
                throw InvalidLinkCodeException(InvalidLinkCodeException.Stage.BASE64, e)
            }

            val plaintext = try {
                compressed.fromGzip()
            } catch (e: Exception) {
                throw InvalidLinkCodeException(InvalidLinkCodeException.Stage.GZIP, e)
            }

            return try {
                json.decodeFromString<LinkingData>(plaintext.utf8())
            } catch (e: Exception) {
                throw InvalidLinkCodeException(InvalidLinkCodeException.Stage.JSON, e)
            }
        }
    }
}

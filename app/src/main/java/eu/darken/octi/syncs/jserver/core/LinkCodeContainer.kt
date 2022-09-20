package eu.darken.octi.syncs.jserver.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.sync.core.SyncDeviceId
import kotlinx.parcelize.Parcelize
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

@JsonClass(generateAdapter = true)
@Parcelize
data class LinkCodeContainer(
    @Json(name = "accountId") val accountId: JServer.Credentials.AccountId,
    @Json(name = "deviceId") val deviceId: SyncDeviceId,
    @Json(name = "shareCode") val linkCode: JServer.Credentials.LinkCode,
) : Parcelable {

    fun toEncodedString(moshi: Moshi): String {
        val adapter = moshi.adapter<LinkCodeContainer>()
        return adapter
            .toJson(this)
            .toByteArray()
            .toByteString()
            .base64()
    }

    companion object {
        fun fromEncodedString(moshi: Moshi, encoded: String): LinkCodeContainer {
            val adapter = moshi.adapter<LinkCodeContainer>()
            return encoded
                .decodeBase64()!!
                .let { adapter.fromJson(it)!! }
        }
    }
}
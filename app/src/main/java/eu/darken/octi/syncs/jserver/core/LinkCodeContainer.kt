package eu.darken.octi.syncs.jserver.core

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.serialization.fromJson
import eu.darken.octi.sync.core.DeviceId
import kotlinx.parcelize.Parcelize
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

@JsonClass(generateAdapter = true)
@Parcelize
data class LinkCodeContainer(
    @Json(name = "serverAddress") val serverAdress: JServer.Address,
    @Json(name = "accountId") val accountId: JServer.Credentials.AccountId,
    @Json(name = "devicePassword") val devicePassword: JServer.Credentials.DevicePassword,
    @Json(name = "fromDeviceId") val fromDeviceId: DeviceId,
    @Json(name = "shareCode") val linkCode: JServer.Credentials.LinkCode,
) : Parcelable {

    fun toEncodedString(moshi: Moshi): String = moshi.adapter<LinkCodeContainer>()
        .toJson(this)
        .toByteArray()
        .toByteString()
        .base64()

    companion object {
        fun fromEncodedString(moshi: Moshi, encoded: String): LinkCodeContainer = encoded
            .decodeBase64()!!
            .let { moshi.adapter<LinkCodeContainer>().fromJson(it)!! }
    }
}
package eu.darken.octi.modules.clipboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okio.ByteString

@JsonClass(generateAdapter = true)
data class ClipboardItem(
    @Json(name = "type") val type: Type = Type.EMPTY,
    @Json(name = "data") val data: ByteString = ByteString.EMPTY,
) {

    @JsonClass(generateAdapter = false)
    enum class Type {
        @Json(name = "EMPTY") EMPTY,
        @Json(name = "SIMPLE_TEXT") SIMPLE_TEXT,
        ;
    }

}
package eu.darken.octi.modules.clipboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okio.ByteString

@JsonClass(generateAdapter = true)
data class ClipboardInfo(
    @Json(name = "type") val type: Type = Type.EMPTY,
    @Json(name = "data") val data: ByteString = ByteString.EMPTY,
) {

    init {
        if (data.size > 32 * 1024) throw java.lang.IllegalArgumentException("Size limit exceeded (>32KB)")
    }

    @JsonClass(generateAdapter = false)
    enum class Type {
        @Json(name = "EMPTY") EMPTY,
        @Json(name = "SIMPLE_TEXT") SIMPLE_TEXT,
        ;
    }

}
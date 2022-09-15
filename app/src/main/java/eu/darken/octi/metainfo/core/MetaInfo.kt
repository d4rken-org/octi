package eu.darken.octi.metainfo.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetaInfo(
    @Json(name = "versionName") val versionName: String,
    @Json(name = "deviceName") val deviceName: String,
)
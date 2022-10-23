package eu.darken.octi.modules.apps.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class AppsInfo(
    @Json(name = "installedPackages") val installedPackages: Collection<Pkg>
) {

    @JsonClass(generateAdapter = true)
    data class Pkg(
        @Json(name = "packageName") val packageName: String,
        @Json(name = "label") val label: String?,
        @Json(name = "versionCode") val versionCode: Long,
        @Json(name = "versionName") val versionName: String?,
        @Json(name = "installedAt") val installedAt: Instant,
    )

    override fun toString(): String = "AppsInfo(size=${installedPackages.size})"
}
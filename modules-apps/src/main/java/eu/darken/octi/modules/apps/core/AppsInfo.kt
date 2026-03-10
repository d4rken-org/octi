@file:UseSerializers(InstantSerializer::class)

package eu.darken.octi.modules.apps.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

@Serializable
data class AppsInfo(
    @SerialName("installedPackages") val installedPackages: Collection<Pkg>,
) {

    @Serializable
    data class Pkg(
        @SerialName("packageName") val packageName: String,
        @SerialName("label") val label: String?,
        @SerialName("versionCode") val versionCode: Long,
        @SerialName("versionName") val versionName: String?,
        @SerialName("installedAt") val installedAt: Instant,
        @SerialName("installerPkg") val installerPkg: String?,
        @SerialName("updatedAt") val updatedAt: Instant? = null,
    )

    override fun toString(): String = "AppsInfo(size=${installedPackages.size})"
}

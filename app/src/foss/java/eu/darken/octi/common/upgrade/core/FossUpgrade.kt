@file:UseSerializers(InstantSerializer::class)

package eu.darken.octi.common.upgrade.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

@Serializable
data class FossUpgrade(
    @SerialName("upgradedAt") val upgradedAt: Instant,
    @SerialName("reason") val upgradeType: Type,
) {
    @Serializable
    enum class Type {
        @SerialName("GITHUB_SPONSORS") GITHUB_SPONSORS,
        @SerialName("foss.upgrade.reason.donated") LEGACY_DONATED,
        @SerialName("foss.upgrade.reason.alreadydonated") LEGACY_ALREADY_DONATED,
        @SerialName("foss.upgrade.reason.nomoney") LEGACY_NO_MONEY,
        ;
    }
}

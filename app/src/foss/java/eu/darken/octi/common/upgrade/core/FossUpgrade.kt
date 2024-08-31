package eu.darken.octi.common.upgrade.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class FossUpgrade(
    @Json(name = "upgradedAt") val upgradedAt: Instant,
    @Json(name = "reason") val upgradeType: Type,
) {
    @JsonClass(generateAdapter = false)
    enum class Type {
        @Json(name = "GITHUB_SPONSORS") GITHUB_SPONSORS,
        @Json(name = "foss.upgrade.reason.donated") LEGACY_DONATED,
        @Json(name = "foss.upgrade.reason.alreadydonated") LEGACY_ALREADY_DONATED,
        @Json(name = "foss.upgrade.reason.nomoney") LEGACY_NO_MONEY,
        ;
    }
}
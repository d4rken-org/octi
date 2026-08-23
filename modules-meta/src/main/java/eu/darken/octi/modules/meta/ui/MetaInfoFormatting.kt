package eu.darken.octi.modules.meta.ui

import eu.darken.octi.common.humanizeIdentifier
import eu.darken.octi.modules.meta.core.MetaInfo

/**
 * Compact OS label for display, e.g. "Android 14 (API 34)", "Windows 11", "macOS 14.4".
 * Returns null if no OS metadata is available.
 *
 * Prefers the generic `osType` / `osVersionName` fields; falls back to legacy `androidVersionName`
 * for payloads from older Android clients that don't populate the generic fields.
 */
fun MetaInfo.osDisplayName(): String? {
    val parsed = osType?.let { parseOsFamily(it) }
    val label = parsed?.name ?: androidVersionName?.takeIf { it.isNotBlank() }?.let { "Android" }
    val isAndroidFamily = parsed?.name == "Android" || (parsed == null && !androidVersionName.isNullOrBlank())
    val version = parsed?.inlineVersion
        ?: osVersionName?.takeIf { it.isNotBlank() }
        ?: androidVersionName?.takeIf { it.isNotBlank() && isAndroidFamily }
    return when {
        label == null && version == null -> null
        label == null -> version
        version == null -> label
        else -> {
            val base = "$label $version"
            if (isAndroidFamily && androidApiLevel != null) "$base (API $androidApiLevel)" else base
        }
    }
}

private data class OsFamily(
    val name: String,
    val inlineVersion: String?,
)

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Desktop clients fold the marketing version into the type string ("Windows 11"), while their
 * `osVersionName` carries the kernel version ("10.0"). This is the only place a version is taken
 * out of `osType`.
 */
private val WINDOWS_WITH_VERSION = Regex("^windows +([0-9][0-9a-z.]*)$")

/**
 * Peers report `osType` in whatever spelling their platform uses. Known spellings map to a curated
 * family name, everything else is prettified as-is instead of being force-fitted into a family.
 *
 * Matching is exact (after normalizing case and whitespace), so unrelated values like "chromeosx"
 * can't be swallowed by a prefix match.
 */
private fun parseOsFamily(osType: String): OsFamily? {
    val normalized = osType.trim().lowercase().replace(WHITESPACE_RUN, " ")
    if (normalized.isEmpty()) return null

    val alias = when (normalized) {
        "android" -> "Android"
        "windows" -> "Windows"
        "macos", "mac os", "mac os x" -> "macOS"
        "ios" -> "iOS"
        "chromeos", "chrome os", "chrome_os", "chrome-os" -> "ChromeOS"
        "linux" -> "Linux"
        "browser", "web" -> "Browser"
        else -> null
    }
    if (alias != null) return OsFamily(alias, null)

    WINDOWS_WITH_VERSION.matchEntire(normalized)?.let { match ->
        return OsFamily("Windows", match.groupValues[1])
    }

    return OsFamily(humanizeIdentifier(osType.trim()), null)
}

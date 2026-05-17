package eu.darken.octi.modules.meta.ui

import eu.darken.octi.modules.meta.core.MetaInfo

/**
 * Compact OS label for display, e.g. "Android 14 (API 34)", "Windows 11", "macOS 14.4".
 * Returns null if no OS metadata is available.
 *
 * Prefers the generic `osType` / `osVersionName` fields; falls back to legacy `androidVersionName`
 * for payloads from older Android clients that don't populate the generic fields.
 */
fun MetaInfo.osDisplayName(): String? {
    val osLabel = osType?.takeIf { it.isNotBlank() }?.let { osDisplayLabel(it) }
        ?: androidVersionName?.takeIf { it.isNotBlank() }?.let { "Android" }
    val version = osVersionName?.takeIf { it.isNotBlank() }
        ?: androidVersionName?.takeIf { it.isNotBlank() }
    return when {
        osLabel == null && version == null -> null
        osLabel == null -> version
        version == null -> osLabel
        else -> {
            val base = "$osLabel $version"
            if (osLabel == "Android" && androidApiLevel != null) "$base (API $androidApiLevel)" else base
        }
    }
}

private fun osDisplayLabel(osType: String): String? = when (osType.lowercase()) {
    "android" -> "Android"
    "windows" -> "Windows"
    "macos" -> "macOS"
    "linux" -> "Linux"
    "ios" -> "iOS"
    "chromeos", "chrome_os", "chrome-os" -> "ChromeOS"
    "browser", "web" -> "Browser"
    "" -> null
    else -> osType.replaceFirstChar { it.titlecase() }
}

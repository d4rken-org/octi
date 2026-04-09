package eu.darken.octi.common.widget

import android.os.Bundle
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag

data class WidgetInstanceConfig(
    val isMaterialYou: Boolean,
    val presetName: String?,
    val customBg: Int?,
    val customAccent: Int?,
    val allowedDeviceIds: Set<String>,
) {
    val themeColors: WidgetTheme.Colors?
        get() = when {
            isMaterialYou -> null
            customBg != null && customAccent != null -> WidgetTheme.deriveColors(customBg, customAccent)
            else -> null
        }

    companion object {
        private val TAG = logTag("Widget", "InstanceConfig")

        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_CUSTOM_BG = "custom_bg"
        const val KEY_CUSTOM_ACCENT = "custom_accent"
        const val KEY_DEVICE_FILTER_IDS = "device_filter_ids"

        const val MODE_MATERIAL_YOU = "material_you"
        const val MODE_CUSTOM = "custom"

        val DEFAULT = WidgetInstanceConfig(
            isMaterialYou = true,
            presetName = null,
            customBg = null,
            customAccent = null,
            allowedDeviceIds = emptySet(),
        )

        fun parse(options: Bundle): WidgetInstanceConfig = try {
            val mode = options.getString(KEY_THEME_MODE)
            val isMaterialYou = mode != MODE_CUSTOM
            val presetName = options.getString(KEY_THEME_PRESET)
            val customBg = if (options.containsKey(KEY_CUSTOM_BG)) options.getInt(KEY_CUSTOM_BG) else null
            val customAccent = if (options.containsKey(KEY_CUSTOM_ACCENT)) options.getInt(KEY_CUSTOM_ACCENT) else null
            val allowedDeviceIds = options.getStringArray(KEY_DEVICE_FILTER_IDS)?.toSet().orEmpty()
            WidgetInstanceConfig(
                isMaterialYou = isMaterialYou,
                presetName = presetName,
                customBg = customBg,
                customAccent = customAccent,
                allowedDeviceIds = allowedDeviceIds,
            )
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to parse: ${e.asLog()}" }
            DEFAULT
        }

        fun write(options: Bundle, config: WidgetInstanceConfig) {
            if (config.isMaterialYou) {
                options.putString(KEY_THEME_MODE, MODE_MATERIAL_YOU)
                options.putString(KEY_THEME_PRESET, WidgetTheme.MATERIAL_YOU.name)
                options.remove(KEY_CUSTOM_BG)
                options.remove(KEY_CUSTOM_ACCENT)
            } else {
                options.putString(KEY_THEME_MODE, MODE_CUSTOM)
                options.putString(KEY_THEME_PRESET, config.presetName ?: "")
                options.remove(KEY_CUSTOM_BG)
                options.remove(KEY_CUSTOM_ACCENT)
                config.customBg?.let { options.putInt(KEY_CUSTOM_BG, it) }
                config.customAccent?.let { options.putInt(KEY_CUSTOM_ACCENT, it) }
            }
            if (config.allowedDeviceIds.isEmpty()) {
                options.remove(KEY_DEVICE_FILTER_IDS)
            } else {
                options.putStringArray(KEY_DEVICE_FILTER_IDS, config.allowedDeviceIds.toTypedArray())
            }
        }
    }
}

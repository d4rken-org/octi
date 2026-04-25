package eu.darken.octi.common.widget

import android.os.Bundle
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.serialization.Serializable

@Serializable
data class WidgetInstanceConfig(
    val isMaterialYou: Boolean = true,
    val presetName: String? = null,
    val customBg: Int? = null,
    val customAccent: Int? = null,
    val allowedDeviceIds: Set<String> = emptySet(),
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

        val DEFAULT = WidgetInstanceConfig()

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
    }
}

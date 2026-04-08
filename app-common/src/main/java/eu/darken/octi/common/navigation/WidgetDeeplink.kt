package eu.darken.octi.common.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri

object WidgetDeeplink {
    const val ACTION = "eu.darken.octi.action.OPEN_MODULE_DETAIL"
    const val EXTRA_DEVICE_ID = "deeplink_device_id"
    const val EXTRA_MODULE_TYPE = "deeplink_module_type"

    enum class ModuleType { POWER, CONNECTIVITY, CLIPBOARD }

    data class OpenModuleDetail(
        val deviceId: String,
        val moduleType: ModuleType,
    )

    fun intentDataFor(deviceId: String, moduleType: ModuleType): Uri =
        Uri.parse("octi://widget/detail/${moduleType.name.lowercase()}/$deviceId")

    fun buildIntent(context: Context, deviceId: String, moduleType: ModuleType): Intent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return launchIntent.apply {
            action = ACTION
            data = intentDataFor(deviceId, moduleType)
            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_DEVICE_ID, deviceId)
            putExtra(EXTRA_MODULE_TYPE, moduleType.name)
        }
    }

    /**
     * Parse an incoming [Intent] into an [OpenModuleDetail]. Returns `null` if the intent is not
     * a widget deeplink, is missing required extras, or has an unknown module type.
     */
    fun parse(intent: Intent?): OpenModuleDetail? {
        if (intent?.action != ACTION) return null
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val moduleTypeName = intent.getStringExtra(EXTRA_MODULE_TYPE) ?: return null
        val moduleType = runCatching { ModuleType.valueOf(moduleTypeName) }.getOrNull() ?: return null
        return OpenModuleDetail(deviceId, moduleType)
    }
}

package eu.darken.octi.common.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Deeplink shape for tapping an "incoming file share" notification. Mirrors [WidgetDeeplink]
 * — same action+URI+extras combo so [eu.darken.octi.main.ui.MainActivityVM.handleDeeplinkIntent]
 * can dispatch on a single parse step.
 */
object FileShareDeeplink {
    const val ACTION = "eu.darken.octi.action.OPEN_FILE_SHARE"
    const val EXTRA_DEVICE_ID = "deeplink_file_share_device_id"

    data class OpenFileShare(val deviceId: String)

    fun intentDataFor(deviceId: String): Uri = Uri.parse("octi://file-share/$deviceId")

    fun buildIntent(context: Context, deviceId: String): Intent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return launchIntent.apply {
            action = ACTION
            data = intentDataFor(deviceId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEVICE_ID, deviceId)
        }
    }

    fun parse(intent: Intent?): OpenFileShare? {
        if (intent?.action != ACTION) return null
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)?.takeIf { it.isNotBlank() } ?: return null
        return OpenFileShare(deviceId)
    }
}

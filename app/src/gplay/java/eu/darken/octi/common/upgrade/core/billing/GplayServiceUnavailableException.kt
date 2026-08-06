package eu.darken.octi.common.upgrade.core.billing

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import eu.darken.octi.R
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError

class GplayServiceUnavailableException(cause: Throwable) :
    BillingException("Google Play services are unavailable.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_unavailable_error_title.toCaString(),
        description = R.string.upgrades_gplay_unavailable_error_description.toCaString(),
        fixActionLabel = "Google Play".toCaString(),
        fixAction = { activity ->
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", GPLAY_PKG, null)
            }

            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                onLaunchFailed(e)
                throw e
            } catch (e: SecurityException) {
                // Play can be installed but unreachable: disabled app, work/restricted profile or a
                // ROM that guards the settings screen. The launch is denied, not unresolvable.
                onLaunchFailed(e)
                throw e
            }
        },
        // The failure is not presented here: it propagates to the dialog, which renders this inline.
        // A Toast caps at 2 lines and clipped this message (in French mid-word, dropping a whole
        // condition), so the container was the defect, not the wording.
        fixActionErrorMessage = R.string.upgrades_gplay_not_installed_message.toCaString(),
    )

    private fun onLaunchFailed(e: Exception) {
        log(ERROR) { "Can't launch settings intent for Google Play: $e" }
    }

    companion object {
        private const val GPLAY_PKG = "com.android.vending"
    }
}
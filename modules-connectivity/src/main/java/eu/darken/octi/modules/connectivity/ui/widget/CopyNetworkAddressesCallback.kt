package eu.darken.octi.modules.connectivity.ui.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.EntryPointAccessors
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.connectivity.R
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CopyNetworkAddressesCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val deviceId = parameters[KEY_DEVICE_ID] ?: return
        log(TAG, VERBOSE) { "CopyNetworkAddressesCallback: deviceId=$deviceId" }

        try {
            val ep = EntryPointAccessors.fromApplication(context, NetworkWidgetEntryPoint::class.java)
            val connectivityRepo = ep.connectivityRepo()

            val state = connectivityRepo.state.first()
            val info = state.all.firstOrNull { it.deviceId.id == deviceId }?.data ?: return

            val text = buildAddressText(info)
            if (text.isBlank()) {
                log(TAG, VERBOSE) { "No addresses to copy for device=$deviceId" }
                return
            }

            val cm = context.getSystemService<ClipboardManager>() ?: return
            cm.setPrimaryClip(ClipData.newPlainText("Octi network addresses", text))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.module_connectivity_widget_copied_toast),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to copy network addresses: ${e.asLog()}" }
        }

        NetworkGlanceWidget().updateAll(context)
    }

    private fun buildAddressText(info: ConnectivityInfo): String = buildList {
        info.localAddressIpv4?.takeIf { it.isNotBlank() }?.let { add(it) }
        info.localAddressIpv6?.takeIf { it.isNotBlank() }?.let { add(it) }
        info.publicIp?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString("\n")

    companion object {
        private val TAG = logTag("Module", "Connectivity", "Widget", "CopyAddresses")
        val KEY_DEVICE_ID = ActionParameters.Key<String>("deviceId")
    }
}

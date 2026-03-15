package eu.darken.octi.modules.power.ui.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.glance.appwidget.updateAll
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BatteryWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun refreshWidgets() {
        log(TAG) { "refreshWidgets()" }
        BatteryGlanceWidget().updateAll(context)
    }

    companion object {
        val TAG = logTag("Module", "Power", "Widget", "Manager")
    }
}

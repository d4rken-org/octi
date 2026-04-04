package eu.darken.octi.modules.connectivity.ui.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.glance.appwidget.updateAll
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.widget.WidgetManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetManager {

    override suspend fun refreshWidgets() {
        log(TAG) { "refreshWidgets()" }
        NetworkGlanceWidget().updateAll(context)
    }

    companion object {
        val TAG = logTag("Module", "Connectivity", "Widget", "Manager")
    }
}

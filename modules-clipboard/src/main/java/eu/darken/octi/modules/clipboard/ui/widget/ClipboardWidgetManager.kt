package eu.darken.octi.modules.clipboard.ui.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.glance.appwidget.updateAll
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.widget.WidgetManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetManager {

    override suspend fun refreshWidgets() {
        log(TAG) { "refreshWidgets()" }
        ClipboardGlanceWidget().updateAll(context)
    }

    companion object {
        val TAG = logTag("Module", "Clipboard", "Widget", "Manager")
    }
}

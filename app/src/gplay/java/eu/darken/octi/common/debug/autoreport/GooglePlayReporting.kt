package eu.darken.octi.common.debug.autoreport

import android.app.Application
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.InstallId
import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.debug.AutomaticBugReporter
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePlayReporting @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugSettings: DebugSettings,
    private val installId: InstallId,
) : AutomaticBugReporter {

    override fun setup(application: Application) {
        val isEnabled = debugSettings.isAutoReportingEnabled.valueBlocking
        log(TAG) { "setup(): isEnabled=$isEnabled" }
    }

    override fun notify(throwable: Throwable) {
        // NOOP
    }

    companion object {
        private val TAG = logTag("Debug", "GooglePlayReporting")
    }
}
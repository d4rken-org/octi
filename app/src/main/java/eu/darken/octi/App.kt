package eu.darken.octi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.AutomaticBugReporter
import eu.darken.octi.common.debug.logging.LogCatLogger
import eu.darken.octi.common.debug.logging.Logging
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.RecorderModule
import eu.darken.octi.common.theming.Theming
import eu.darken.octi.main.core.CurriculumVitae
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.release.ReleaseManager
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.worker.SyncWorkerControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bugReporter: AutomaticBugReporter
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var moduleManager: ModuleManager
    @Inject lateinit var syncWorkerControl: SyncWorkerControl
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var recorderModule: RecorderModule
    @Inject lateinit var imageLoaderFactory: ImageLoaderFactory
    @Inject lateinit var theming: Theming
    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var releaseManager: ReleaseManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigWrap.DEBUG) {
            Logging.install(LogCatLogger())
            log(TAG) { "BuildConfigWrap.DEBUG=true" }
            log(TAG) { OctiAscii.logo }
        }

        recorderModule.state
            .onEach { log(TAG) { "RecorderModule: $it" } }
            .launchIn(appScope)

        log(TAG, INFO) { BuildConfigWrap.VERSION_DESCRIPTION }

        bugReporter.setup(this)

        syncManager.start()
        moduleManager.start()

        syncWorkerControl.start()

        curriculumVitae.updateAppLaunch()

        appScope.launch { releaseManager.checkEarlyAdopter() }

        theming.setup()

        Coil.setImageLoader(imageLoaderFactory)

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    override val workManagerConfiguration: Configuration get() = Configuration.Builder()
        .setMinimumLoggingLevel(
            when {
                BuildConfigWrap.DEBUG -> android.util.Log.VERBOSE
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV -> android.util.Log.DEBUG
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.BETA -> android.util.Log.INFO
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE -> android.util.Log.WARN
                else -> android.util.Log.VERBOSE
            }
        )
        .setWorkerFactory(workerFactory)
        .build()

    companion object {
        internal val TAG = logTag("App")
    }
}

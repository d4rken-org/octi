package eu.darken.octi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.getkeepsafe.relinker.ReLinker
import dagger.hilt.android.HiltAndroidApp
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.autoreport.AutoReporting
import eu.darken.octi.common.debug.logging.*
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.worker.SyncWorkerControl
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bugReporter: AutoReporting
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var syncWorkerControl: SyncWorkerControl

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigWrap.DEBUG) {
            Logging.install(LogCatLogger())
            log(TAG) { "BuildConfigWrap.DEBUG=true" }
        }

        log(TAG) {
            """
                
                            .---.         ,,
                 ,,        /     \       ;,,'
                ;, ;      (  o  o )      ; ;
                  ;,';,,,  \  \/ /      ,; ;
               ,,,  ;,,,,;;,`   '-,;'''',,,'
              ;,, ;,, ,,,,   ,;  ,,,'';;,,;''';
                 ;,,,;    ~~'  '';,,''',,;''''  
                                    '''
            """.trimIndent()
        }

        ReLinker
            .log { message -> log(TAG) { "ReLinker: $message" } }
            .loadLibrary(this, "bugsnag-plugin-android-anr")

        bugReporter.setup()

        syncManager.start()

        syncWorkerControl.schedule()

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
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

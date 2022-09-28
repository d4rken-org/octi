package eu.darken.octi

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.getkeepsafe.relinker.ReLinker
import dagger.hilt.android.HiltAndroidApp
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.autoreport.AutoReporting
import eu.darken.octi.common.debug.logging.*
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.ThemeType
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.worker.SyncWorkerControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bugReporter: AutoReporting
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var moduleManager: ModuleManager
    @Inject lateinit var syncWorkerControl: SyncWorkerControl
    @Inject lateinit var generalSettings: GeneralSettings

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
        moduleManager.start()

        syncWorkerControl.start()

        generalSettings.themeType.flow
            .map { ThemeType.valueOf(it) }
            .onEach {
                withContext(dispatcherProvider.Main) {
                    when (it) {
                        ThemeType.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        ThemeType.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        ThemeType.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "themeMode" }
            .launchIn(appScope)

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

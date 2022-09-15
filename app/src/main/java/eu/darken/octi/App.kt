package eu.darken.octi

import android.app.Application
import com.getkeepsafe.relinker.ReLinker
import dagger.hilt.android.HiltAndroidApp
import eu.darken.octi.battery.core.BatteryRepo
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.autoreport.AutoReporting
import eu.darken.octi.common.debug.logging.*
import eu.darken.octi.metainfo.core.MetaSync
import javax.inject.Inject

@HiltAndroidApp
open class App : Application() {

    @Inject lateinit var bugReporter: AutoReporting
    @Inject lateinit var batteryRepo: BatteryRepo
    @Inject lateinit var metaSync: MetaSync

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigWrap.DEBUG) {
            Logging.install(LogCatLogger())
            log(TAG) { "BuildConfigWrap.DEBUG=true" }
        }

        ReLinker
            .log { message -> log(TAG) { "ReLinker: $message" } }
            .loadLibrary(this, "bugsnag-plugin-android-anr")

        bugReporter.setup()
        metaSync.start()
        batteryRepo.start()

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }

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
    }

    companion object {
        internal val TAG = logTag("App")
    }
}

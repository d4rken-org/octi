package eu.darken.octi.common.theming

import androidx.appcompat.app.AppCompatDelegate
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Theming @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val generalSettings: GeneralSettings,
) {

    fun setup() {
        log(TAG) { "setup()" }

        generalSettings.themeMode.valueBlocking.applyMode()

        generalSettings.themeMode.flow
            .onEach { it.applyMode() }
            .setupCommonEventHandlers(TAG) { "themeMode" }
            .launchIn(appScope)
    }

    private fun ThemeMode.applyMode() = when (this) {
        ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    companion object {
        private val TAG = logTag("Theming")
    }
}

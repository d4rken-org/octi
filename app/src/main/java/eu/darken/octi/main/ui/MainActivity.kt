package eu.darken.octi.main.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.navigation.LocalNavigationController
import eu.darken.octi.common.navigation.NavigationController
import eu.darken.octi.common.navigation.NavigationEntry
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.uix.Activity2
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainActivityVM by viewModels()

    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var debugSessionManager: DebugSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        setContent {
            val themeState by vm.themeState.collectAsState()

            val backStack = rememberNavBackStack(vm.startDestination)
            navCtrl.setup(backStack)

            OctiTheme(state = themeState) {
                CompositionLocalProvider(LocalNavigationController provides navCtrl) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = {
                            if (!navCtrl.up()) {
                                finish()
                            }
                        },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            navigationEntries.forEach { entry ->
                                entry.apply {
                                    log(TAG) { "Set up navigation entry: $this" }
                                    setup()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    companion object {
        private val TAG = logTag("MainActivity")
    }
}

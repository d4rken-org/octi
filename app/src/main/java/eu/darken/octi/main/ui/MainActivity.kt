package eu.darken.octi.main.ui

import android.content.Intent
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
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.navigation.LocalNavigationController
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationController
import eu.darken.octi.common.navigation.NavigationEntry
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.core.LocalConnectorContributions
import eu.darken.octi.common.theming.OctiTheme
import eu.darken.octi.common.uix.Activity2
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainActivityVM by viewModels()

    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var connectorContributions: Map<ConnectorType, @JvmSuppressWildcards ConnectorUiContribution>
    @Inject lateinit var debugSessionManager: DebugSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        if (BuildConfigWrap.DEBUG) {
            check(connectorContributions.isNotEmpty()) {
                "No ConnectorUiContribution registered — check @IntoMap wiring in syncs-* modules"
            }
        }

        setContent {
            val themeState by vm.themeState.collectAsState()

            val backStack = rememberNavBackStack(vm.startDestination)
            navCtrl.setup(backStack)

            OctiTheme(state = themeState) {
                CompositionLocalProvider(
                    LocalNavigationController provides navCtrl,
                    LocalConnectorContributions provides connectorContributions,
                ) {
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

        // Cold-start via widget deeplink: parse extras, VM holds them until Dashboard observes.
        vm.handleDeeplinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val handled = vm.handleDeeplinkIntent(intent)
        if (handled) {
            // Force nav back to Dashboard so the deeplink observer picks it up. If Dashboard
            // is not on the back stack (e.g. mid-onboarding), popTo no-ops and we leave nav alone.
            navCtrl.popTo(Nav.Main.Dashboard)
        }
    }

    companion object {
        private val TAG = logTag("MainActivity")
    }
}

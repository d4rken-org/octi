package eu.darken.octi.sync.core

import androidx.annotation.StringRes
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dagger.MapKey
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.sync.ConnectorType

/**
 * Each sync backend contributes one instance to [dagger.multibindings.IntoMap] keyed by
 * [ConnectorType]. Hosts the UI-shaped bits the `app` module used to dispatch on via
 * `when (ConnectorType)` expressions.
 *
 * Invariant: exactly one contribution per [ConnectorType]. Dagger fails at wiring if the key
 * collides.
 */
interface ConnectorUiContribution {
    val type: ConnectorType

    /** Lower values sort first in the Add-account list and similar listings. */
    val displayOrder: Int

    @get:StringRes val labelRes: Int

    @get:StringRes val descriptionRes: Int

    @Composable
    fun Icon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)

    fun addAccountDestination(): NavigationDestination

    /**
     * Destination for the "link new device" action for this connector, or null if the backend
     * does not support device linking. OctiServer returns its link-host screen; GDrive returns null.
     */
    fun linkDeviceDestination(connector: SyncConnector): NavigationDestination? = null

    /** Title rendered in the sync list card header (e.g. "Google Drive (AppData)"). */
    @Composable
    fun listCardTitle(connector: SyncConnector): String

    /** Value for the "Account" labeled row in the sync list card. */
    @Composable
    fun listCardAccountValue(connector: SyncConnector): String

    /** Per-backend bottom sheet shown when tapping a card in the sync list. */
    @Composable
    fun ActionsSheet(
        connector: SyncConnector,
        state: SyncConnectorState,
        isPaused: Boolean,
        pauseReason: ConnectorPauseReason?,
        isPro: Boolean,
        onDismiss: () -> Unit,
        onTogglePause: () -> Unit,
        onForceSync: () -> Unit,
        onViewDevices: () -> Unit,
        onLinkNewDevice: () -> Unit,
        onReset: () -> Unit,
        onDisconnect: () -> Unit,
    )
}

@MapKey
annotation class ConnectorTypeKey(val value: ConnectorType)

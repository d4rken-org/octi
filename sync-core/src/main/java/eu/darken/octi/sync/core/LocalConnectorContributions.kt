package eu.darken.octi.sync.core

import androidx.compose.runtime.staticCompositionLocalOf
import eu.darken.octi.common.sync.ConnectorType

/**
 * Composition-local map of all sync-connector UI contributions, keyed by [ConnectorType].
 * Provided at the activity composition root; read by any composable that needs to render
 * per-connector icons, labels, or navigate to add-account screens.
 *
 * Default is an empty map so previews render a fallback without crashing. Real callers wire
 * the Hilt-bound map via CompositionLocalProvider in MainActivity.
 */
val LocalConnectorContributions =
    staticCompositionLocalOf<Map<ConnectorType, ConnectorUiContribution>> {
        emptyMap()
    }

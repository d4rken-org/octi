package eu.darken.octi.common.navigation

import eu.darken.octi.common.flow.SingleEventFlow

interface NavigationEventSource {
    val navEvents: SingleEventFlow<NavEvent>
}

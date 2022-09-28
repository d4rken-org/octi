package eu.darken.octi.syncs.jserver.ui.actions

import eu.darken.octi.syncs.jserver.core.JServerApi

sealed class ActionEvents {
    data class HealthCheck(val health: JServerApi.Health) : ActionEvents()
}

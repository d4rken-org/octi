package eu.darken.octi.syncs.kserver.ui.actions

import eu.darken.octi.syncs.kserver.core.KServerApi

sealed class ActionEvents {
    data class HealthCheck(val health: KServerApi.Health) : ActionEvents()
}

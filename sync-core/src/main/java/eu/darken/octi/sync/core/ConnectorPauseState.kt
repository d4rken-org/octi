package eu.darken.octi.sync.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConnectorPauseReason {
    @SerialName("manual")
    Manual,

    @SerialName("auth_issue")
    AuthIssue,
}

@Serializable
data class ConnectorPauseState(
    @SerialName("connector_id") val connectorId: ConnectorId,
    @SerialName("reason") val reason: ConnectorPauseReason,
)

fun Set<ConnectorPauseState>.reasonFor(connectorId: ConnectorId): ConnectorPauseReason? =
    firstOrNull { it.connectorId == connectorId }?.reason

val Set<ConnectorPauseState>.connectorIds: Set<ConnectorId>
    get() = mapTo(mutableSetOf()) { it.connectorId }

package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import java.time.Instant

sealed interface SyncEvent {
    val connectorId: ConnectorId

    data class ModuleChanged(
        override val connectorId: ConnectorId,
        val deviceId: DeviceId,
        val moduleId: ModuleId,
        val modifiedAt: Instant,
        val action: Action,
    ) : SyncEvent {
        enum class Action { UPDATED, DELETED }
    }

    data class BlobChanged(
        override val connectorId: ConnectorId,
        val deviceId: DeviceId,
        val moduleId: ModuleId,
        val blobKey: BlobKey,
        val action: Action,
    ) : SyncEvent {
        enum class Action { ADDED, MODIFIED, DELETED }
    }
}

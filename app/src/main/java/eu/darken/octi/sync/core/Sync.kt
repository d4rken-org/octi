package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface Sync {
    interface Hub

    interface Connector {

        val state: Flow<State>

        suspend fun sync()

        interface State {
            val isSyncing: Boolean
            val lastSyncAt: Instant?
        }

    }

    interface Data {
        val devices: Collection<Device>

        interface Device {
            val deviceId: SyncDeviceId
            val lastUpdatedAt: Instant
            val payload: String
        }
    }

}
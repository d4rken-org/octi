package eu.darken.octi.sync.core

import java.time.Instant

interface SyncConnectorState {
    val readActions: Int
    val lastReadAt: Instant?

    val writeActions: Int
    val lastWriteAt: Instant?

    val lastError: Exception?

    val quota: Quota?

    val isBusy: Boolean
        get() = readActions > 0 || writeActions > 0

    val lastSyncAt: Instant?
        get() = setOf(lastReadAt, lastWriteAt).filterNotNull().maxByOrNull { it }

    val devices: Collection<DeviceId>?

    data class Quota(
        val updatedAt: Instant = Instant.now(),
        val storageUsed: Long = -1,
        val storageTotal: Long = -1,
    ) {
        val storageFree: Long
            get() = storageTotal - storageUsed
    }
}
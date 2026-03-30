package eu.darken.octi.sync.core

import java.time.Duration
import java.time.Instant

interface SyncConnectorState {
    val activeActions: Int
    val lastActionAt: Instant?

    val lastError: Exception?

    data class Quota(
        val updatedAt: Instant = Instant.now(),
        val storageUsed: Long = -1,
        val storageTotal: Long = -1,
    ) {
        val storageFree: Long
            get() = storageTotal - storageUsed
    }

    val quota: Quota?

    val isBusy: Boolean
        get() = activeActions > 0

    val lastSyncAt: Instant?
        get() = lastActionAt

    val devices: Collection<DeviceId>?

    val isAvailable: Boolean

    data class ClockOffset(
        val offset: Duration,
        val measuredAt: Instant,
    )

    // Clock offsets collected during the most recent sync round.
    // Positive offset = local ahead, negative = local behind.
    val clockOffsets: List<ClockOffset>
        get() = emptyList()
}
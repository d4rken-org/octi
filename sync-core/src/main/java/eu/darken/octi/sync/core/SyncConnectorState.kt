package eu.darken.octi.sync.core

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

interface SyncConnectorState {

    val lastActionAt: Instant?

    val lastError: Exception?

    data class Quota(
        val updatedAt: Instant = Clock.System.now(),
        val storageUsed: Long = -1,
        val storageTotal: Long = -1,
    ) {
        val storageFree: Long
            get() = storageTotal - storageUsed
    }

    val quota: Quota?

    val lastSyncAt: Instant?
        get() = lastActionAt

    val deviceMetadata: List<DeviceMetadata> get() = emptyList()

    val issues: List<ConnectorIssue> get() = emptyList()

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

package eu.darken.octi.sync.core

import kotlin.time.Duration
import kotlin.time.Instant

interface SyncConnectorState {

    val lastActionAt: Instant?

    val lastError: Exception?

    val lastSyncAt: Instant?
        get() = lastActionAt

    val deviceMetadata: List<DeviceMetadata> get() = emptyList()

    /**
     * When this connector last completed a **full** (non-targeted) payload read that actually
     * retrieved data. `null` means no such read has completed for this connector instance — the
     * absence of a device's payload therefore proves nothing, and callers must not treat it as
     * degradation. Cleared on Pause/Resume and Reset so a peer added while paused isn't
     * mistaken for a missing one.
     */
    val lastFullReadAt: Instant? get() = null

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

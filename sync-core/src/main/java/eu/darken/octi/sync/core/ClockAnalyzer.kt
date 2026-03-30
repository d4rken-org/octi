package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.replayingShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClockAnalyzer @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val syncManager: SyncManager,
    private val syncSettings: SyncSettings,
) {

    data class ClockAnalysis(
        val skewedDeviceIds: Set<DeviceId>,
        val suspectDeviceIds: Set<DeviceId>,
        val isCurrentDeviceSuspect: Boolean,
        val canDetermineSource: Boolean,
        val localClockOffset: Duration?,
    )

    val analysis: Flow<ClockAnalysis?> = combine(
        syncManager.data,
        syncManager.states,
        syncSettings.clockSkewThreshold.flow,
    ) { devices, states, threshold ->
        val now = Instant.now()
        val deviceTimestamps = devices.mapNotNull { device ->
            val mostRecent = device.modules.maxOfOrNull { it.modifiedAt } ?: return@mapNotNull null
            device.deviceId to mostRecent
        }
        val allOffsets = states.flatMap { it.clockOffsets }
        val freshOffsets = allOffsets.filter { it.measuredAt.isAfter(now.minus(Duration.ofMinutes(30))) }
        Companion.analyze(deviceTimestamps, syncSettings.deviceId, freshOffsets, threshold, now)
    }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "clockAnalysis" }
        .replayingShare(scope)

    companion object {
        private val TAG = logTag("Sync", "ClockAnalyzer")

        internal fun analyze(
            devices: List<Pair<DeviceId, Instant>>,
            currentDeviceId: DeviceId,
            clockOffsets: List<SyncConnectorState.ClockOffset>,
            threshold: Duration,
            now: Instant,
        ): ClockAnalysis? {
            if (devices.size < 2) {
                log(TAG, VERBOSE) { "analyze(): ${devices.size} device(s), ${clockOffsets.size} offset(s) — skipping (need 2+ devices)" }
                return null
            }

            val threshold = threshold.coerceIn(Duration.ofSeconds(1), Duration.ofDays(1))

            // If current device is absent from sync data, we can't reliably determine blame
            val currentDevicePresent = devices.any { (id, _) -> id == currentDeviceId }
            val remoteDevices = devices.filter { (id, _) -> id != currentDeviceId }
            val skewedDeviceIds = remoteDevices
                .filter { (_, modifiedAt) -> modifiedAt.isAfter(now.plus(threshold)) }
                .map { (id, _) -> id }
                .toSet()

            // Compute server clock offset early — it's a primary detection signal
            // Positive = local ahead, negative = local behind
            val freshOffsets = clockOffsets.filter { it.measuredAt.isAfter(now.minus(Duration.ofMinutes(30))) }
            val localClockOffset = if (freshOffsets.isNotEmpty()) {
                val offsets = freshOffsets.map { it.offset.seconds }.sorted()
                val mid = offsets.size / 2
                val medianSeconds = if (offsets.size % 2 == 0) (offsets[mid - 1] + offsets[mid]) / 2 else offsets[mid]
                Duration.ofSeconds(medianSeconds)
            } else {
                null
            }

            val offsetExceedsThreshold = localClockOffset != null && localClockOffset.abs() > threshold

            // No skewed devices AND no significant offset → no discrepancy
            if (skewedDeviceIds.isEmpty() && !offsetExceedsThreshold) {
                log(TAG, VERBOSE) { "analyze(): no discrepancy (skewed=${skewedDeviceIds.size}, offset=$localClockOffset, exceedsThreshold=$offsetExceedsThreshold, threshold=$threshold)" }
                return null
            }

            val isCurrentDeviceSuspect: Boolean
            val suspectDeviceIds: Set<DeviceId>
            val canDetermineSource: Boolean

            if (localClockOffset != null) {
                // Server time available — use it as ground truth
                canDetermineSource = true
                if (offsetExceedsThreshold) {
                    isCurrentDeviceSuspect = true
                    suspectDeviceIds = emptySet()
                } else {
                    isCurrentDeviceSuspect = false
                    suspectDeviceIds = skewedDeviceIds
                }
            } else {
                // No server time — use device count heuristic
                val totalRemoteCount = remoteDevices.size
                when {
                    !currentDevicePresent || totalRemoteCount < 2 -> {
                        // Only 2 devices total — can't determine who's wrong
                        canDetermineSource = false
                        isCurrentDeviceSuspect = false
                        suspectDeviceIds = emptySet()
                    }

                    skewedDeviceIds.size == 1 -> {
                        // 3+ devices, only 1 skewed — that device is likely wrong
                        canDetermineSource = true
                        isCurrentDeviceSuspect = false
                        suspectDeviceIds = skewedDeviceIds
                    }

                    else -> {
                        // 3+ devices, multiple skewed — current device is likely wrong
                        canDetermineSource = true
                        isCurrentDeviceSuspect = true
                        suspectDeviceIds = emptySet()
                    }
                }
            }

            log(TAG) {
                "Clock analysis: skewed=$skewedDeviceIds, suspects=$suspectDeviceIds, " +
                        "currentSuspect=$isCurrentDeviceSuspect, serverOffset=$localClockOffset"
            }

            return ClockAnalysis(
                skewedDeviceIds = skewedDeviceIds,
                suspectDeviceIds = suspectDeviceIds,
                isCurrentDeviceSuspect = isCurrentDeviceSuspect,
                canDetermineSource = canDetermineSource,
                localClockOffset = localClockOffset,
            )
        }
    }
}

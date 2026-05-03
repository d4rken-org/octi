package eu.darken.octi.sync.ui.devices

import eu.darken.octi.sync.core.SyncDevicesSortMode
import io.github.z4kn4fein.semver.Version
import kotlin.time.Instant

internal fun parseVersion(s: String?): Version? = s?.let {
    try {
        Version.parse(it, strict = false)
    } catch (_: Exception) {
        null
    }
}

internal val SyncDevicesVM.DeviceItem.versionKey: Version?
    get() = parseVersion(metaInfo?.octiVersionName ?: serverVersion)

internal fun comparatorFor(
    mode: SyncDevicesSortMode,
    reversed: Boolean = false,
): Comparator<SyncDevicesVM.DeviceItem> {
    val baseTieBreak = compareBy<SyncDevicesVM.DeviceItem, String>(String.CASE_INSENSITIVE_ORDER) { it.displayLabel }
        .thenBy { it.deviceId.id }
    val tieBreak = if (reversed) baseTieBreak.reversed() else baseTieBreak
    // Time/version primary keys flip direction on reverse; nulls always trail so that "unknown"
    // values don't surface to the top when the user just wanted to invert the timeline.
    val instantOrder: Comparator<Instant?> =
        nullsLast(if (reversed) naturalOrder<Instant>() else reverseOrder<Instant>())
    val versionOrder: Comparator<Version?> =
        nullsLast(if (reversed) naturalOrder<Version>() else reverseOrder<Version>())
    return when (mode) {
        SyncDevicesSortMode.DATE_ADDED -> compareBy(instantOrder) { it: SyncDevicesVM.DeviceItem -> it.serverAddedAt }.then(tieBreak)
        SyncDevicesSortMode.LAST_SEEN -> compareBy(instantOrder) { it: SyncDevicesVM.DeviceItem -> it.lastSeen }.then(tieBreak)
        SyncDevicesSortMode.NAME -> tieBreak
        SyncDevicesSortMode.APP_VERSION -> compareBy(versionOrder) { it: SyncDevicesVM.DeviceItem -> it.versionKey }.then(tieBreak)
    }
}

package eu.darken.octi.sync.core

private const val DEVICE_LABEL_ID_CHARS = 8

val DeviceId.shortLabel: String
    get() = id.take(DEVICE_LABEL_ID_CHARS)

fun disambiguateDeviceLabels(labelsByDevice: Map<DeviceId, String>): Map<DeviceId, String> {
    val collisionCounts = labelsByDevice.values
        .groupingBy { it.normalizedDeviceLabel() }
        .eachCount()

    return labelsByDevice.mapValues { (deviceId, label) ->
        if ((collisionCounts[label.normalizedDeviceLabel()] ?: 0) > 1) {
            "$label (${deviceId.shortLabel})"
        } else {
            label
        }
    }
}

private fun String.normalizedDeviceLabel(): String = trim().lowercase()

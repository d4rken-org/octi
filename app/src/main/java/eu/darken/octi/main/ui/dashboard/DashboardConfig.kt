package eu.darken.octi.main.ui.dashboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TileLayoutConfig(
    @SerialName("order") val order: List<String> = DEFAULT_ORDER,
    @SerialName("wideModules") val wideModules: Set<String> = DEFAULT_WIDE,
    @SerialName("hiddenModules") val hiddenModules: Set<String> = emptySet(),
) {
    companion object {
        val DEFAULT_ORDER = listOf(
            "eu.darken.octi.module.core.power",
            "eu.darken.octi.module.core.wifi",
            "eu.darken.octi.module.core.connectivity",
            "eu.darken.octi.module.core.apps",
            "eu.darken.octi.module.core.clipboard",
        )
        val DEFAULT_WIDE = setOf("eu.darken.octi.module.core.power")
    }

    fun normalize(allModuleIds: Set<String>): TileLayoutConfig {
        val knownOrder = order.filter { it in allModuleIds }.distinct()
        val missing = allModuleIds - knownOrder.toSet()
        return copy(
            order = knownOrder + missing.sorted(),
            wideModules = wideModules.filter { it in allModuleIds }.toSet(),
            hiddenModules = hiddenModules.filter { it in allModuleIds }.toSet(),
        )
    }

    fun toRows(availableModules: Set<String>): List<TileRow> {
        val visible = order.filter { it !in hiddenModules && it in availableModules }
        val rows = mutableListOf<TileRow>()
        var i = 0
        while (i < visible.size) {
            if (visible[i] in wideModules) {
                rows.add(TileRow(listOf(visible[i])))
                i++
            } else if (i + 1 < visible.size && visible[i + 1] !in wideModules) {
                rows.add(TileRow(listOf(visible[i], visible[i + 1])))
                i += 2
            } else {
                rows.add(TileRow(listOf(visible[i])))
                i++
            }
        }
        return rows
    }
}

data class TileRow(val modules: List<String>)

@Serializable
data class DashboardConfig(
    @SerialName("collapsedDevices") val collapsedDevices: Set<String> = emptySet(),
    @SerialName("deviceOrder") val deviceOrder: List<String> = emptyList(),
    @SerialName("isSyncExpanded") val isSyncExpanded: Boolean = false,
    @SerialName("defaultTileLayout") val defaultTileLayout: TileLayoutConfig = TileLayoutConfig(),
    @SerialName("deviceTileLayouts") val deviceTileLayouts: Map<String, TileLayoutConfig> = emptyMap(),
) {

    fun isCollapsed(deviceId: String): Boolean = collapsedDevices.contains(deviceId)

    fun effectiveLayout(deviceId: String): TileLayoutConfig =
        deviceTileLayouts[deviceId] ?: defaultTileLayout

    fun toToggledCollapsed(deviceId: String): DashboardConfig = copy(
        collapsedDevices = if (isCollapsed(deviceId)) {
            collapsedDevices - deviceId
        } else {
            collapsedDevices + deviceId
        }
    )

    fun toUpdatedOrder(newOrder: List<String>): DashboardConfig = copy(
        deviceOrder = newOrder
    )

    fun toCleaned(existingDeviceIds: Set<String>): DashboardConfig = copy(
        collapsedDevices = collapsedDevices.intersect(existingDeviceIds),
        deviceOrder = deviceOrder.filter { existingDeviceIds.contains(it) },
        deviceTileLayouts = deviceTileLayouts.filterKeys { it in existingDeviceIds },
    )

    fun applyCustomOrdering(deviceIds: List<String>): List<String> {
        if (deviceOrder.isEmpty()) return deviceIds

        val deviceSet = deviceIds.toSet()
        val ordered = mutableListOf<String>()

        // Add devices in custom order
        deviceOrder.forEach { deviceId ->
            if (deviceSet.contains(deviceId)) {
                ordered.add(deviceId)
            }
        }

        // Add any remaining devices not in the custom order
        deviceIds.forEach { deviceId ->
            if (!ordered.contains(deviceId)) {
                ordered.add(deviceId)
            }
        }

        return ordered
    }
}

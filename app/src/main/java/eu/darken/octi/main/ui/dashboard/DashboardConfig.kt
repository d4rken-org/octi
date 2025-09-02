package eu.darken.octi.main.ui.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DashboardConfig(
    @Json(name = "collapsedDevices") val collapsedDevices: Set<String> = emptySet(),
    @Json(name = "deviceOrder") val deviceOrder: List<String> = emptyList(),
) {
    
    fun isCollapsed(deviceId: String): Boolean = collapsedDevices.contains(deviceId)
    
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
        deviceOrder = deviceOrder.filter { existingDeviceIds.contains(it) }
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
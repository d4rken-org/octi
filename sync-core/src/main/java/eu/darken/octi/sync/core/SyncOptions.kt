package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId

data class SyncOptions(
    val stats: Boolean = true,
    val readData: Boolean = true,
    val writeData: Boolean = true,
    val writePayload: List<SyncWrite.Device.Module> = emptyList(),
    val moduleFilter: Set<ModuleId>? = null,
    val deviceFilter: Set<DeviceId>? = null,
)
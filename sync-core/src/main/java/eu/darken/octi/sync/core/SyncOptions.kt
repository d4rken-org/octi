package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId

data class SyncOptions(
    val stats: Boolean = true,
    val readData: Boolean = true,
    val writeData: Boolean = true,
    val writePayload: List<ModuleWrite> = emptyList(),
    val moduleFilter: Set<ModuleId>? = null,
    val deviceFilter: Set<DeviceId>? = null,
) {

    data class ModuleWrite(
        val module: SyncWrite.Device.Module,
        val expectedHash: String,
    )

    val logLabel: String
        get() = buildString {
            append("SyncOptions(")
            append("read=$readData, write=$writeData, stats=$stats")
            if (writePayload.isNotEmpty()) append(", payload=${writePayload.size}")
            moduleFilter?.let { append(", modules=${it.size}") }
            deviceFilter?.let { append(", devices=${it.size}") }
            append(")")
        }
}

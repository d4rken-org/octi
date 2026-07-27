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

    /**
     * Union of two requests — the result satisfies both.
     *
     * Booleans OR, filters union, and a `null` filter (meaning "everything") absorbs a narrower
     * one. Never intersect: a request that is coalesced into another must not lose scope, e.g. a
     * write-only request must not swallow a caller's full read.
     */
    fun merge(other: SyncOptions): SyncOptions = SyncOptions(
        stats = stats || other.stats,
        readData = readData || other.readData,
        writeData = writeData || other.writeData,
        writePayload = mergePayloads(writePayload, other.writePayload),
        moduleFilter = unionOrAll(moduleFilter, other.moduleFilter),
        deviceFilter = unionOrAll(deviceFilter, other.deviceFilter),
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

    companion object {
        private fun <T> unionOrAll(a: Set<T>?, b: Set<T>?): Set<T>? = when {
            a == null || b == null -> null
            else -> a + b
        }

        /** Latest payload per module wins — [b] is the newer request. */
        private fun mergePayloads(a: List<ModuleWrite>, b: List<ModuleWrite>): List<ModuleWrite> {
            if (b.isEmpty()) return a
            if (a.isEmpty()) return b
            val byModule = LinkedHashMap<ModuleId, ModuleWrite>()
            a.forEach { byModule[it.module.moduleId] = it }
            b.forEach { byModule[it.module.moduleId] = it }
            return byModule.values.toList()
        }
    }
}

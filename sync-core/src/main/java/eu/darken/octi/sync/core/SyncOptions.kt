package eu.darken.octi.sync.core

data class SyncOptions(
    val stats: Boolean = true,
    val readData: Boolean = true,
    val writeData: Boolean = true,
)
package eu.darken.octi.sync.core

interface ModuleRepo<T> {
    suspend fun updateSelf(self: SyncDataContainer<T>)

    suspend fun updateOthers(newOthers: Collection<SyncDataContainer<T>>)
}
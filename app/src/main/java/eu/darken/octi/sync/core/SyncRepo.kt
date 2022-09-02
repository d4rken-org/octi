package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.provider.gdrive.GDriveHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val syncOptions: SyncOptions,
    private val gDriveHub: GDriveHub,
) {

    val connectors = gDriveHub.connectors
    val syncData = gDriveHub.data

    suspend fun syncAll() {
        log(TAG) { "syncAll()" }
        connectors.first().forEach {
            it.sync()
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Repo")
    }
}
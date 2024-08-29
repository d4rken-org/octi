package eu.darken.octi.syncs.gdrive.core

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GDriveHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val accountRepo: GoogleAccountRepo,
    private val connectorFactory: GDriveAppDataConnector.Factory,
    private val syncSettings: SyncSettings,
) : ConnectorHub {

    private val _connectors: Flow<Collection<GDriveAppDataConnector>> = accountRepo.accounts
        .mapLatest { acc ->
            acc.map { connectorFactory.create(it) }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.Default)

    override val connectors: Flow<Collection<SyncConnector>> = _connectors

    override suspend fun owns(connectorId: ConnectorId): Boolean {
        return _connectors.first().any { it.identifier == connectorId }
    }

    override suspend fun remove(connectorId: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "remove(id=$connectorId)" }
        val connector = _connectors.first().single { it.identifier == connectorId }
        try {
            connector.deleteDevice(syncSettings.deviceId)
        } catch (e: UserRecoverableAuthIOException) {
            // User was locked out
            log(TAG, ERROR) { "Failed to delete device, access was locked out:\n${e.asLog()}" }
            throw UserLockedOutException(e)
        }
        accountRepo.remove(connector.account.id)
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "Hub")
    }
}
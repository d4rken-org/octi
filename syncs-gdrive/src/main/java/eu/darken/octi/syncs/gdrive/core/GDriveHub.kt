package eu.darken.octi.syncs.gdrive.core

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.execute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GDriveHub @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val accountRepo: GoogleAccountRepo,
    private val connectorFactory: GDriveAppDataConnector.Factory,
    private val syncSettings: SyncSettings,
) : ConnectorHub {

    private data class Live(
        val connector: GDriveAppDataConnector,
        val scope: CoroutineScope,
        val processorJob: Job,
    )

    private val live = ConcurrentHashMap<GoogleAccount.Id, Live>()

    private val _connectors: Flow<Collection<GDriveAppDataConnector>> = accountRepo.accounts
        .map { accounts -> reconcile(accounts) }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(appScope + dispatcherProvider.Default)

    override val connectors: Flow<Collection<GDriveAppDataConnector>> = _connectors

    @Synchronized
    private fun reconcile(accounts: Collection<GoogleAccount>): Collection<GDriveAppDataConnector> {
        val currentKeys = accounts.map { it.id }.toSet()

        // Remove gone accounts — cancel their processor scope.
        val goneKeys = live.keys - currentKeys
        goneKeys.forEach { key ->
            live.remove(key)?.let {
                log(TAG, INFO) { "Tearing down connector for $key" }
                it.scope.cancel()
            }
        }

        // Add new accounts — spin up connector + processor on a connector-lifetime scope.
        accounts.forEach { account ->
            live.computeIfAbsent(account.id) {
                log(TAG, INFO) { "Creating connector for $account" }
                val connectorScope = CoroutineScope(appScope.coroutineContext + SupervisorJob())
                val connector = connectorFactory.create(account)
                val job = connector.start(connectorScope)
                // Kick off an initial read-only sync; fire-and-forget via the queue.
                connector.submit(ConnectorCommand.Sync(SyncOptions(writeData = false)))
                Live(connector, connectorScope, job)
            }
        }

        return live.values.map { it.connector }
    }

    override suspend fun owns(connectorId: ConnectorId): Boolean {
        return _connectors.first().any { it.identifier == connectorId }
    }

    override suspend fun remove(connectorId: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "remove(id=$connectorId)" }
        val connector = _connectors.first().singleOrNull { it.identifier == connectorId } ?: run {
            log(TAG, WARN) { "remove(id=$connectorId): connector not found" }
            return@withContext
        }
        try {
            connector.execute(ConnectorCommand.DeleteDevice(syncSettings.deviceId))
            accountRepo.remove(connector.account.id)
            // accountRepo.remove will trigger accountRepo.accounts re-emission, which reconcile()
            // will observe — causing the connector scope to be cancelled there.
        } catch (e: UserRecoverableAuthIOException) {
            log(TAG, ERROR) { "Failed to delete device, access was locked out:\n${e.asLog()}" }
            throw UserLockedOutException(e)
        }
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "Hub")
    }
}

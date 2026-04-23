package eu.darken.octi.syncs.octiserver.core

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
import eu.darken.octi.sync.core.SyncConnector
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
class OctiServerHub @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val accountRepo: OctiServerAccountRepo,
    private val connectorFactory: OctiServerConnector.Factory,
    private val endpointFactory: OctiServerEndpoint.Factory,
    private val syncSettings: SyncSettings,
) : ConnectorHub {

    private data class Live(
        val connector: OctiServerConnector,
        val scope: CoroutineScope,
        val processorJob: Job,
    )

    private val live = ConcurrentHashMap<OctiServer.Credentials.AccountId, Live>()

    private val _connectors: Flow<Collection<SyncConnector>> = accountRepo.accounts
        .map { accounts -> reconcile(accounts) }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(appScope + dispatcherProvider.Default)

    override val connectors: Flow<Collection<SyncConnector>> = _connectors

    @Synchronized
    private fun reconcile(accounts: Collection<OctiServer.Credentials>): Collection<SyncConnector> {
        val currentKeys = accounts.map { it.accountId }.toSet()

        val goneKeys = live.keys - currentKeys
        goneKeys.forEach { key ->
            live.remove(key)?.let {
                log(TAG, INFO) { "Tearing down connector for $key" }
                it.scope.cancel()
            }
        }

        accounts.forEach { creds ->
            live.computeIfAbsent(creds.accountId) {
                log(TAG, INFO) { "Creating connector for ${creds.serverAdress.domain}:${creds.accountId.id}" }
                val connectorScope = CoroutineScope(appScope.coroutineContext + SupervisorJob())
                val connector = connectorFactory.create(creds)
                val job = connector.start(connectorScope)
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
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to delete ourselves from $connectorId: ${e.asLog()}" }
        }
        accountRepo.remove((connector as OctiServerConnector).credentials.accountId)
    }

    suspend fun linkAcount(linkingData: LinkingData) = withContext(NonCancellable) {
        log(TAG) { "linkAccount(server=${linkingData.serverAdress.domain})" }

        val endPoint = endpointFactory.create(linkingData.serverAdress)

        val linkedAccount = endPoint.linkToExistingAccount(
            linkingData.linkCode,
        )

        val newCredentials = OctiServer.Credentials(
            serverAdress = linkingData.serverAdress,
            accountId = linkedAccount.accountId,
            devicePassword = linkedAccount.devicePassword,
            encryptionKeyset = linkingData.encryptionKeyset,
        )

        log(TAG) { "Account successfully linked: server=${newCredentials.serverAdress.domain}, account=${newCredentials.accountId}" }

        accountRepo.add(newCredentials)
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "Hub")
    }
}

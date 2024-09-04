package eu.darken.octi.syncs.kserver.core

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
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KServerHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val accountRepo: KServerAccountRepo,
    private val connectorFactory: KServerConnector.Factory,
    private val endpointFactory: KServerEndpoint.Factory,
    private val syncSettings: SyncSettings,
) : ConnectorHub {

    private val _connectors = accountRepo.accounts
        .mapLatest { acc ->
            acc.map { connectorFactory.create(it) }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.Default)

    override val connectors: Flow<Collection<SyncConnector>> = _connectors

    init {
        _connectors
            .drop(1) // Initial launch
            .distinctUntilChangedBy { connectors -> connectors.map { it.credentials } }
            .map { connectors -> connectors.filter { it.data.first() == null } }
            .onEach { connectors ->
                // Connectors that have been added and have no data yet
                connectors.forEach { connector ->
                    log(TAG, INFO) { "Syncing initial data for ${connector.credentials}" }
                    connector.sync(SyncOptions())
                    log(TAG, INFO) { "Initial data sync done for ${connector.credentials}" }
                }
            }
            .catch { log(TAG, WARN) { "Initial sync flow failed\n${it.asLog()}" } }
            .launchIn(scope)
    }

    override suspend fun owns(connectorId: ConnectorId): Boolean {
        return _connectors.first().any { it.identifier == connectorId }
    }

    override suspend fun remove(connectorId: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "remove(id=$connectorId)" }
        val connector = _connectors.first().single { it.identifier == connectorId }
        try {
            connector.deleteDevice(syncSettings.deviceId)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to delete ourselves from $connectorId: ${e.asLog()}" }
        }
        accountRepo.remove(connector.credentials.accountId)
    }

    suspend fun linkAcount(linkingData: LinkingData) = withContext(NonCancellable) {
        log(TAG) { "linkAccount(link=$linkingData)" }

        val endPoint = endpointFactory.create(linkingData.serverAdress)

        val linkedAccount = endPoint.linkToExistingAccount(
            linkingData.linkCode,
        )

        val newCredentials = KServer.Credentials(
            serverAdress = linkingData.serverAdress,
            accountId = linkedAccount.accountId,
            devicePassword = linkedAccount.devicePassword,
            encryptionKeyset = linkingData.encryptionKeyset,
        )

        log(TAG) { "Account successfully linked: $newCredentials" }

        accountRepo.add(newCredentials)
    }

    companion object {
        private val TAG = logTag("Sync", "KServer", "Hub")
    }
}
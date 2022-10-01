package eu.darken.octi.syncs.jserver.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncConnector
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
class JServerHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val accountRepo: JServerAccountRepo,
    private val connectorFactory: JServerConnector.Factory,
    private val endpointFactory: JServerEndpoint.Factory,
) : ConnectorHub {

    private val _connectors = accountRepo.accounts
        .mapLatest { acc ->
            acc.map { connectorFactory.create(it) }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.Default)

    override val connectors: Flow<Collection<SyncConnector>> = _connectors

    override suspend fun owns(connectorId: ConnectorId): Boolean {
        return _connectors.first().any { it.identifier == connectorId }
    }

    override suspend fun remove(connectorId: ConnectorId) {
        log(TAG) { "remove(id=$connectorId)" }
        val connector = _connectors.first().single { it.identifier == connectorId }
        accountRepo.remove(connector.credentials.accountId)
    }

    suspend fun linkAcount(linkingData: LinkingData) = withContext(NonCancellable) {
        log(TAG) { "linkAccount(link=$linkingData)" }

        val endPoint = endpointFactory.create(linkingData.serverAdress)

        val linkedAccount = endPoint.linkToExistingAccount(
            linkingData.linkCode,
        )

        val newCredentials = JServer.Credentials(
            serverAdress = linkingData.serverAdress,
            accountId = linkedAccount.accountId,
            devicePassword = linkedAccount.devicePassword,
            encryptionKeyset = linkingData.encryptionKeyset,
        )

        log(TAG) { "Account successfully linked: $newCredentials" }

        accountRepo.add(newCredentials)
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Hub")
    }
}
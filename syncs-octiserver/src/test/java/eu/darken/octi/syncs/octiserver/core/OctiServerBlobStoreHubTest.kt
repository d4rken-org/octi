package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class OctiServerBlobStoreHubTest : BaseTest() {

    private val octiServerHub: OctiServerHub = mockk()
    private val blobStoreFactory: OctiServerBlobStore.Factory = mockk()
    private val storageStatusProviderFactory: OctiServerStorageStatusProvider.Factory = mockk(relaxed = true)
    private val endpointFactory: OctiServerEndpoint.Factory = mockk()
    private val endpoint: OctiServerEndpoint = mockk()

    private val legacyKeyset = PayloadEncryption.KeySet(
        type = "AES256_SIV",
        key = "CMyhkP8HEoQBCngKMHR5cGUuZ29vZ2xlYXBpcy5jb20vZ29vZ2xlLmNyeXB0by50aW5rLkFlc1NpdktleRJCEkDAEayVsnPs8JIV0wrVP3EuGuM8dEkIxw0gqyuNJGDqJAQoAJB44ZS9JayECYZ/mUv13oslpQ+Vxjj98je6InGMGAEQARjMoZD/ByAB".decodeBase64()!!,
    )

    private fun credentialsWith(keyset: PayloadEncryption.KeySet) = OctiServer.Credentials(
        serverAdress = OctiServer.Address(domain = "example.com"),
        accountId = OctiServer.Credentials.AccountId(id = "acc-1"),
        devicePassword = OctiServer.Credentials.DevicePassword(password = "secret"),
        encryptionKeyset = keyset,
    )

    private fun mockConnector(credentials: OctiServer.Credentials): OctiServerConnector {
        val connectorId = ConnectorId(
            type = ConnectorType.OCTISERVER,
            subtype = credentials.serverAdress.domain,
            account = credentials.accountId.id,
        )
        return mockk<OctiServerConnector>().also {
            every { it.identifier } returns connectorId
            every { it.credentials } returns credentials
        }
    }

    private fun stubEndpointFactory() {
        every { endpointFactory.create(any()) } returns endpoint
        every { endpoint.setCredentials(any()) } just Runs
    }

    private fun newHub(backgroundScope: kotlinx.coroutines.CoroutineScope) = OctiServerBlobStoreHub(
        scope = backgroundScope,
        dispatcherProvider = TestDispatcherProvider(),
        octiServerHub = octiServerHub,
        blobStoreFactory = blobStoreFactory,
        storageStatusProviderFactory = storageStatusProviderFactory,
        endpointFactory = endpointFactory,
    )

    @Test
    fun `SUPPORTED capability with GCM-SIV keyset exposes a store`() = runTest2 {
        val connectors = MutableStateFlow<Collection<SyncConnector>>(emptyList())
        every { octiServerHub.connectors } returns connectors
        stubEndpointFactory()
        coEvery { endpoint.resolveCapabilities() } returns OctiServerCapabilities(
            blobSupport = OctiServerCapabilities.BlobSupport.SUPPORTED,
            storageApiVersion = 1,
        )

        val credentials = credentialsWith(PayloadEncryption().exportKeyset())
        val connector = mockConnector(credentials)
        val mockStore: OctiServerBlobStore = mockk {
            every { connectorId } returns connector.identifier
        }
        every { blobStoreFactory.create(credentials, endpoint, any(), any()) } returns mockStore

        val hub = newHub(backgroundScope)

        connectors.value = listOf(connector)

        val result = hub.blobStores.first()
        result.toList() shouldContainExactly listOf(mockStore)
    }

    @Test
    fun `LEGACY capability excludes connector without invoking the factory`() = runTest2 {
        val connectors = MutableStateFlow<Collection<SyncConnector>>(emptyList())
        every { octiServerHub.connectors } returns connectors
        stubEndpointFactory()
        coEvery { endpoint.resolveCapabilities() } returns OctiServerCapabilities(
            blobSupport = OctiServerCapabilities.BlobSupport.LEGACY,
        )

        val hub = newHub(backgroundScope)

        val credentials = credentialsWith(PayloadEncryption().exportKeyset())
        connectors.value = listOf(mockConnector(credentials))

        val result = hub.blobStores.first()
        result.toList().shouldBeEmpty()
        verify(exactly = 0) { blobStoreFactory.create(any(), any(), any(), any()) }
    }

    @Test
    fun `factory IllegalArgumentException filters out the connector`() = runTest2 {
        val connectors = MutableStateFlow<Collection<SyncConnector>>(emptyList())
        every { octiServerHub.connectors } returns connectors
        stubEndpointFactory()
        coEvery { endpoint.resolveCapabilities() } returns OctiServerCapabilities(
            blobSupport = OctiServerCapabilities.BlobSupport.SUPPORTED,
            storageApiVersion = 1,
        )

        val credentials = credentialsWith(legacyKeyset)
        every { blobStoreFactory.create(credentials, endpoint, any(), any()) } throws
            IllegalArgumentException("Only AES256_GCM_SIV keysets are supported for blob storage (was: AES256_SIV)")

        val hub = newHub(backgroundScope)

        connectors.value = listOf(mockConnector(credentials))

        val result = hub.blobStores.first()
        result.toList().shouldBeEmpty()
    }

    @Test
    fun `memoizeCapabilities with LEGACY retires an already-exposed store`() = runTest2 {
        val connectors = MutableStateFlow<Collection<SyncConnector>>(emptyList())
        every { octiServerHub.connectors } returns connectors
        stubEndpointFactory()
        coEvery { endpoint.resolveCapabilities() } returns OctiServerCapabilities(
            blobSupport = OctiServerCapabilities.BlobSupport.SUPPORTED,
            storageApiVersion = 1,
        )

        val credentials = credentialsWith(PayloadEncryption().exportKeyset())
        val connector = mockConnector(credentials)
        val mockStore: OctiServerBlobStore = mockk {
            every { connectorId } returns connector.identifier
        }
        every { blobStoreFactory.create(credentials, endpoint, any(), any()) } returns mockStore

        val hub = newHub(backgroundScope)

        connectors.value = listOf(connector)

        // Prime: store is exposed under SUPPORTED.
        hub.blobStores.first { it.contains(mockStore) }

        // Demote capability out-of-band (e.g. the way put-on-legacy observations do).
        hub.memoizeCapabilities(
            connectorId = connector.identifier,
            capabilities = OctiServerCapabilities(blobSupport = OctiServerCapabilities.BlobSupport.LEGACY),
        )

        // The store should be retired from subsequent emissions.
        hub.blobStores.first { it.isEmpty() }
    }
}

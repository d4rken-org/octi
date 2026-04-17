package eu.darken.octi.sync.core.blob

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncSettings
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okio.Buffer
import okio.Sink
import okio.Source
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Clock

class BlobManagerTest : BaseTest() {

    private val connectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = "test.example.com",
        account = "acc-1",
    )
    private val deviceId = DeviceId("device-1")
    private val moduleId = ModuleId("module-1")
    private val blobKey = BlobKey("blob-1")
    private val metadata = BlobMetadata(
        size = 20,
        createdAt = Clock.System.now(),
        checksum = "abc",
    )

    @Test
    fun `put uses quota total bytes for preflight`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(maxTotalBytes = 1_000),
            quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 90, totalBytes = 100),
        )
        val manager = createManager(backgroundScope, store)

        val result = manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("payload") },
            metadata = metadata,
        )

        result.successful shouldBe emptySet()
        result.perConnectorErrors shouldContainKey connectorId
        result.perConnectorErrors.getValue(connectorId).shouldBeInstanceOf<BlobQuotaExceededException>()
        store.putCalls shouldBe 0
    }

    @Test
    fun `put failure records backoff and success clears it`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 0, totalBytes = 1_000),
            failOnPut = true,
        )
        val manager = createManager(backgroundScope, store)

        manager.isBackedOff(connectorId, blobKey) shouldBe false

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        manager.isBackedOff(connectorId, blobKey) shouldBe true

        store.failOnPut = false
        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        manager.isBackedOff(connectorId, blobKey) shouldBe false
    }

    @Test
    fun `quotas emits immediate placeholder before network quota resolves`() = runTest2 {
        val quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 10, totalBytes = 100)
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = quota,
            quotaDelayMs = 250,
        )
        val manager = createManager(backgroundScope, store)

        manager.quotas().first() shouldBe mapOf(connectorId to null)
        manager.quotas().drop(1).first() shouldBe mapOf(connectorId to quota)
    }

    private fun createManager(scope: CoroutineScope, vararg stores: BlobStore): BlobManager {
        val hub = object : BlobStoreHub {
            override val blobStores: Flow<Collection<BlobStore>> = flowOf(stores.toList())

            override suspend fun owns(connectorId: ConnectorId): Boolean = stores.any { it.connectorId == connectorId }
        }

        val syncSettings = mockk<SyncSettings>()
        every { syncSettings.pausedConnectors } returns mockk {
            every { flow } returns flowOf(emptySet())
        }

        return BlobManager(
            scope = scope,
            dispatcherProvider = TestDispatcherProvider(),
            blobStoreHubs = setOf(hub),
            syncSettings = syncSettings,
        )
    }

    private class FakeBlobStore(
        override val connectorId: ConnectorId,
        private val constraints: BlobStoreConstraints,
        private val quota: BlobStoreQuota?,
        private val quotaDelayMs: Long = 0,
        var failOnPut: Boolean = false,
    ) : BlobStore {
        var putCalls: Int = 0

        override suspend fun put(
            deviceId: DeviceId,
            moduleId: ModuleId,
            key: BlobKey,
            source: Source,
            metadata: BlobMetadata,
            onProgress: BlobProgressCallback?,
        ): RemoteBlobRef {
            putCalls += 1
            if (failOnPut) throw RuntimeException("put() failed (test)")
            return RemoteBlobRef(key.id)
        }

        override suspend fun get(
            deviceId: DeviceId,
            moduleId: ModuleId,
            key: BlobKey,
            remoteRef: RemoteBlobRef,
            sink: Sink,
            onProgress: BlobProgressCallback?,
        ): BlobMetadata {
            throw UnsupportedOperationException()
        }

        override suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef): BlobMetadata? {
            throw UnsupportedOperationException()
        }

        override suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef) = Unit

        override suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<RemoteBlobRef> = emptySet()

        override suspend fun getConstraints(): BlobStoreConstraints = constraints

        override suspend fun getQuota(): BlobStoreQuota? {
            if (quotaDelayMs > 0) {
                delay(quotaDelayMs)
            }
            return quota
        }
    }
}

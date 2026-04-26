package eu.darken.octi.sync.core.blob

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncSettings
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun createManager(
        scope: CoroutineScope,
        vararg stores: BlobStore,
        pausedConnectorsFlow: Flow<Set<ConnectorId>> = flowOf(emptySet()),
    ): BlobManager {
        val hub = object : BlobStoreHub {
            override val blobStores: Flow<Collection<BlobStore>> = flowOf(stores.toList())

            override suspend fun owns(connectorId: ConnectorId): Boolean = stores.any { it.connectorId == connectorId }
        }

        val syncSettings = mockk<SyncSettings>()
        every { syncSettings.pausedConnectors } returns mockk {
            every { flow } returns pausedConnectorsFlow
        }

        return BlobManager(
            scope = scope,
            dispatcherProvider = TestDispatcherProvider(),
            blobStoreHubs = setOf(hub),
            syncSettings = syncSettings,
        )
    }

    @Test
    fun `backoff schedule exhaustion marks state terminal`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 0, totalBytes = 1_000),
            failOnPut = true,
        )
        val manager = createManager(backgroundScope, store)

        // BACKOFF_SCHEDULE has 4 entries — failure #5 trips the terminal flag.
        repeat(5) {
            manager.put(
                deviceId = deviceId,
                moduleId = moduleId,
                blobKey = blobKey,
                openSource = { Buffer().writeUtf8("p") },
                metadata = metadata,
            )
        }

        manager.isBackedOff(connectorId, blobKey) shouldBe true
        val status = manager.retryStatus.first()[connectorId to blobKey]
        status shouldBe RetryStatus.Stopped
    }

    @Test
    fun `preflight FileTooLarge surfaces in retryStatus`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(maxFileBytes = 5),
            quota = null,
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata, // size = 20
        )

        val status = manager.retryStatus.first()[connectorId to blobKey]
        status.shouldBeInstanceOf<RetryStatus.FileTooLarge>()
        status.maxBytes shouldBe 5
    }

    @Test
    fun `preflight QuotaExceeded surfaces in retryStatus`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 95, totalBytes = 100),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata, // size = 20, used + size = 115 > 100
        )

        val status = manager.retryStatus.first()[connectorId to blobKey]
        status.shouldBeInstanceOf<RetryStatus.QuotaExceeded>()
        status.usedBytes shouldBe 95
        status.totalBytes shouldBe 100
    }

    @Test
    fun `runtime BlobQuotaExceededException routes to terminal QuotaExceeded`() = runTest2 {
        // Pre-flight passes (no quota), then put() throws BlobQuotaExceededException at runtime
        // (simulates server-side 507 with account_quota_exceeded).
        val serverQuota = BlobStoreQuota(connectorId = connectorId, usedBytes = 90, totalBytes = 100)
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = null, // pre-flight has no info
            throwOnPut = BlobQuotaExceededException(quota = serverQuota, requestedBytes = 20),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        // Goes straight to terminal QuotaExceeded — NOT through transient backoff.
        val status = manager.retryStatus.first()[connectorId to blobKey]
        status.shouldBeInstanceOf<RetryStatus.QuotaExceeded>()
        status.usedBytes shouldBe 90
        status.totalBytes shouldBe 100
        manager.isBackedOff(connectorId, blobKey) shouldBe true
    }

    @Test
    fun `runtime BlobServerStorageLowException routes to terminal ServerStorageLow`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = null,
            throwOnPut = BlobServerStorageLowException(connectorId = connectorId),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        manager.retryStatus.first()[connectorId to blobKey] shouldBe RetryStatus.ServerStorageLow
        manager.isBackedOff(connectorId, blobKey) shouldBe true
    }

    @Test
    fun `connectorRejections fires on ServerStorageLow and clears on next success`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = null,
            throwOnPut = BlobServerStorageLowException(connectorId = connectorId),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        manager.connectorRejections.first()[connectorId] shouldBe BlobManager.RejectionReason.ServerStorageLow

        // Server recovers — next put succeeds.
        store.throwOnPut = null
        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = BlobKey("blob-2"),
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        manager.connectorRejections.first() shouldNotContainKey connectorId
    }

    @Test
    fun `connectorRejections fires on AccountQuotaFull and survives clearBackoff`() = runTest2 {
        val serverQuota = BlobStoreQuota(connectorId = connectorId, usedBytes = 100, totalBytes = 100)
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = null,
            throwOnPut = BlobQuotaExceededException(quota = serverQuota, requestedBytes = 20),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        manager.connectorRejections.first()[connectorId] shouldBe BlobManager.RejectionReason.AccountQuotaFull

        // User taps Retry on the file → clearBackoff is called. The dashboard issue must persist
        // until an actual successful upload, NOT clear on backoff-clear alone.
        manager.clearBackoff(blobKey)

        manager.connectorRejections.first()[connectorId] shouldBe BlobManager.RejectionReason.AccountQuotaFull
    }

    @Test
    fun `paused-then-resumed clears non-preflight terminal but keeps preflight`() = runTest2 {
        val transientStore = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 0, totalBytes = 1_000),
            failOnPut = true,
        )
        val preflightConnectorId = connectorId.copy(account = "acc-2")
        val preflightStore = FakeBlobStore(
            connectorId = preflightConnectorId,
            constraints = BlobStoreConstraints(maxFileBytes = 5),
            quota = null,
        )
        val paused = MutableStateFlow<Set<ConnectorId>>(emptySet())
        val manager = createManager(
            backgroundScope,
            transientStore,
            preflightStore,
            pausedConnectorsFlow = paused,
        )

        // Drive both connectors to terminal: transient = exhaust schedule, preflight = single put.
        repeat(5) {
            manager.put(
                deviceId = deviceId,
                moduleId = moduleId,
                blobKey = blobKey,
                openSource = { Buffer().writeUtf8("p") },
                metadata = metadata,
            )
        }

        manager.retryStatus.first()[connectorId to blobKey] shouldBe RetryStatus.Stopped
        manager.retryStatus.first()[preflightConnectorId to blobKey].shouldBeInstanceOf<RetryStatus.FileTooLarge>()

        // Pause both, then resume both. Non-preflight terminal must clear; preflight must persist.
        paused.value = setOf(connectorId, preflightConnectorId)
        advanceUntilIdle()
        paused.value = emptySet()
        advanceUntilIdle()

        manager.isBackedOff(connectorId, blobKey) shouldBe false
        manager.retryStatus.first()[preflightConnectorId to blobKey].shouldBeInstanceOf<RetryStatus.FileTooLarge>()
    }

    @Test
    fun `clearBackoff by blobKey removes all per-connector entries for that blob`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            constraints = BlobStoreConstraints(),
            quota = BlobStoreQuota(connectorId = connectorId, usedBytes = 0, totalBytes = 1_000),
            failOnPut = true,
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )
        manager.isBackedOff(connectorId, blobKey) shouldBe true

        manager.clearBackoff(blobKey)
        manager.isBackedOff(connectorId, blobKey) shouldBe false
    }

    private class FakeBlobStore(
        override val connectorId: ConnectorId,
        private val constraints: BlobStoreConstraints,
        private val quota: BlobStoreQuota?,
        private val quotaDelayMs: Long = 0,
        var failOnPut: Boolean = false,
        var throwOnPut: Throwable? = null,
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
            throwOnPut?.let { throw it }
            if (failOnPut) throw RuntimeException("put() failed (test)")
            return RemoteBlobRef(key.id)
        }

        override suspend fun get(
            deviceId: DeviceId,
            moduleId: ModuleId,
            key: BlobKey,
            remoteRef: RemoteBlobRef,
            sink: Sink,
            expectedPlaintextSize: Long,
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

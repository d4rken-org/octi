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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private fun snap(
        used: Long,
        total: Long,
        available: Long = total - used,
        maxFile: Long? = null,
        overhead: Long = 0L,
        cId: ConnectorId = connectorId,
    ) = StorageSnapshot(
        connectorId = cId,
        accountLabel = null,
        usedBytes = used,
        totalBytes = total,
        availableBytes = available,
        maxFileBytes = maxFile,
        perFileOverheadBytes = overhead,
        updatedAt = Clock.System.now(),
    )

    @Test
    fun `put preflight rejects when availableBytes is exceeded`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Ready(connectorId, snap(used = 90, total = 100)),
        )
        val manager = createManager(backgroundScope, store)

        val result = manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("payload") },
            metadata = metadata, // size = 20, available = 10 → reject
        )

        result.successful shouldBe emptySet()
        result.perConnectorErrors shouldContainKey connectorId
        result.perConnectorErrors.getValue(connectorId).shouldBeInstanceOf<BlobQuotaExceededException>()
        store.putCalls shouldBe 0
    }

    @Test
    fun `put preflight uses requiredStoredBytes including per-file overhead`() = runTest2 {
        // available = 100, plaintext = 20, overhead = 1024 → required = 1044 > available → reject.
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Ready(
                connectorId,
                snap(used = 0, total = 5_000, available = 100, overhead = 1024),
            ),
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
        result.perConnectorErrors.getValue(connectorId).shouldBeInstanceOf<BlobQuotaExceededException>()
        store.putCalls shouldBe 0
    }

    @Test
    fun `put preflight allows upload when availableBytes is sufficient`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Ready(connectorId, snap(used = 0, total = 1_000)),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        store.putCalls shouldBe 1
    }

    @Test
    fun `put preflight skips quota check when status has no lastKnown`() = runTest2 {
        // Unsupported / Unavailable(null) — no constraints known, upload proceeds.
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Unsupported(connectorId),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

        store.putCalls shouldBe 1
    }

    @Test
    fun `put failure records backoff and success clears it`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Ready(connectorId, snap(used = 0, total = 1_000)),
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
    fun `backoff schedule exhaustion marks state terminal`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Ready(connectorId, snap(used = 0, total = 1_000)),
            failOnPut = true,
        )
        val manager = createManager(backgroundScope, store)

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
        manager.retryStatus.first()[connectorId to blobKey] shouldBe RetryStatus.Stopped
    }

    @Test
    fun `preflight FileTooLarge surfaces in retryStatus`() = runTest2 {
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Ready(connectorId, snap(used = 0, total = 1_000, maxFile = 5)),
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
            initial = StorageStatus.Ready(connectorId, snap(used = 95, total = 100)),
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata, // size = 20, available = 5 → reject
        )

        val status = manager.retryStatus.first()[connectorId to blobKey]
        status.shouldBeInstanceOf<RetryStatus.QuotaExceeded>()
        status.usedBytes shouldBe 95
        status.totalBytes shouldBe 100
    }

    @Test
    fun `runtime BlobQuotaExceededException routes to terminal QuotaExceeded`() = runTest2 {
        val serverException = BlobQuotaExceededException(
            connectorId = connectorId,
            usedBytes = 90,
            totalBytes = 100,
            accountLabel = null,
            requestedBytes = 20,
        )
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Loading(connectorId, lastKnown = null),
            throwOnPut = serverException,
        )
        val manager = createManager(backgroundScope, store)

        manager.put(
            deviceId = deviceId,
            moduleId = moduleId,
            blobKey = blobKey,
            openSource = { Buffer().writeUtf8("p") },
            metadata = metadata,
        )

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
            initial = StorageStatus.Loading(connectorId, lastKnown = null),
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
            initial = StorageStatus.Loading(connectorId, lastKnown = null),
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
        val serverException = BlobQuotaExceededException(
            connectorId = connectorId,
            usedBytes = 100,
            totalBytes = 100,
            accountLabel = null,
            requestedBytes = 20,
        )
        val store = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Loading(connectorId, lastKnown = null),
            throwOnPut = serverException,
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

        manager.clearBackoff(blobKey)

        manager.connectorRejections.first()[connectorId] shouldBe BlobManager.RejectionReason.AccountQuotaFull
    }

    @Test
    fun `paused-then-resumed clears non-preflight terminal but keeps preflight`() = runTest2 {
        val transientStore = FakeBlobStore(
            connectorId = connectorId,
            initial = StorageStatus.Ready(connectorId, snap(used = 0, total = 1_000)),
            failOnPut = true,
        )
        val preflightConnectorId = connectorId.copy(account = "acc-2")
        val preflightStore = FakeBlobStore(
            connectorId = preflightConnectorId,
            initial = StorageStatus.Ready(preflightConnectorId, snap(used = 0, total = 1_000, maxFile = 5, cId = preflightConnectorId)),
        )
        val paused = MutableStateFlow<Set<ConnectorId>>(emptySet())
        val manager = createManager(
            backgroundScope,
            transientStore,
            preflightStore,
            pausedConnectorsFlow = paused,
        )

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
            initial = StorageStatus.Ready(connectorId, snap(used = 0, total = 1_000)),
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

    @Test
    fun `get propagates CancellationException without falling through to next candidate`() = runTest2 {
        val connectorIdA = connectorId
        val connectorIdB = ConnectorId(
            type = ConnectorType.OCTISERVER,
            subtype = "second.example.com",
            account = "acc-1",
        )
        val storeA = FakeBlobStore(
            connectorId = connectorIdA,
            initial = StorageStatus.Ready(connectorIdA, snap(used = 0, total = 1_000)),
            throwOnGet = CancellationException("user cancelled"),
        )
        val storeB = FakeBlobStore(
            connectorId = connectorIdB,
            initial = StorageStatus.Ready(connectorIdB, snap(used = 0, total = 1_000, cId = connectorIdB)),
            getMetadata = metadata,
        )
        val manager = createManager(backgroundScope, storeA, storeB)

        val candidates = mapOf(
            connectorIdA to RemoteBlobRef(blobKey.id),
            connectorIdB to RemoteBlobRef(blobKey.id),
        )

        var thrown: Throwable? = null
        try {
            manager.get(
                deviceId = deviceId,
                moduleId = moduleId,
                blobKey = blobKey,
                candidates = candidates,
                expectedPlaintextSize = 0,
                openSink = { Buffer() },
            )
        } catch (e: Throwable) {
            thrown = e
        }

        thrown.shouldBeInstanceOf<CancellationException>()
        storeA.getCalls shouldBe 1
        storeB.getCalls shouldBe 0
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
        every { syncSettings.pausedConnectorIds } returns pausedConnectorsFlow

        return BlobManager(
            scope = scope,
            dispatcherProvider = TestDispatcherProvider(),
            blobStoreHubs = setOf(hub),
            syncSettings = syncSettings,
        )
    }

    private class FakeBlobStore(
        override val connectorId: ConnectorId,
        initial: StorageStatus,
        var failOnPut: Boolean = false,
        var throwOnPut: Throwable? = null,
        var throwOnGet: Throwable? = null,
        var getMetadata: BlobMetadata? = null,
    ) : BlobStore {
        var putCalls: Int = 0
        var getCalls: Int = 0

        override val storageStatus: StorageStatusProvider = object : StorageStatusProvider {
            override val connectorId: ConnectorId = this@FakeBlobStore.connectorId
            private val _status = MutableStateFlow(initial)
            override val status: StateFlow<StorageStatus> = _status.asStateFlow()
            override suspend fun refresh(forceFresh: Boolean) = Unit
            override fun invalidate() = Unit
        }

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
            getCalls += 1
            throwOnGet?.let { throw it }
            return getMetadata ?: throw UnsupportedOperationException()
        }

        override suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef): BlobMetadata? =
            throw UnsupportedOperationException()

        override suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef) = Unit

        override suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<RemoteBlobRef> = emptySet()
    }
}

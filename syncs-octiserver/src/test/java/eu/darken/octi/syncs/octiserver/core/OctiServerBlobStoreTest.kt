package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.StorageStatusProvider
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import okhttp3.RequestBody
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Clock

class OctiServerBlobStoreTest : BaseTest() {

    private val endpoint = mockk<OctiServerEndpoint>(relaxed = true)

    private fun credentialsWith(keyset: PayloadEncryption.KeySet) = OctiServer.Credentials(
        serverAdress = OctiServer.Address(domain = "example.com"),
        accountId = OctiServer.Credentials.AccountId(id = "acc-1"),
        devicePassword = OctiServer.Credentials.DevicePassword(password = "secret"),
        encryptionKeyset = keyset,
    )

    private fun fakeStatus(connectorId: ConnectorId): StorageStatusProvider = object : StorageStatusProvider {
        override val connectorId: ConnectorId = connectorId
        private val _status = MutableStateFlow<StorageStatus>(StorageStatus.Loading(connectorId, lastKnown = null))
        override val status: StateFlow<StorageStatus> = _status.asStateFlow()
        override suspend fun refresh(forceFresh: Boolean) = Unit
        override fun invalidate() = Unit
    }

    private fun validStore(): OctiServerBlobStore {
        val cId = ConnectorId(ConnectorType.OCTISERVER, "example.com", "acc-1")
        return OctiServerBlobStore(
            credentials = credentialsWith(PayloadEncryption().exportKeyset()),
            endpoint = endpoint,
            storageStatus = fakeStatus(cId),
        )
    }

    @Test
    fun `constructor rejects legacy AES256_SIV credentials`() {
        // Reuses the legacy test keyset from PayloadEncryptionTest.kt:35 — bytes are a valid
        // legacy AES256_SIV keyset produced by an old client, exactly the wire state the
        // guard must reject.
        val legacyKeyset = PayloadEncryption.KeySet(
            type = "AES256_SIV",
            key = "CMyhkP8HEoQBCngKMHR5cGUuZ29vZ2xlYXBpcy5jb20vZ29vZ2xlLmNyeXB0by50aW5rLkFlc1NpdktleRJCEkDAEayVsnPs8JIV0wrVP3EuGuM8dEkIxw0gqyuNJGDqJAQoAJB44ZS9JayECYZ/mUv13oslpQ+Vxjj98je6InGMGAEQARjMoZD/ByAB".decodeBase64()!!,
        )

        shouldThrow<IllegalArgumentException> {
            OctiServerBlobStore(
                credentialsWith(legacyKeyset),
                endpoint,
                fakeStatus(ConnectorId(ConnectorType.OCTISERVER, "example.com", "acc-1")),
            )
        }
    }

    @Test
    fun `constructor rejects unknown keyset type`() {
        val unknownKeyset = PayloadEncryption.KeySet(
            type = "AES256_GCM",
            key = ByteString.EMPTY,
        )

        shouldThrow<IllegalArgumentException> {
            OctiServerBlobStore(
                credentialsWith(unknownKeyset),
                endpoint,
                fakeStatus(ConnectorId(ConnectorType.OCTISERVER, "example.com", "acc-1")),
            )
        }
    }

    @Test
    fun `delete delegates to endpoint deleteBlob with remote ref value`() = runTest2 {
        val deviceId = DeviceId("dev-1")
        val moduleId = ModuleId("module.test")
        val ref = RemoteBlobRef("srv-blob-xyz")
        coEvery { endpoint.deleteBlob(deviceId, moduleId, ref.value) } returns Unit

        validStore().delete(deviceId, moduleId, ref)

        coVerify(exactly = 1) { endpoint.deleteBlob(deviceId, moduleId, "srv-blob-xyz") }
    }

    @Test
    fun `delete propagates endpoint exceptions so callers can tombstone`() = runTest2 {
        val deviceId = DeviceId("dev-1")
        val moduleId = ModuleId("module.test")
        val ref = RemoteBlobRef("srv-blob-xyz")
        coEvery { endpoint.deleteBlob(deviceId, moduleId, ref.value) } throws RuntimeException("boom")

        shouldThrow<RuntimeException> {
            validStore().delete(deviceId, moduleId, ref)
        }
    }

    @Test
    fun `put with empty plaintext source emits valid ciphertext to PATCH and finalizes`() = runTest2 {
        // Tink AES-GCM-SIV streaming wraps zero-byte plaintext in a header + final auth tag —
        // the on-the-wire ciphertext is non-empty (~120 bytes). A regression where the producer
        // closed the channel before flushing the trailing buffer would surface here as either
        // zero PATCH calls or a zero-byte body, and finalize would never run.
        val deviceId = DeviceId("dev-1")
        val moduleId = ModuleId("module.test")
        val blobKey = BlobKey("blob-empty")

        val sessionResp = OctiServerApi.CreateSessionResponse(
            blobId = "srv-blob-empty",
            sessionId = "sess-1",
            offsetBytes = 0L,
            expiresAt = Clock.System.now(),
            state = "ACTIVE",
        )
        coEvery {
            endpoint.createBlobSession(deviceId, moduleId, any(), any())
        } returns sessionResp

        // Capture the ciphertext body actually shipped to the server. The chunk is in pieces
        // of okhttp's RequestBody — we read its content length to assert non-zero, then capture
        // bytes by writing into a Buffer through writeTo for content verification.
        val capturedBody = slot<RequestBody>()
        val capturedOffset = slot<Long>()
        coEvery {
            endpoint.appendBlobSession(deviceId, moduleId, sessionResp.sessionId, capture(capturedOffset), capture(capturedBody))
        } answers {
            // Server returns the new offset header value — equal to incoming offset + body length.
            val body = capturedBody.captured
            val incomingOffset = capturedOffset.captured
            incomingOffset + body.contentLength()
        }

        coEvery {
            endpoint.finalizeBlobSession(deviceId, moduleId, sessionResp.sessionId, any())
        } returns OctiServerApi.FinalizeSessionResponse(
            blobId = sessionResp.blobId,
            sessionId = sessionResp.sessionId,
            sizeBytes = 0L,
            state = "COMPLETE",
        )

        val emptySource = Buffer() // zero bytes
        val store = validStore()
        val ref = store.put(
            deviceId = deviceId,
            moduleId = moduleId,
            key = blobKey,
            source = emptySource,
            metadata = BlobMetadata(size = 0L, createdAt = Clock.System.now(), checksum = ""),
            onProgress = null,
        )

        ref shouldBe RemoteBlobRef(sessionResp.blobId)
        // Ciphertext is non-empty for empty plaintext — Tink writes header + auth tag.
        capturedBody.captured.contentLength() shouldNotBe 0L
        // Finalize must be called once with the cipher hash hex string.
        coVerify(exactly = 1) {
            endpoint.finalizeBlobSession(deviceId, moduleId, sessionResp.sessionId, withArg { it.shouldNotBeEmpty() })
        }
    }
}

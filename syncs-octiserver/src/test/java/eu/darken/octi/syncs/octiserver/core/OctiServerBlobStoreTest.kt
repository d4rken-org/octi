package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class OctiServerBlobStoreTest : BaseTest() {

    private val blobCacheDirs = mockk<BlobCacheDirs>(relaxed = true)
    private val endpoint = mockk<OctiServerEndpoint>(relaxed = true)

    private fun credentialsWith(keyset: PayloadEncryption.KeySet) = OctiServer.Credentials(
        serverAdress = OctiServer.Address(domain = "example.com"),
        accountId = OctiServer.Credentials.AccountId(id = "acc-1"),
        devicePassword = OctiServer.Credentials.DevicePassword(password = "secret"),
        encryptionKeyset = keyset,
    )

    private fun validStore(): OctiServerBlobStore = OctiServerBlobStore(
        blobCacheDirs = blobCacheDirs,
        credentials = credentialsWith(PayloadEncryption().exportKeyset()),
        endpoint = endpoint,
    )

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
            OctiServerBlobStore(blobCacheDirs, credentialsWith(legacyKeyset), endpoint)
        }
    }

    @Test
    fun `constructor rejects unknown keyset type`() {
        val unknownKeyset = PayloadEncryption.KeySet(
            type = "AES256_GCM",
            key = ByteString.EMPTY,
        )

        shouldThrow<IllegalArgumentException> {
            OctiServerBlobStore(blobCacheDirs, credentialsWith(unknownKeyset), endpoint)
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
}

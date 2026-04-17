package eu.darken.octi.syncs.octiserver.core

import android.content.Context
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.mockk
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class OctiServerBlobStoreTest : BaseTest() {

    private val context = mockk<Context>(relaxed = true)
    private val endpoint = mockk<OctiServerEndpoint>(relaxed = true)

    private fun credentialsWith(keyset: PayloadEncryption.KeySet) = OctiServer.Credentials(
        serverAdress = OctiServer.Address(domain = "example.com"),
        accountId = OctiServer.Credentials.AccountId(id = "acc-1"),
        devicePassword = OctiServer.Credentials.DevicePassword(password = "secret"),
        encryptionKeyset = keyset,
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
            OctiServerBlobStore(context, credentialsWith(legacyKeyset), endpoint)
        }
    }

    @Test
    fun `constructor rejects unknown keyset type`() {
        val unknownKeyset = PayloadEncryption.KeySet(
            type = "AES256_GCM",
            key = ByteString.EMPTY,
        )

        shouldThrow<IllegalArgumentException> {
            OctiServerBlobStore(context, credentialsWith(unknownKeyset), endpoint)
        }
    }
}

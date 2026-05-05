package eu.darken.octi.sync.core.encryption

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime-detected crypto features. Currently exposes whether AES-GCM-SIV is usable
 * end-to-end on this device — see [PayloadEncryption.gcmSivAvailable] for the underlying
 * detection. Exists so callers like account creation can be exercised in tests with both
 * `gcmSivAvailable=true` and `gcmSivAvailable=false` without touching JCE provider state.
 */
interface CryptoCapabilities {
    val gcmSivAvailable: Boolean
}

@Singleton
class RealCryptoCapabilities @Inject constructor() : CryptoCapabilities {
    override val gcmSivAvailable: Boolean
        // Reading the property forces [CryptoBootstrap] class load (and thus its init
        // block: JCE provider install + Tink registration + RFC 8452 postcondition) on
        // first access. App.onCreate injects this singleton early so the warmup is
        // deterministic.
        get() = CryptoBootstrap.gcmSivAvailable
}

@InstallIn(SingletonComponent::class)
@Module
abstract class CryptoCapabilitiesModule {
    @Binds
    abstract fun bindCryptoCapabilities(impl: RealCryptoCapabilities): CryptoCapabilities
}

package eu.darken.octi.common.upgrade.core

import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication
import kotlin.time.Instant

/**
 * The persist tests drive [FossCache]'s constructor seam against temp files, so this is the one
 * test that exercises the file-level `settings_foss` delegate the @Inject constructor actually
 * wires up — a delegate that got moved out of the class body to make that seam possible.
 *
 * One test method on purpose: DataStore forbids two active instances on the same file, and
 * FossCache is a @Singleton in production.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FossCacheWiringTest {

    @Test
    fun `the injected constructor reads and writes the settings_foss store`() = runTest {
        // The real DI Json config: the same instance production hands FossCache.
        val cache = FossCache(
            context = ApplicationProvider.getApplicationContext(),
            json = SerializationModule().json(),
        )

        cache.upgrade.value() shouldBe null

        val record = FossUpgrade(
            upgradedAt = Instant.fromEpochMilliseconds(0),
            upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        )
        cache.upgrade.value(record)
        cache.upgrade.value() shouldBe record
    }
}

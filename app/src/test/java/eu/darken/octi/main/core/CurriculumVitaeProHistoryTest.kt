package eu.darken.octi.main.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.serialization.SerializationModule
import eu.darken.octi.main.core.CurriculumVitae.ProState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Plain Application on purpose: the Hilt @HiltAndroidApp App builds its own CurriculumVitae, which
// would open a second live DataStore on the same file and trip "multiple DataStores active".
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class CurriculumVitaeProHistoryTest {

    // One test method on purpose: DataStore forbids two active instances on the same file, and
    // CurriculumVitae is a @Singleton in production.
    @Test
    fun `pro state transitions persist counters atomically and in order`() = runTest {
        // The real DI Json config: CV's existing values (e.g. the Instant install date) need the
        // contextual serializers.
        val serialization = SerializationModule()
        val cv = CurriculumVitae(
            context = ApplicationProvider.getApplicationContext(),
            appScope = CoroutineScope(Dispatchers.Unconfined),
            json = serialization.json(),
        )

        cv.proHistory() shouldBe CurriculumVitae.ProHistory(
            lastState = null,
            graceEngagedCount = 0,
            graceEngagedLast = null,
            proLostCount = 0,
            proLostLast = null,
        )

        // First observation is a baseline, not a transition.
        cv.updateProState(ProState.PURCHASED)
        cv.proHistory().apply {
            lastState shouldBe ProState.PURCHASED
            graceEngagedCount shouldBe 0
            proLostCount shouldBe 0
        }

        // Repeats are no-ops.
        cv.updateProState(ProState.PURCHASED)
        cv.proHistory().graceEngagedCount shouldBe 0

        // A rapid PURCHASED -> GRACE -> FREE episode: exactly one increment each, final state FREE.
        cv.updateProState(ProState.GRACE)
        cv.updateProState(ProState.FREE)
        cv.proHistory().apply {
            lastState shouldBe ProState.FREE
            graceEngagedCount shouldBe 1
            graceEngagedLast shouldNotBe null
            proLostCount shouldBe 1
            proLostLast shouldNotBe null
        }

        // Recovery doesn't count; a second full episode counts again.
        cv.updateProState(ProState.PURCHASED)
        cv.updateProState(ProState.GRACE)
        cv.updateProState(ProState.PURCHASED)
        cv.updateProState(ProState.FREE)
        cv.proHistory().apply {
            lastState shouldBe ProState.FREE
            graceEngagedCount shouldBe 2
            proLostCount shouldBe 2
        }
    }
}

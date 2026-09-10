package eu.darken.octi.common.debug.recording.core

import android.app.Application
import android.content.Context
import eu.darken.octi.common.debug.logging.Logging
import eu.darken.octi.common.upgrade.UpgradeDiagnostics
import eu.darken.octi.common.upgrade.core.BillingCache
import eu.darken.octi.common.upgrade.core.HangingPreferencesDataStore
import eu.darken.octi.common.upgrade.core.UpgradeDiagnosticsGplay
import eu.darken.octi.main.core.CurriculumVitae
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The GPlay diagnostics bound their own reads and answer "unavailable" rather than inventing a
 * verdict. The header's budget sits on top of those bounds, so it is the one that must not expire
 * first: an outer budget that cuts the read off drops exactly the evidence the inner bounds went to
 * the trouble of producing. Driven with the REAL [UpgradeDiagnosticsGplay] and the PRODUCTION
 * header budget for that reason — only the diagnostics' own inner bounds are seamed down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecorderModuleDiagnosticsGplayTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val logCapture = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            logLines.add(message)
        }
    }

    private val proHistory = CurriculumVitae.ProHistory(
        lastState = CurriculumVitae.ProState.PURCHASED,
        graceEngagedCount = 1,
        graceEngagedLast = null,
        proLostCount = 0,
        proLostLast = null,
    )

    @Before
    fun installLogCapture() {
        Logging.install(logCapture)
    }

    @After
    fun removeLogCapture() {
        Logging.remove(logCapture)
    }

    /**
     * Real dispatchers and the real recorder: the bounds under test are wall-clock, and the header
     * runs on a live recorder whose globally installed loggers have to be accounted for.
     */
    private fun withModule(
        upgradeDiagnostics: UpgradeDiagnostics,
        block: suspend (RecorderModule) -> Unit,
    ) {
        val loggersBefore = Logging.loggers
        val externalDir = tempFolder.newFolder("external")
        val cacheRoot = tempFolder.newFolder("cache")
        File(externalDir, "debug/logs").mkdirs()
        File(cacheRoot, "debug/logs").mkdirs()

        val context = mockk<Context>(relaxed = true)
        every { context.getExternalFilesDir(null) } returns externalDir
        every { context.cacheDir } returns cacheRoot

        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var module: RecorderModule? = null
        try {
            try {
                val created = RecorderModule(
                    context = context,
                    appScope = appScope,
                    dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
                    upgradeDiagnostics = upgradeDiagnostics,
                )
                module = created
                // Envelope: independent of every bound under test, so an ignored one fails this in
                // seconds instead of holding the gradle worker.
                runBlocking {
                    withTimeout(TEST_ENVELOPE_MS) { block(created) }
                }
            } finally {
                module?.let { runBlocking { withTimeoutOrNull(TEST_ENVELOPE_MS) { it.stopRecorder() } } }
            }
        } finally {
            appScope.cancel()
            val leaked = Logging.loggers - loggersBefore.toSet()
            leaked.forEach { Logging.remove(it) }
            leaked shouldBe emptyList<Logging.Logger>()
        }
    }

    private fun headerLine(): String = logLines.single { it.startsWith("Upgrade diagnostics: ") }

    @Test
    fun `a wedged billing cache reaches the header as unavailable`() {
        val diagnostics = UpgradeDiagnosticsGplay(
            billingCache = BillingCache(HangingPreferencesDataStore()).apply { cacheTimeoutMs = 50L },
            curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory },
        )

        withModule(diagnostics) { module ->
            module.startRecorder()

            headerLine() shouldContain "BillingCache=unavailable"
            // The header's own budget must never be the one that fires: it would replace the
            // verdict above with "we don't know", which is what the inner bound exists to avoid.
            logLines.any { it.contains("read did not finish") } shouldBe false
        }
    }

    @Test
    fun `a wedged pro-state history reaches the header as unavailable`() {
        // The second of the two sequential inner budgets: both run inside the one header budget.
        val diagnostics = UpgradeDiagnosticsGplay(
            billingCache = mockk<BillingCache>().apply {
                coEvery { snapshot() } returns BillingCache.Snapshot(
                    lastProStateAt = 0L,
                    lastProStateSku = "",
                    proUnconfirmedSince = 0L,
                )
            },
            curriculumVitae = mockk<CurriculumVitae>().apply {
                coEvery { proHistory() } coAnswers { awaitCancellation() }
            },
        ).apply { historyTimeoutMs = 50L }

        withModule(diagnostics) { module ->
            module.startRecorder()

            headerLine() shouldContain "ProHistory=unavailable"
            logLines.any { it.contains("read did not finish") } shouldBe false
        }
    }

    companion object {
        private const val TEST_ENVELOPE_MS = 10_000L
    }
}

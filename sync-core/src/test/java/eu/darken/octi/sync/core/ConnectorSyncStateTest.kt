package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.module.core.ModuleId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class ConnectorSyncStateTest : BaseTest() {

    private val connectorId1 = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc1")
    private val connectorId2 = ConnectorId(type = ConnectorType.GDRIVE, subtype = "test", account = "acc2")
    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private fun create(timeSource: TestTimeSource): ConnectorSyncState = ConnectorSyncState().apply {
        this.timeSource = timeSource
    }

    @Test
    fun `unknown pair has no record`() {
        val state = create(TestTimeSource())

        state.getRecord(connectorId1, powerModuleId).shouldBeNull()
    }

    @Test
    fun `record roundtrips hash and starts at zero age`() {
        val state = create(TestTimeSource())

        state.setHash(connectorId1, powerModuleId, "hash-1")

        state.getRecord(connectorId1, powerModuleId) shouldBe ConnectorSyncState.SentRecord("hash-1", ZERO)
    }

    @Test
    fun `age advances with the time source`() {
        val timeSource = TestTimeSource()
        val state = create(timeSource)

        state.setHash(connectorId1, powerModuleId, "hash-1")
        timeSource += 90.minutes

        state.getRecord(connectorId1, powerModuleId) shouldBe ConnectorSyncState.SentRecord("hash-1", 90.minutes)
    }

    @Test
    fun `setHash resets the age`() {
        val timeSource = TestTimeSource()
        val state = create(timeSource)

        state.setHash(connectorId1, powerModuleId, "hash-1")
        timeSource += 2.hours
        state.setHash(connectorId1, powerModuleId, "hash-2")

        state.getRecord(connectorId1, powerModuleId) shouldBe ConnectorSyncState.SentRecord("hash-2", ZERO)
    }

    @Test
    fun `getRecord returns one consistent snapshot`() {
        val timeSource = TestTimeSource()
        val state = create(timeSource)

        state.setHash(connectorId1, powerModuleId, "hash-1")
        timeSource += 3.hours
        val stale = state.getRecord(connectorId1, powerModuleId)!!

        state.setHash(connectorId1, powerModuleId, "hash-2")

        // The earlier snapshot is not mutated by the later write — hash and age moved together.
        stale.hash shouldBe "hash-1"
        stale.age shouldBe 3.hours
        state.getRecord(connectorId1, powerModuleId) shouldBe ConnectorSyncState.SentRecord("hash-2", ZERO)
    }

    @Test
    fun `records are tracked per connector and module`() {
        val timeSource = TestTimeSource()
        val state = create(timeSource)

        state.setHash(connectorId1, powerModuleId, "power-1")
        state.setHash(connectorId1, metaModuleId, "meta-1")
        state.setHash(connectorId2, powerModuleId, "power-2")

        state.getRecord(connectorId1, powerModuleId)!!.hash shouldBe "power-1"
        state.getRecord(connectorId1, metaModuleId)!!.hash shouldBe "meta-1"
        state.getRecord(connectorId2, powerModuleId)!!.hash shouldBe "power-2"
    }

    @Test
    fun `clearConnector drops only that connector's records`() {
        val timeSource = TestTimeSource()
        val state = create(timeSource)

        state.setHash(connectorId1, powerModuleId, "power-1")
        state.setHash(connectorId1, metaModuleId, "meta-1")
        state.setHash(connectorId2, powerModuleId, "power-2")

        state.clearConnector(connectorId1)

        state.getRecord(connectorId1, powerModuleId).shouldBeNull()
        state.getRecord(connectorId1, metaModuleId).shouldBeNull()
        state.getRecord(connectorId2, powerModuleId)!!.hash shouldBe "power-2"
    }
}

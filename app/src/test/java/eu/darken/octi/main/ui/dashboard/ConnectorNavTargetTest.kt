package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ConnectorNavTargetTest : BaseTest() {

    private val octiserver = ConnectorId(ConnectorType.OCTISERVER, "default", "alice")
    private val deviceA = DeviceId("device-a")

    private fun call(
        connectorId: ConnectorId = octiserver,
        deviceId: DeviceId? = null,
        isPaused: Boolean = false,
    ) = DashboardVM.connectorNavTarget(
        connectorId = connectorId,
        deviceId = deviceId,
        isPaused = isPaused,
    )

    @Nested
    inner class `paused connector` {
        @Test
        fun `with device id routes to the sync list`() {
            call(deviceId = deviceA, isPaused = true) shouldBe Nav.Sync.List
        }

        @Test
        fun `without device id routes to the sync list`() {
            call(deviceId = null, isPaused = true) shouldBe Nav.Sync.List
        }
    }

    @Nested
    inner class `active connector` {
        @Test
        fun `with device id routes to that device`() {
            call(deviceId = deviceA, isPaused = false) shouldBe Nav.Sync.Devices(
                connectorId = octiserver.idString,
                deviceId = deviceA.id,
            )
        }

        @Test
        fun `without device id routes to the connector devices`() {
            call(deviceId = null, isPaused = false) shouldBe Nav.Sync.Devices(
                connectorId = octiserver.idString,
                deviceId = null,
            )
        }
    }
}

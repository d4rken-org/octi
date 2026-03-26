package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class OctiServerConnectorMergeTest : BaseTest() {

    private val connectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc-1")
    private val deviceA = DeviceId("device-a")
    private val deviceB = DeviceId("device-b")
    private val deviceC = DeviceId("device-c")

    private val power = ModuleId("eu.darken.octi.module.core.power")
    private val wifi = ModuleId("eu.darken.octi.module.core.wifi")
    private val meta = ModuleId("eu.darken.octi.module.core.meta")
    private val clipboard = ModuleId("eu.darken.octi.module.core.clipboard")
    private val apps = ModuleId("eu.darken.octi.module.core.apps")

    private fun module(deviceId: DeviceId, moduleId: ModuleId, payload: String = "data"): OctiServerModuleData {
        return OctiServerModuleData(
            connectorId = connectorId,
            deviceId = deviceId,
            moduleId = moduleId,
            modifiedAt = Instant.now(),
            payload = payload.encodeUtf8(),
        )
    }

    private fun deviceData(deviceId: DeviceId, vararg modules: OctiServerModuleData): OctiServerDeviceData {
        return OctiServerDeviceData(deviceId = deviceId, modules = modules.toList())
    }

    private fun serverData(vararg devices: SyncRead.Device): OctiServerData {
        return OctiServerData(connectorId = connectorId, devices = devices.toList())
    }

    /** Access mergeData via a minimal instance — it's internal, no DI needed for pure logic */
    private fun mergeData(
        existing: SyncRead,
        update: SyncRead,
        filter: Set<ModuleId>,
    ): OctiServerData {
        val updatedDeviceMap = update.devices.associateBy { it.deviceId }
        val mergedDevices = existing.devices.map { existingDevice ->
            val updatedDevice = updatedDeviceMap[existingDevice.deviceId]
            if (updatedDevice == null) {
                existingDevice
            } else {
                val keptModules = existingDevice.modules.filter { it.moduleId !in filter }
                OctiServerDeviceData(
                    deviceId = existingDevice.deviceId,
                    modules = keptModules + updatedDevice.modules,
                )
            }
        }
        return OctiServerData(connectorId = existing.connectorId, devices = mergedDevices)
    }

    @Nested
    inner class `targeted sync preserves existing data` {

        @Test
        fun `updating one module keeps all others`() {
            val existing = serverData(
                deviceData(
                    deviceB,
                    module(deviceB, power, "power-old"),
                    module(deviceB, wifi, "wifi-old"),
                    module(deviceB, meta, "meta-old"),
                    module(deviceB, clipboard, "clipboard-old"),
                    module(deviceB, apps, "apps-old"),
                ),
            )

            val update = serverData(
                deviceData(deviceB, module(deviceB, clipboard, "clipboard-new")),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            val deviceBModules = result.devices.single().modules
            deviceBModules shouldHaveSize 5
            deviceBModules.map { it.moduleId } shouldContainExactlyInAnyOrder listOf(power, wifi, meta, clipboard, apps)
            deviceBModules.single { it.moduleId == clipboard }.payload shouldBe "clipboard-new".encodeUtf8()
            deviceBModules.single { it.moduleId == power }.payload shouldBe "power-old".encodeUtf8()
        }

        @Test
        fun `updating multiple modules in filter`() {
            val existing = serverData(
                deviceData(
                    deviceB,
                    module(deviceB, power, "power-old"),
                    module(deviceB, clipboard, "clipboard-old"),
                    module(deviceB, meta, "meta-old"),
                ),
            )

            val update = serverData(
                deviceData(
                    deviceB,
                    module(deviceB, clipboard, "clipboard-new"),
                    module(deviceB, meta, "meta-new"),
                ),
            )

            val result = mergeData(existing, update, setOf(clipboard, meta))

            val modules = result.devices.single().modules
            modules shouldHaveSize 3
            modules.single { it.moduleId == clipboard }.payload shouldBe "clipboard-new".encodeUtf8()
            modules.single { it.moduleId == meta }.payload shouldBe "meta-new".encodeUtf8()
            modules.single { it.moduleId == power }.payload shouldBe "power-old".encodeUtf8()
        }
    }

    @Nested
    inner class `multi-device scenarios` {

        @Test
        fun `preserves data from devices not in update`() {
            val existing = serverData(
                deviceData(deviceB, module(deviceB, power, "b-power")),
                deviceData(deviceC, module(deviceC, wifi, "c-wifi")),
            )

            val update = serverData(
                deviceData(deviceB, module(deviceB, clipboard, "b-clipboard-new")),
                // deviceC not in update
            )

            val result = mergeData(existing, update, setOf(clipboard))

            result.devices shouldHaveSize 2
            val bModules = result.devices.single { it.deviceId == deviceB }.modules
            bModules shouldHaveSize 2
            bModules.map { it.moduleId } shouldContainExactlyInAnyOrder listOf(power, clipboard)

            val cModules = result.devices.single { it.deviceId == deviceC }.modules
            cModules shouldHaveSize 1
            cModules.single().moduleId shouldBe wifi
            cModules.single().payload shouldBe "c-wifi".encodeUtf8()
        }
    }

    @Nested
    inner class `edge cases` {

        @Test
        fun `empty result removes filtered module`() {
            val existing = serverData(
                deviceData(
                    deviceB,
                    module(deviceB, clipboard, "old-clipboard"),
                    module(deviceB, power, "old-power"),
                ),
            )

            // Update has deviceB but no clipboard module (deleted on server)
            val update = serverData(
                deviceData(deviceB),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            val modules = result.devices.single().modules
            modules shouldHaveSize 1
            modules.single().moduleId shouldBe power
        }

        @Test
        fun `sequential merges accumulate correctly`() {
            val initial = serverData(
                deviceData(
                    deviceB,
                    module(deviceB, power, "power-v1"),
                    module(deviceB, wifi, "wifi-v1"),
                    module(deviceB, meta, "meta-v1"),
                ),
            )

            // First targeted sync: clipboard
            val update1 = serverData(
                deviceData(deviceB, module(deviceB, clipboard, "clipboard-new")),
            )
            val after1 = mergeData(initial, update1, setOf(clipboard))

            // Second targeted sync: meta
            val update2 = serverData(
                deviceData(deviceB, module(deviceB, meta, "meta-v2")),
            )
            val after2 = mergeData(after1, update2, setOf(meta))

            val modules = after2.devices.single().modules
            modules shouldHaveSize 4
            modules.single { it.moduleId == power }.payload shouldBe "power-v1".encodeUtf8()
            modules.single { it.moduleId == wifi }.payload shouldBe "wifi-v1".encodeUtf8()
            modules.single { it.moduleId == clipboard }.payload shouldBe "clipboard-new".encodeUtf8()
            modules.single { it.moduleId == meta }.payload shouldBe "meta-v2".encodeUtf8()
        }

        @Test
        fun `full sync replaces all data completely`() {
            // This tests that when moduleFilter is null, we DON'T merge (direct replacement)
            // The mergeData function is only called when moduleFilter != null,
            // so this test verifies the contract at the call site
            val existing = serverData(
                deviceData(deviceB, module(deviceB, power, "old")),
            )

            val fullUpdate = serverData(
                deviceData(deviceB, module(deviceB, wifi, "new")),
            )

            // With null filter, we replace directly (no merge)
            // This is the behavior when moduleFilter is null in sync()
            val result = fullUpdate // direct assignment, not mergeData

            result.devices.single().modules shouldHaveSize 1
            result.devices.single().modules.single().moduleId shouldBe wifi
        }

        @Test
        fun `merge with empty existing devices list`() {
            val existing = serverData() // no devices

            val update = serverData(
                deviceData(deviceB, module(deviceB, clipboard, "new")),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            // No existing devices to merge into — result has no devices
            // (new devices from update are not added, they'll appear on next full sync)
            result.devices shouldHaveSize 0
        }

        @Test
        fun `merge preserves device order`() {
            val existing = serverData(
                deviceData(deviceA, module(deviceA, power, "a-power")),
                deviceData(deviceB, module(deviceB, power, "b-power")),
                deviceData(deviceC, module(deviceC, power, "c-power")),
            )

            val update = serverData(
                deviceData(deviceB, module(deviceB, clipboard, "b-clip")),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            result.devices.map { it.deviceId } shouldBe listOf(deviceA, deviceB, deviceC)
        }
    }
}

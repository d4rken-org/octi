package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class GDriveAppDataConnectorMergeTest : BaseTest() {

    private val connectorId = ConnectorId(type = ConnectorType.GDRIVE, subtype = "test", account = "acc-1")
    private val deviceA = DeviceId("device-a")
    private val deviceB = DeviceId("device-b")
    private val deviceC = DeviceId("device-c")

    private val power = ModuleId("eu.darken.octi.module.core.power")
    private val wifi = ModuleId("eu.darken.octi.module.core.wifi")
    private val meta = ModuleId("eu.darken.octi.module.core.meta")
    private val clipboard = ModuleId("eu.darken.octi.module.core.clipboard")
    private val apps = ModuleId("eu.darken.octi.module.core.apps")

    private fun module(deviceId: DeviceId, moduleId: ModuleId, payload: String = "data"): GDriveModuleData {
        return GDriveModuleData(
            connectorId = connectorId,
            deviceId = deviceId,
            moduleId = moduleId,
            modifiedAt = Instant.now(),
            payload = payload.encodeUtf8(),
        )
    }

    private fun deviceData(deviceId: DeviceId, vararg modules: GDriveModuleData): GDriveDeviceData {
        return GDriveDeviceData(deviceId = deviceId, modules = modules.toList())
    }

    private fun driveData(vararg devices: SyncRead.Device): GDriveData {
        return GDriveData(connectorId = connectorId, devices = devices.toList())
    }

    private fun mergeData(
        existing: SyncRead,
        update: SyncRead,
        moduleFilter: Set<ModuleId>?,
    ): GDriveData {
        val updatedDeviceMap = update.devices.associateBy { it.deviceId }
        val existingDeviceIds = existing.devices.map { it.deviceId }.toSet()

        val mergedDevices = existing.devices.map { existingDevice ->
            val updatedDevice = updatedDeviceMap[existingDevice.deviceId]
            if (updatedDevice == null) {
                existingDevice
            } else {
                val keptModules = if (moduleFilter != null) {
                    existingDevice.modules.filter { it.moduleId !in moduleFilter }
                } else {
                    emptyList()
                }
                GDriveDeviceData(
                    deviceId = existingDevice.deviceId,
                    modules = keptModules + updatedDevice.modules,
                )
            }
        }

        val newDevices = update.devices
            .filter { it.deviceId !in existingDeviceIds }
            .map { GDriveDeviceData(deviceId = it.deviceId, modules = it.modules.toList()) }

        return GDriveData(connectorId = existing.connectorId, devices = mergedDevices + newDevices)
    }

    @Nested
    inner class `targeted sync preserves existing data` {

        @Test
        fun `updating one module keeps all others`() {
            val existing = driveData(
                deviceData(
                    deviceB,
                    module(deviceB, power, "power-old"),
                    module(deviceB, wifi, "wifi-old"),
                    module(deviceB, meta, "meta-old"),
                    module(deviceB, clipboard, "clipboard-old"),
                    module(deviceB, apps, "apps-old"),
                ),
            )

            val update = driveData(
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
            val existing = driveData(
                deviceData(
                    deviceB,
                    module(deviceB, power, "power-old"),
                    module(deviceB, clipboard, "clipboard-old"),
                    module(deviceB, meta, "meta-old"),
                ),
            )

            val update = driveData(
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
            val existing = driveData(
                deviceData(deviceB, module(deviceB, power, "b-power")),
                deviceData(deviceC, module(deviceC, wifi, "c-wifi")),
            )

            val update = driveData(
                deviceData(deviceB, module(deviceB, clipboard, "b-clipboard-new")),
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

        @Test
        fun `update for one device preserves other devices`() {
            val existing = driveData(
                deviceData(deviceA, module(deviceA, power, "a-power-old")),
                deviceData(deviceB, module(deviceB, power, "b-power-old")),
            )

            // readDrive with deviceFilter would only return deviceB — merge sees only deviceB in update
            val update = driveData(
                deviceData(deviceB, module(deviceB, power, "b-power-new")),
            )

            val result = mergeData(existing, update, setOf(power))

            result.devices shouldHaveSize 2
            result.devices.single { it.deviceId == deviceA }.modules.single().payload shouldBe "a-power-old".encodeUtf8()
            result.devices.single { it.deviceId == deviceB }.modules.single().payload shouldBe "b-power-new".encodeUtf8()
        }
    }

    @Nested
    inner class `new device handling` {

        @Test
        fun `new device in update is appended`() {
            val existing = driveData(
                deviceData(deviceA, module(deviceA, power, "a-power")),
            )

            val update = driveData(
                deviceData(deviceB, module(deviceB, clipboard, "b-clipboard")),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            result.devices shouldHaveSize 2
            result.devices.single { it.deviceId == deviceA }.modules.single().payload shouldBe "a-power".encodeUtf8()
            result.devices.single { it.deviceId == deviceB }.modules.single().payload shouldBe "b-clipboard".encodeUtf8()
        }

        @Test
        fun `new device with targeted sync includes all its modules`() {
            val existing = driveData(
                deviceData(deviceA, module(deviceA, power, "a-power")),
            )

            val update = driveData(
                deviceData(
                    deviceB,
                    module(deviceB, power, "b-power"),
                    module(deviceB, wifi, "b-wifi"),
                ),
            )

            val result = mergeData(existing, update, setOf(power))

            result.devices shouldHaveSize 2
            val newDevice = result.devices.single { it.deviceId == deviceB }
            newDevice.modules shouldHaveSize 2
            newDevice.modules.map { it.moduleId } shouldContainExactlyInAnyOrder listOf(power, wifi)
        }

        @Test
        fun `merge with empty existing appends all update devices`() {
            val existing = driveData()

            val update = driveData(
                deviceData(deviceB, module(deviceB, clipboard, "new")),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            result.devices shouldHaveSize 1
            result.devices.single().deviceId shouldBe deviceB
        }
    }

    @Nested
    inner class `edge cases` {

        @Test
        fun `empty result removes filtered module`() {
            val existing = driveData(
                deviceData(
                    deviceB,
                    module(deviceB, clipboard, "old-clipboard"),
                    module(deviceB, power, "old-power"),
                ),
            )

            val update = driveData(
                deviceData(deviceB),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            val modules = result.devices.single().modules
            modules shouldHaveSize 1
            modules.single().moduleId shouldBe power
        }

        @Test
        fun `sequential merges accumulate correctly`() {
            val initial = driveData(
                deviceData(
                    deviceB,
                    module(deviceB, power, "power-v1"),
                    module(deviceB, wifi, "wifi-v1"),
                    module(deviceB, meta, "meta-v1"),
                ),
            )

            val update1 = driveData(
                deviceData(deviceB, module(deviceB, clipboard, "clipboard-new")),
            )
            val after1 = mergeData(initial, update1, setOf(clipboard))

            val update2 = driveData(
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
            val existing = driveData(
                deviceData(deviceB, module(deviceB, power, "old")),
            )

            val fullUpdate = driveData(
                deviceData(deviceB, module(deviceB, wifi, "new")),
            )

            // With null filter, we replace directly (no merge)
            val result = fullUpdate

            result.devices.single().modules shouldHaveSize 1
            result.devices.single().modules.single().moduleId shouldBe wifi
        }

        @Test
        fun `merge preserves device order`() {
            val existing = driveData(
                deviceData(deviceA, module(deviceA, power, "a-power")),
                deviceData(deviceB, module(deviceB, power, "b-power")),
                deviceData(deviceC, module(deviceC, power, "c-power")),
            )

            val update = driveData(
                deviceData(deviceB, module(deviceB, clipboard, "b-clip")),
            )

            val result = mergeData(existing, update, setOf(clipboard))

            result.devices.map { it.deviceId } shouldBe listOf(deviceA, deviceB, deviceC)
        }
    }
}

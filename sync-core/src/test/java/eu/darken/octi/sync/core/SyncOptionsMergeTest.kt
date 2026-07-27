package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SyncOptionsMergeTest : BaseTest() {

    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")
    private val wifiModuleId = ModuleId("eu.darken.octi.module.core.wifi")

    private fun moduleWrite(moduleId: ModuleId, payload: String) = SyncOptions.ModuleWrite(
        module = mockk<SyncWrite.Device.Module> {
            every { this@mockk.moduleId } returns moduleId
            every { this@mockk.payload } returns payload.encodeUtf8()
        },
        expectedHash = payload,
    )

    @Test
    fun `booleans are ORed, never intersected`() {
        val writeOnly = SyncOptions(stats = false, readData = false, writeData = true)
        val readOnly = SyncOptions(stats = false, readData = true, writeData = false)

        val merged = writeOnly.merge(readOnly)

        merged.readData shouldBe true
        merged.writeData shouldBe true
        merged.stats shouldBe false
    }

    @Test
    fun `a null filter means everything and absorbs a narrower one`() {
        val filtered = SyncOptions(moduleFilter = setOf(powerModuleId), deviceFilter = setOf(DeviceId("a")))
        val unfiltered = SyncOptions()

        filtered.merge(unfiltered).moduleFilter shouldBe null
        filtered.merge(unfiltered).deviceFilter shouldBe null
        unfiltered.merge(filtered).moduleFilter shouldBe null
    }

    @Test
    fun `filters union`() {
        val a = SyncOptions(moduleFilter = setOf(powerModuleId), deviceFilter = setOf(DeviceId("a")))
        val b = SyncOptions(moduleFilter = setOf(wifiModuleId), deviceFilter = setOf(DeviceId("b")))

        val merged = a.merge(b)

        merged.moduleFilter shouldBe setOf(powerModuleId, wifiModuleId)
        merged.deviceFilter shouldBe setOf(DeviceId("a"), DeviceId("b"))
    }

    @Test
    fun `payloads keep one entry per module with the newer one winning`() {
        val a = SyncOptions(writePayload = listOf(moduleWrite(powerModuleId, "v1")))
        val b = SyncOptions(
            writePayload = listOf(moduleWrite(powerModuleId, "v2"), moduleWrite(wifiModuleId, "wifi")),
        )

        val merged = a.merge(b)

        merged.writePayload.size shouldBe 2
        merged.writePayload.single { it.module.moduleId == powerModuleId }.expectedHash shouldBe "v2"
    }
}

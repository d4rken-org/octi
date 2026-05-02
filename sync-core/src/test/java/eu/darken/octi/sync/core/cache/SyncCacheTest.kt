package eu.darken.octi.sync.core.cache

import android.content.Context
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import kotlin.time.Instant

class SyncCacheTest : BaseTest() {

    @TempDir
    lateinit var cacheDir: File

    private val context = mockk<Context>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    private val connectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "prod", account = "acc-123")

    private fun newCache(): SyncCache {
        every { context.cacheDir } returns cacheDir
        return SyncCache(json, TestDispatcherProvider(), context)
    }

    private fun metadataFiles(): List<File> = cacheDir
        .walkTopDown()
        .filter { it.isFile }
        .toList()

    private fun cachedMetadataFile(): File = metadataFiles().single()

    @Test
    fun `device metadata round-trip`() = runTest {
        val cache = newCache()
        val metadata = listOf(
            DeviceMetadata(
                deviceId = DeviceId("device-abc"),
                version = "1.2.3",
                platform = "android",
                label = "Pixel 8",
                lastSeen = Instant.parse("2026-05-02T10:14:00Z"),
                addedAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        )

        cache.saveDeviceMetadata(connectorId, metadata)

        cache.loadDeviceMetadata(connectorId) shouldBe metadata
        metadataFiles().none { it.name.endsWith(".tmp") } shouldBe true
    }

    @Test
    fun `empty device metadata is cached as empty list`() = runTest {
        val cache = newCache()

        cache.saveDeviceMetadata(connectorId, emptyList())

        cache.loadDeviceMetadata(connectorId) shouldBe emptyList()
    }

    @Test
    fun `remove device metadata deletes cached entry`() = runTest {
        val cache = newCache()
        cache.saveDeviceMetadata(connectorId, listOf(DeviceMetadata(deviceId = DeviceId("device-abc"))))

        cache.removeDeviceMetadata(connectorId)

        cache.loadDeviceMetadata(connectorId).shouldBeNull()
    }

    @Test
    fun `corrupt device metadata cache returns null`() = runTest {
        val cache = newCache()
        cache.saveDeviceMetadata(connectorId, listOf(DeviceMetadata(deviceId = DeviceId("device-abc"))))
        cachedMetadataFile().writeText("not-json")

        cache.loadDeviceMetadata(connectorId).shouldBeNull()
        metadataFiles() shouldBe emptyList()
    }

    @Test
    fun `unsupported device metadata schema returns null and deletes cached entry`() = runTest {
        val cache = newCache()
        cache.saveDeviceMetadata(connectorId, listOf(DeviceMetadata(deviceId = DeviceId("device-abc"))))
        cachedMetadataFile().writeText(
            """
                {
                    "schemaVersion": 999,
                    "connectorId": {"type": "kserver", "subtype": "prod", "account": "acc-123"},
                    "cachedAt": "2026-05-02T10:15:30Z",
                    "devices": []
                }
            """.trimIndent()
        )

        cache.loadDeviceMetadata(connectorId).shouldBeNull()
        metadataFiles() shouldBe emptyList()
    }

    @Test
    fun `connector id mismatch returns null and deletes cached entry`() = runTest {
        val cache = newCache()
        cache.saveDeviceMetadata(connectorId, listOf(DeviceMetadata(deviceId = DeviceId("device-abc"))))
        cachedMetadataFile().writeText(
            """
                {
                    "schemaVersion": 1,
                    "connectorId": {"type": "kserver", "subtype": "prod", "account": "other-account"},
                    "cachedAt": "2026-05-02T10:15:30Z",
                    "devices": []
                }
            """.trimIndent()
        )

        cache.loadDeviceMetadata(connectorId).shouldBeNull()
        metadataFiles() shouldBe emptyList()
    }

    @Test
    fun `missing optional device metadata fields deserialize`() = runTest {
        val cache = newCache()
        cache.saveDeviceMetadata(connectorId, emptyList())
        cachedMetadataFile().writeText(
            """
                {
                    "schemaVersion": 1,
                    "connectorId": {"type": "kserver", "subtype": "prod", "account": "acc-123"},
                    "cachedAt": "2026-05-02T10:15:30Z",
                    "devices": [
                        {
                            "deviceId": {"id": "device-minimal"}
                        }
                    ]
                }
            """.trimIndent()
        )

        cache.loadDeviceMetadata(connectorId) shouldBe listOf(DeviceMetadata(deviceId = DeviceId("device-minimal")))
    }
}

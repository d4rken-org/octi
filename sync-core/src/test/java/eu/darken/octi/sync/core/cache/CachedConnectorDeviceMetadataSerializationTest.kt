package eu.darken.octi.sync.core.cache

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import kotlin.time.Instant

class CachedConnectorDeviceMetadataSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testConnectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "prod", account = "acc-123")
    private val testDeviceId = DeviceId(id = "device-abc")

    private val fullCache = CachedConnectorDeviceMetadata(
        connectorId = testConnectorId,
        cachedAt = Instant.parse("2026-05-02T10:15:30Z"),
        devices = listOf(
            DeviceMetadata(
                deviceId = testDeviceId,
                version = "1.2.3",
                platform = "android",
                label = "Pixel 8",
                lastSeen = Instant.parse("2026-05-02T10:14:00Z"),
                addedAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        ),
    )

    @Test
    fun `round-trip serialization`() {
        val encoded = json.encodeToString(fullCache)
        val decoded = json.decodeFromString<CachedConnectorDeviceMetadata>(encoded)
        decoded shouldBe fullCache
    }

    @Test
    fun `wire format stability`() {
        val encoded = json.encodeToString(fullCache)
        encoded.toComparableJson() shouldBe """
            {
                "schemaVersion": 1,
                "connectorId": {"type": "kserver", "subtype": "prod", "account": "acc-123"},
                "cachedAt": "2026-05-02T10:15:30Z",
                "devices": [
                    {
                        "deviceId": {"id": "device-abc"},
                        "version": "1.2.3",
                        "platform": "android",
                        "label": "Pixel 8",
                        "lastSeen": "2026-05-02T10:14:00Z",
                        "addedAt": "2026-04-01T09:00:00Z"
                    }
                ]
            }
        """.toComparableJson()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "schemaVersion": 1,
                "connectorId": {"type": "kserver", "subtype": "prod", "account": "acc-123"},
                "cachedAt": "2026-05-02T10:15:30Z",
                "devices": [],
                "futureField": "ignored"
            }
        """
        val decoded = json.decodeFromString<CachedConnectorDeviceMetadata>(futureJson)
        decoded.devices shouldBe emptyList()
    }

    @Test
    fun `round-trips when capabilities are populated`() {
        val withCaps = fullCache.copy(
            devices = fullCache.devices.map {
                it.copy(capabilities = setOf("encryption:_reported", "encryption:AES256_GCM_SIV"))
            },
        )
        val encoded = json.encodeToString(withCaps)
        val decoded = json.decodeFromString<CachedConnectorDeviceMetadata>(encoded)
        decoded shouldBe withCaps
    }

    @Test
    fun `backward compatibility - legacy cache without capabilities field decodes`() {
        val legacyJson = """
            {
                "schemaVersion": 1,
                "connectorId": {"type": "kserver", "subtype": "prod", "account": "acc-123"},
                "cachedAt": "2026-05-02T10:15:30Z",
                "devices": [
                    {
                        "deviceId": {"id": "device-abc"},
                        "version": "1.2.3",
                        "platform": "android"
                    }
                ]
            }
        """
        val decoded = json.decodeFromString<CachedConnectorDeviceMetadata>(legacyJson)
        decoded.devices.single().capabilities shouldBe null
    }
}

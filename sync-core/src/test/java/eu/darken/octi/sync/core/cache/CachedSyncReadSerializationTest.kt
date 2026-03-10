package eu.darken.octi.sync.core.cache

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class CachedSyncReadSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testConnectorId = ConnectorId(type = "kserver", subtype = "prod", account = "acc-123")
    private val testDeviceId = DeviceId(id = "device-abc")
    private val testModuleId = ModuleId(id = "eu.darken.octi.module.power")

    private val fullRead = CachedSyncRead(
        connectorId = testConnectorId,
        devices = listOf(
            CachedSyncRead.Device(
                deviceId = testDeviceId,
                modules = listOf(
                    CachedSyncRead.Device.Module(
                        connectorId = testConnectorId,
                        deviceId = testDeviceId,
                        moduleId = testModuleId,
                        modifiedAt = Instant.parse("2024-06-15T12:00:00Z"),
                        payload = "test-payload".encodeUtf8(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `round-trip serialization`() {
        val encoded = json.encodeToString(fullRead)
        val decoded = json.decodeFromString<CachedSyncRead>(encoded)
        decoded shouldBe fullRead
    }

    @Test
    fun `wire key accountId maps to connectorId property`() {
        val moshiJson = """
            {
                "accountId": {"type": "gdrive", "subtype": "appdata", "account": "user@gmail.com"},
                "devices": []
            }
        """
        val decoded = json.decodeFromString<CachedSyncRead>(moshiJson)
        decoded.connectorId shouldBe ConnectorId(type = "gdrive", subtype = "appdata", account = "user@gmail.com")
    }

    @Test
    fun `nested Module accountId maps to connectorId property`() {
        val moshiJson = """
            {
                "accountId": {"type": "kserver", "subtype": "prod", "account": "acc-1"},
                "devices": [{
                    "deviceId": {"id": "dev-1"},
                    "modules": [{
                        "accountId": {"type": "kserver", "subtype": "prod", "account": "acc-1"},
                        "deviceId": {"id": "dev-1"},
                        "moduleId": {"id": "eu.darken.octi.module.meta"},
                        "modifiedAt": "2024-01-01T00:00:00Z",
                        "payload": "dGVzdA=="
                    }]
                }]
            }
        """
        val decoded = json.decodeFromString<CachedSyncRead>(moshiJson)
        val module = decoded.devices.first().modules.first()
        module.connectorId shouldBe ConnectorId(type = "kserver", subtype = "prod", account = "acc-1")
    }

    @Test
    fun `wire format uses accountId not connectorId`() {
        val encoded = json.encodeToString(fullRead)
        encoded.contains("\"accountId\"") shouldBe true
        encoded.contains("\"connectorId\"") shouldBe false
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "accountId": {"type": "kserver", "subtype": "prod", "account": "acc-1"},
                "devices": [],
                "syncTimestamp": "2024-06-15T12:00:00Z"
            }
        """
        val decoded = json.decodeFromString<CachedSyncRead>(futureJson)
        decoded.devices shouldBe emptyList()
    }
}

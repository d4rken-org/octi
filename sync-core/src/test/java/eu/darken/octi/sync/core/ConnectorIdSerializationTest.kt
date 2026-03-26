package eu.darken.octi.sync.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class ConnectorIdSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `round-trip serialization`() {
        val connectorId = ConnectorId(type = ConnectorType.GDRIVE, subtype = "appdata", account = "user@example.com")
        val encoded = json.encodeToString(connectorId)
        val decoded = json.decodeFromString<ConnectorId>(encoded)
        decoded shouldBe connectorId
    }

    @Test
    fun `wire format stability`() {
        val connectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "prod", account = "acc-123")
        val encoded = json.encodeToString(connectorId)
        encoded.toComparableJson() shouldBe """
            {
                "type": "kserver",
                "subtype": "prod",
                "account": "acc-123"
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize Moshi-written JSON`() {
        val moshiJson = """{"type":"gdrive","subtype":"appdata","account":"user@gmail.com"}"""
        val decoded = json.decodeFromString<ConnectorId>(moshiJson)
        decoded.type shouldBe ConnectorType.GDRIVE
        decoded.subtype shouldBe "appdata"
        decoded.account shouldBe "user@gmail.com"
    }

    @Test
    fun `backward compatibility - deserialize kserver JSON`() {
        val oldJson = """{"type":"kserver","subtype":"prod","account":"acc-123"}"""
        val decoded = json.decodeFromString<ConnectorId>(oldJson)
        decoded.type shouldBe ConnectorType.OCTISERVER
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """{"type":"gdrive","subtype":"appdata","account":"user@gmail.com","newField":42}"""
        val decoded = json.decodeFromString<ConnectorId>(futureJson)
        decoded.type shouldBe ConnectorType.GDRIVE
    }

    @Test
    fun `idString uses typeId`() {
        val connectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "prod", account = "acc-1")
        connectorId.idString shouldBe "kserver-prod-acc-1"
    }
}

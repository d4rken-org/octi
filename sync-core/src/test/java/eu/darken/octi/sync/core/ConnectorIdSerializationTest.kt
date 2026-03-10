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
        val connectorId = ConnectorId(type = "gdrive", subtype = "appdata", account = "user@example.com")
        val encoded = json.encodeToString(connectorId)
        val decoded = json.decodeFromString<ConnectorId>(encoded)
        decoded shouldBe connectorId
    }

    @Test
    fun `wire format stability`() {
        val connectorId = ConnectorId(type = "kserver", subtype = "prod", account = "acc-123")
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
        decoded.type shouldBe "gdrive"
        decoded.subtype shouldBe "appdata"
        decoded.account shouldBe "user@gmail.com"
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """{"type":"gdrive","subtype":"appdata","account":"user@gmail.com","newField":42}"""
        val decoded = json.decodeFromString<ConnectorId>(futureJson)
        decoded.type shouldBe "gdrive"
    }
}

package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ConnectorTypeSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `GDRIVE serializes to gdrive`() {
        json.encodeToString(ConnectorType.GDRIVE) shouldBe "\"gdrive\""
    }

    @Test
    fun `OCTISERVER serializes to kserver`() {
        json.encodeToString(ConnectorType.OCTISERVER) shouldBe "\"kserver\""
    }

    @Test
    fun `gdrive deserializes to GDRIVE`() {
        json.decodeFromString<ConnectorType>("\"gdrive\"") shouldBe ConnectorType.GDRIVE
    }

    @Test
    fun `kserver deserializes to OCTISERVER`() {
        json.decodeFromString<ConnectorType>("\"kserver\"") shouldBe ConnectorType.OCTISERVER
    }

    @Test
    fun `typeId matches SerialName values`() {
        ConnectorType.GDRIVE.typeId shouldBe "gdrive"
        ConnectorType.OCTISERVER.typeId shouldBe "kserver"
    }
}

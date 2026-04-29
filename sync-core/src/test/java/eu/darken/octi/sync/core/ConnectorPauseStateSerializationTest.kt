package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class ConnectorPauseStateSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val connectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = "prod",
        account = "acc-123",
    )

    @Test
    fun `pause reason wire values are stable`() {
        json.encodeToString(ConnectorPauseReason.Manual) shouldBe "\"manual\""
        json.encodeToString(ConnectorPauseReason.AuthIssue) shouldBe "\"auth_issue\""
    }

    @Test
    fun `round-trip serialization`() {
        val states = setOf(
            ConnectorPauseState(
                connectorId = connectorId,
                reason = ConnectorPauseReason.AuthIssue,
            )
        )

        val encoded = json.encodeToString(states)
        val decoded = json.decodeFromString<Set<ConnectorPauseState>>(encoded)

        decoded shouldBe states
    }

    @Test
    fun `wire format stability`() {
        val encoded = json.encodeToString(
            setOf(
                ConnectorPauseState(
                    connectorId = connectorId,
                    reason = ConnectorPauseReason.AuthIssue,
                )
            )
        )

        encoded.toComparableJson() shouldBe """
            [
                {
                    "connector_id": {
                        "type": "kserver",
                        "subtype": "prod",
                        "account": "acc-123"
                    },
                    "reason": "auth_issue"
                }
            ]
        """.toComparableJson()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            [
                {
                    "connector_id": {
                        "type": "kserver",
                        "subtype": "prod",
                        "account": "acc-123",
                        "new_connector_field": "ignored"
                    },
                    "reason": "manual",
                    "new_pause_field": "ignored"
                }
            ]
        """

        val decoded = json.decodeFromString<Set<ConnectorPauseState>>(futureJson)

        decoded shouldBe setOf(
            ConnectorPauseState(
                connectorId = connectorId,
                reason = ConnectorPauseReason.Manual,
            )
        )
    }
}

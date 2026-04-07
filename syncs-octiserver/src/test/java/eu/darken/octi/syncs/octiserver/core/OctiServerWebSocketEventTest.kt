package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncEvent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Clock
import kotlin.time.Instant

class OctiServerWebSocketEventTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testConnectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test.server", account = "acc-1")

    /**
     * Parses server JSON into client events using the same logic as [OctiServerWebSocket].
     * Extracted here because [OctiServerWebSocket.EventPayload] is private.
     */
    @kotlinx.serialization.Serializable
    private data class EventPayload(
        @kotlinx.serialization.SerialName("events") val events: List<Event>,
    ) {
        @kotlinx.serialization.Serializable
        data class Event(
            @kotlinx.serialization.SerialName("type") val type: String,
            @kotlinx.serialization.SerialName("deviceId") val deviceId: String,
            @kotlinx.serialization.SerialName("moduleId") val moduleId: String,
            @kotlinx.serialization.SerialName("modifiedAt") val modifiedAt: String? = null,
            @kotlinx.serialization.SerialName("action") val action: String = "updated",
        )
    }

    private fun EventPayload.Event.toSyncEvent(): SyncEvent? = when (type) {
        "module_changed" -> SyncEvent.ModuleChanged(
            connectorId = testConnectorId,
            deviceId = DeviceId(deviceId),
            moduleId = ModuleId(moduleId),
            modifiedAt = modifiedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Clock.System.now(),
            action = when (action) {
                "deleted" -> SyncEvent.ModuleChanged.Action.DELETED
                else -> SyncEvent.ModuleChanged.Action.UPDATED
            },
        )
        else -> null
    }

    @Nested
    inner class `wire compatibility with server` {

        @Test
        fun `parse server module_changed notification`() {
            // Exact format produced by BroadcastDebouncer on the server
            val serverJson = """
                {
                    "events": [
                        {
                            "type": "module_changed",
                            "deviceId": "550e8400-e29b-41d4-a716-446655440000",
                            "moduleId": "eu.darken.octi.module.power",
                            "modifiedAt": "2026-03-23T10:15:30Z",
                            "action": "updated"
                        }
                    ]
                }
            """

            val payload = json.decodeFromString<EventPayload>(serverJson)
            payload.events.size shouldBe 1

            val event = payload.events[0].toSyncEvent()
            event.shouldBeInstanceOf<SyncEvent.ModuleChanged>()
            event.deviceId shouldBe DeviceId("550e8400-e29b-41d4-a716-446655440000")
            event.moduleId shouldBe ModuleId("eu.darken.octi.module.power")
            event.action shouldBe SyncEvent.ModuleChanged.Action.UPDATED
            event.modifiedAt shouldBe Instant.parse("2026-03-23T10:15:30Z")
        }

        @Test
        fun `parse server delete notification`() {
            val serverJson = """
                {
                    "events": [
                        {
                            "type": "module_changed",
                            "deviceId": "device-1",
                            "moduleId": "eu.darken.octi.module.clipboard",
                            "modifiedAt": "2026-03-23T10:15:30Z",
                            "action": "deleted"
                        }
                    ]
                }
            """

            val payload = json.decodeFromString<EventPayload>(serverJson)
            val event = payload.events[0].toSyncEvent()
            event.shouldBeInstanceOf<SyncEvent.ModuleChanged>()
            event.action shouldBe SyncEvent.ModuleChanged.Action.DELETED
        }

        @Test
        fun `parse batched server notification with multiple events`() {
            val serverJson = """
                {
                    "events": [
                        {"type": "module_changed", "deviceId": "d1", "moduleId": "eu.darken.octi.module.power", "modifiedAt": "2026-03-23T10:00:00Z", "action": "updated"},
                        {"type": "module_changed", "deviceId": "d1", "moduleId": "eu.darken.octi.module.meta", "modifiedAt": "2026-03-23T10:00:01Z", "action": "updated"},
                        {"type": "module_changed", "deviceId": "d1", "moduleId": "eu.darken.octi.module.wifi", "modifiedAt": "2026-03-23T10:00:02Z", "action": "updated"}
                    ]
                }
            """

            val payload = json.decodeFromString<EventPayload>(serverJson)
            payload.events.size shouldBe 3

            val events = payload.events.mapNotNull { it.toSyncEvent() }
            events.size shouldBe 3
            events.map { (it as SyncEvent.ModuleChanged).moduleId.id } shouldBe listOf(
                "eu.darken.octi.module.power",
                "eu.darken.octi.module.meta",
                "eu.darken.octi.module.wifi",
            )
        }

        @Test
        fun `unknown event type is ignored`() {
            val serverJson = """
                {
                    "events": [
                        {"type": "blob_changed", "deviceId": "d1", "moduleId": "m1", "blobKey": "k1", "action": "added"},
                        {"type": "module_changed", "deviceId": "d1", "moduleId": "m1", "action": "updated"}
                    ]
                }
            """

            val payload = json.decodeFromString<EventPayload>(serverJson)
            payload.events.size shouldBe 2

            val events = payload.events.mapNotNull { it.toSyncEvent() }
            events.size shouldBe 1 // blob_changed is unknown, filtered out
        }

        @Test
        fun `missing modifiedAt defaults to now`() {
            val serverJson = """
                {"events": [{"type": "module_changed", "deviceId": "d1", "moduleId": "m1", "action": "updated"}]}
            """

            val payload = json.decodeFromString<EventPayload>(serverJson)
            val event = payload.events[0].toSyncEvent()
            event.shouldBeInstanceOf<SyncEvent.ModuleChanged>()
            // modifiedAt should be close to now (not crash)
            // Tolerance instead of equality to avoid flakiness across second boundaries
            (kotlin.math.abs(event.modifiedAt.epochSeconds - Clock.System.now().epochSeconds) <= 2L) shouldBe true
        }

        @Test
        fun `missing action defaults to updated`() {
            val serverJson = """
                {"events": [{"type": "module_changed", "deviceId": "d1", "moduleId": "m1"}]}
            """

            val payload = json.decodeFromString<EventPayload>(serverJson)
            val event = payload.events[0].toSyncEvent()
            event.shouldBeInstanceOf<SyncEvent.ModuleChanged>()
            event.action shouldBe SyncEvent.ModuleChanged.Action.UPDATED
        }

        @Test
        fun `forward compatibility - unknown fields in event are ignored`() {
            val serverJson = """
                {
                    "events": [
                        {
                            "type": "module_changed",
                            "deviceId": "d1",
                            "moduleId": "m1",
                            "action": "updated",
                            "futureField": "some-value",
                            "anotherField": 42
                        }
                    ]
                }
            """

            val payload = json.decodeFromString<EventPayload>(serverJson)
            val event = payload.events[0].toSyncEvent()
            event.shouldBeInstanceOf<SyncEvent.ModuleChanged>()
            event.moduleId shouldBe ModuleId("m1")
        }
    }

    @Nested
    inner class `SyncEvent data class` {

        @Test
        fun `ModuleChanged equality`() {
            val event1 = SyncEvent.ModuleChanged(
                connectorId = testConnectorId,
                deviceId = DeviceId("d1"),
                moduleId = ModuleId("m1"),
                modifiedAt = Instant.parse("2026-01-01T00:00:00Z"),
                action = SyncEvent.ModuleChanged.Action.UPDATED,
            )
            val event2 = event1.copy()
            event1 shouldBe event2
        }

        @Test
        fun `BlobChanged equality`() {
            val event = SyncEvent.BlobChanged(
                connectorId = testConnectorId,
                deviceId = DeviceId("d1"),
                moduleId = ModuleId("m1"),
                blobKey = eu.darken.octi.sync.core.BlobKey("key-1"),
                action = SyncEvent.BlobChanged.Action.ADDED,
            )
            event.action shouldBe SyncEvent.BlobChanged.Action.ADDED
            event.blobKey.key shouldBe "key-1"
        }

        @Test
        fun `sealed interface exhaustive when`() {
            val events: List<SyncEvent> = listOf(
                SyncEvent.ModuleChanged(
                    connectorId = testConnectorId,
                    deviceId = DeviceId("d1"),
                    moduleId = ModuleId("m1"),
                    modifiedAt = Clock.System.now(),
                    action = SyncEvent.ModuleChanged.Action.UPDATED,
                ),
                SyncEvent.BlobChanged(
                    connectorId = testConnectorId,
                    deviceId = DeviceId("d1"),
                    moduleId = ModuleId("m1"),
                    blobKey = eu.darken.octi.sync.core.BlobKey("k1"),
                    action = SyncEvent.BlobChanged.Action.DELETED,
                ),
            )

            val types = events.map { event ->
                when (event) {
                    is SyncEvent.ModuleChanged -> "module"
                    is SyncEvent.BlobChanged -> "blob"
                }
            }
            types shouldBe listOf("module", "blob")
        }
    }
}

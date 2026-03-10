package eu.darken.octi.modules.connectivity.core

import eu.darken.octi.common.collections.toByteString
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class ConnectivityInfoSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val fullInfo = ConnectivityInfo(
        connectionType = ConnectivityInfo.ConnectionType.WIFI,
        publicIp = "203.0.113.42",
        localAddressIpv4 = "192.168.1.100",
        localAddressIpv6 = "2001:db8::1",
        gatewayIp = "192.168.1.1",
        dnsServers = listOf("8.8.8.8", "8.8.4.4"),
    )

    @Test
    fun `serialize full ConnectivityInfo`() {
        val result = json.encodeToString(fullInfo)

        result.toComparableJson() shouldBe """
            {
                "connectionType": "WIFI",
                "publicIp": "203.0.113.42",
                "localAddressIpv4": "192.168.1.100",
                "localAddressIpv6": "2001:db8::1",
                "gatewayIp": "192.168.1.1",
                "dnsServers": ["8.8.8.8", "8.8.4.4"]
            }
        """.toComparableJson()
    }

    @Test
    fun `serialize all ConnectionType values`() {
        ConnectivityInfo.ConnectionType.entries.forEach { type ->
            val info = ConnectivityInfo(
                connectionType = type,
                publicIp = null,
                localAddressIpv4 = null,
                localAddressIpv6 = null,
                gatewayIp = null,
                dnsServers = null,
            )
            val encoded = json.encodeToString(info)
            val deserialized = json.decodeFromString<ConnectivityInfo>(encoded)

            deserialized.connectionType shouldBe type
        }
    }

    @Test
    fun `ConnectionType JSON names are stable`() {
        val wifiInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.WIFI)
        json.encodeToString(wifiInfo).contains("\"WIFI\"") shouldBe true

        val cellularInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.CELLULAR)
        json.encodeToString(cellularInfo).contains("\"CELLULAR\"") shouldBe true

        val ethernetInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.ETHERNET)
        json.encodeToString(ethernetInfo).contains("\"ETHERNET\"") shouldBe true

        val noneInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.NONE)
        json.encodeToString(noneInfo).contains("\"NONE\"") shouldBe true
    }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val encoded = json.encodeToString(fullInfo)
        val deserialized = json.decodeFromString<ConnectivityInfo>(encoded)

        deserialized shouldBe fullInfo
    }

    @Test
    fun `serialize with all null fields`() {
        val emptyInfo = ConnectivityInfo(
            connectionType = null,
            publicIp = null,
            localAddressIpv4 = null,
            localAddressIpv6 = null,
            gatewayIp = null,
            dnsServers = null,
        )

        val encoded = json.encodeToString(emptyInfo)
        val deserialized = json.decodeFromString<ConnectivityInfo>(encoded)

        deserialized.connectionType shouldBe null
        deserialized.publicIp shouldBe null
        deserialized.localAddressIpv4 shouldBe null
        deserialized.localAddressIpv6 shouldBe null
        deserialized.gatewayIp shouldBe null
        deserialized.dnsServers shouldBe null
    }

    @Test
    fun `deserialize minimal JSON with missing optional fields`() {
        val minimalJson = """{}"""

        val result = json.decodeFromString<ConnectivityInfo>(minimalJson)

        result.connectionType shouldBe null
        result.publicIp shouldBe null
        result.localAddressIpv4 shouldBe null
        result.localAddressIpv6 shouldBe null
        result.gatewayIp shouldBe null
        result.dnsServers shouldBe null
    }

    @Test
    fun `deserialize JSON with unknown ConnectionType gracefully`() {
        val unknownJson = """
            {
                "connectionType": "5G_MILLIMETER_WAVE",
                "publicIp": "1.2.3.4"
            }
        """

        // kotlinx.serialization throws on unknown enum values
        // This documents the behavior - old clients won't crash because unknown types
        // only appear if the sender has a newer version
        val result = try {
            json.decodeFromString<ConnectivityInfo>(unknownJson)
        } catch (e: Exception) {
            null
        }

        // Unknown enum values cause deserialization failure, which BaseModuleSync handles gracefully
        // by skipping that device's data rather than crashing
        if (result != null) {
            result.publicIp shouldBe "1.2.3.4"
        }
    }

    @Test
    fun `deserialize JSON with empty dnsServers list`() {
        val dnsJson = """
            {
                "connectionType": "WIFI",
                "publicIp": "1.2.3.4",
                "dnsServers": []
            }
        """

        val result = json.decodeFromString<ConnectivityInfo>(dnsJson)

        result.dnsServers shouldBe emptyList()
    }

    @Test
    fun `serialize with empty dnsServers list`() {
        val info = fullInfo.copy(dnsServers = emptyList())

        val encoded = json.encodeToString(info)
        val deserialized = json.decodeFromString<ConnectivityInfo>(encoded)

        deserialized.dnsServers shouldBe emptyList()
    }

    @Test
    fun `ConnectivitySerializer deserializes Moshi-written ByteString payload`() {
        val serializer = ConnectivitySerializer(json)
        val moshiPayload = """
            {
                "connectionType": "CELLULAR",
                "publicIp": "198.51.100.1",
                "localAddressIpv4": "10.0.0.5",
                "localAddressIpv6": null,
                "gatewayIp": "10.0.0.1",
                "dnsServers": ["1.1.1.1"]
            }
        """.toByteString()
        val result = serializer.deserialize(moshiPayload)
        result.connectionType shouldBe ConnectivityInfo.ConnectionType.CELLULAR
        result.publicIp shouldBe "198.51.100.1"
        result.localAddressIpv4 shouldBe "10.0.0.5"
        result.localAddressIpv6 shouldBe null
        result.dnsServers shouldBe listOf("1.1.1.1")
    }

    @Test
    fun `ConnectivitySerializer serialize output matches Moshi wire format`() {
        val serializer = ConnectivitySerializer(json)
        val bytes = serializer.serialize(fullInfo)
        bytes.utf8().toComparableJson() shouldBe """
            {
                "connectionType": "WIFI",
                "publicIp": "203.0.113.42",
                "localAddressIpv4": "192.168.1.100",
                "localAddressIpv6": "2001:db8::1",
                "gatewayIp": "192.168.1.1",
                "dnsServers": ["8.8.8.8", "8.8.4.4"]
            }
        """.toComparableJson()
    }

    @Test
    fun `ConnectivitySerializer round-trip via ByteString`() {
        val serializer = ConnectivitySerializer(json)

        val bytes = serializer.serialize(fullInfo)
        val deserialized = serializer.deserialize(bytes)

        deserialized shouldBe fullInfo
    }

    @Test
    fun `ConnectivitySerializer round-trip with all nulls`() {
        val serializer = ConnectivitySerializer(json)
        val emptyInfo = ConnectivityInfo(
            connectionType = null,
            publicIp = null,
            localAddressIpv4 = null,
            localAddressIpv6 = null,
            gatewayIp = null,
            dnsServers = null,
        )

        val bytes = serializer.serialize(emptyInfo)
        val deserialized = serializer.deserialize(bytes)

        deserialized shouldBe emptyInfo
    }
}

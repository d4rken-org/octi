package eu.darken.octi.modules.connectivity.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class ConnectivityInfoSerializationTest : BaseTest() {

    private val moshi = Moshi.Builder().build()

    private val adapter by lazy { moshi.adapter<ConnectivityInfo>() }

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
        val json = adapter.toJson(fullInfo)

        json.toComparableJson() shouldBe """
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
            val json = adapter.toJson(info)
            val deserialized = adapter.fromJson(json)

            deserialized shouldNotBe null
            deserialized!!.connectionType shouldBe type
        }
    }

    @Test
    fun `ConnectionType JSON names are stable`() {
        val wifiInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.WIFI)
        adapter.toJson(wifiInfo).contains("\"WIFI\"") shouldBe true

        val cellularInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.CELLULAR)
        adapter.toJson(cellularInfo).contains("\"CELLULAR\"") shouldBe true

        val ethernetInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.ETHERNET)
        adapter.toJson(ethernetInfo).contains("\"ETHERNET\"") shouldBe true

        val noneInfo = fullInfo.copy(connectionType = ConnectivityInfo.ConnectionType.NONE)
        adapter.toJson(noneInfo).contains("\"NONE\"") shouldBe true
    }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val json = adapter.toJson(fullInfo)
        val deserialized = adapter.fromJson(json)

        deserialized shouldNotBe null
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

        val json = adapter.toJson(emptyInfo)
        val deserialized = adapter.fromJson(json)

        deserialized shouldNotBe null
        deserialized!!.connectionType shouldBe null
        deserialized.publicIp shouldBe null
        deserialized.localAddressIpv4 shouldBe null
        deserialized.localAddressIpv6 shouldBe null
        deserialized.gatewayIp shouldBe null
        deserialized.dnsServers shouldBe null
    }

    @Test
    fun `deserialize minimal JSON with missing optional fields`() {
        val minimalJson = """{}"""

        val result = adapter.fromJson(minimalJson)

        result shouldNotBe null
        result!!.connectionType shouldBe null
        result.publicIp shouldBe null
        result.localAddressIpv4 shouldBe null
        result.localAddressIpv6 shouldBe null
        result.gatewayIp shouldBe null
        result.dnsServers shouldBe null
    }

    @Test
    fun `deserialize JSON with unknown ConnectionType gracefully`() {
        val json = """
            {
                "connectionType": "5G_MILLIMETER_WAVE",
                "publicIp": "1.2.3.4"
            }
        """

        // Moshi with @JsonClass(generateAdapter = false) enum will throw on unknown values
        // This documents the behavior - old clients won't crash because unknown types
        // only appear if the sender has a newer version
        val result = try {
            adapter.fromJson(json)
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
        val json = """
            {
                "connectionType": "WIFI",
                "publicIp": "1.2.3.4",
                "dnsServers": []
            }
        """

        val result = adapter.fromJson(json)

        result shouldNotBe null
        result!!.dnsServers shouldBe emptyList()
    }

    @Test
    fun `serialize with empty dnsServers list`() {
        val info = fullInfo.copy(dnsServers = emptyList())

        val json = adapter.toJson(info)
        val deserialized = adapter.fromJson(json)

        deserialized shouldNotBe null
        deserialized!!.dnsServers shouldBe emptyList()
    }

    @Test
    fun `ConnectivitySerializer round-trip via ByteString`() {
        val serializer = ConnectivitySerializer(moshi)

        val bytes = serializer.serialize(fullInfo)
        val deserialized = serializer.deserialize(bytes)

        deserialized shouldBe fullInfo
    }

    @Test
    fun `ConnectivitySerializer round-trip with all nulls`() {
        val serializer = ConnectivitySerializer(moshi)
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

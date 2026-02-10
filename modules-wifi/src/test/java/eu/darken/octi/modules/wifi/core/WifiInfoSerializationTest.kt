package eu.darken.octi.modules.wifi.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import eu.darken.octi.common.collections.toByteString
import testhelpers.json.toComparableJson

class WifiInfoSerializationTest : BaseTest() {

    private val moshi = Moshi.Builder().build()

    private val adapter by lazy { moshi.adapter<WifiInfo>() }

    private val fullInfo = WifiInfo(
        currentWifi = WifiInfo.Wifi(
            ssid = "\"MyNetwork\"",
            reception = 0.75f,
            freqType = WifiInfo.Wifi.Type.FIVE_GHZ,
        ),
    )

    @Test
    fun `round-trip serialization preserves all fields`() {
        val json = adapter.toJson(fullInfo)
        val deserialized = adapter.fromJson(json)

        deserialized shouldBe fullInfo
    }

    @Test
    fun `serialize full WifiInfo`() {
        val json = adapter.toJson(fullInfo)

        json.toComparableJson() shouldBe """
            {
                "currentWifi": {
                    "ssid": "\"MyNetwork\"",
                    "reception": 0.75,
                    "freqType": "5GHZ"
                }
            }
        """.toComparableJson()
    }

    @Test
    fun `current format does not contain address fields`() {
        val json = adapter.toJson(fullInfo)

        json shouldNotContain "addressIpv4"
        json shouldNotContain "addressIpv6"
    }

    @Test
    fun `deserialize old format with addressIpv4 and addressIpv6 fields`() {
        val oldJson = """
            {
                "currentWifi": {
                    "ssid": "\"HomeWifi\"",
                    "reception": 0.85,
                    "freqType": "2.4GHZ",
                    "addressIpv4": "192.168.1.100",
                    "addressIpv6": "fe80::1"
                }
            }
        """

        val result = adapter.fromJson(oldJson)

        result shouldNotBe null
        result!!.currentWifi shouldNotBe null
        result.currentWifi!!.ssid shouldBe "\"HomeWifi\""
        result.currentWifi!!.reception shouldBe 0.85f
        result.currentWifi!!.freqType shouldBe WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ
    }

    @Test
    fun `deserialize old format with only addressIpv4`() {
        val oldJson = """
            {
                "currentWifi": {
                    "ssid": "\"Office\"",
                    "reception": 0.5,
                    "freqType": "5GHZ",
                    "addressIpv4": "10.0.0.42"
                }
            }
        """

        val result = adapter.fromJson(oldJson)

        result shouldNotBe null
        result!!.currentWifi shouldNotBe null
        result.currentWifi!!.ssid shouldBe "\"Office\""
        result.currentWifi!!.reception shouldBe 0.5f
        result.currentWifi!!.freqType shouldBe WifiInfo.Wifi.Type.FIVE_GHZ
    }

    @Test
    fun `deserialize old format with only addressIpv6`() {
        val oldJson = """
            {
                "currentWifi": {
                    "ssid": null,
                    "reception": null,
                    "freqType": null,
                    "addressIpv6": "2001:db8::abcd"
                }
            }
        """

        val result = adapter.fromJson(oldJson)

        result shouldNotBe null
        result!!.currentWifi shouldNotBe null
        result.currentWifi!!.ssid shouldBe null
        result.currentWifi!!.reception shouldBe null
        result.currentWifi!!.freqType shouldBe null
    }

    @Test
    fun `deserialize old format with null address fields`() {
        val oldJson = """
            {
                "currentWifi": {
                    "ssid": "\"Guest\"",
                    "reception": 0.3,
                    "freqType": "UNKNOWN",
                    "addressIpv4": null,
                    "addressIpv6": null
                }
            }
        """

        val result = adapter.fromJson(oldJson)

        result shouldNotBe null
        result!!.currentWifi shouldNotBe null
        result.currentWifi!!.ssid shouldBe "\"Guest\""
        result.currentWifi!!.reception shouldBe 0.3f
        result.currentWifi!!.freqType shouldBe WifiInfo.Wifi.Type.UNKNOWN
    }

    @Test
    fun `deserialize old format full payload via WifiSerializer`() {
        val serializer = WifiSerializer(moshi)

        // Simulate what an older app version would have serialized
        val oldJson = """
            {
                "currentWifi": {
                    "ssid": "\"MyNet\"",
                    "reception": 0.9,
                    "freqType": "5GHZ",
                    "addressIpv4": "192.168.0.50",
                    "addressIpv6": "fe80::abc:def"
                }
            }
        """

        val bytes = oldJson.toByteString()
        val result = serializer.deserialize(bytes)

        result shouldNotBe null
        result.currentWifi shouldNotBe null
        result.currentWifi!!.ssid shouldBe "\"MyNet\""
        result.currentWifi!!.reception shouldBe 0.9f
        result.currentWifi!!.freqType shouldBe WifiInfo.Wifi.Type.FIVE_GHZ
    }

    @Test
    fun `deserialize with null currentWifi`() {
        val json = """{"currentWifi": null}"""

        val result = adapter.fromJson(json)

        result shouldNotBe null
        result!!.currentWifi shouldBe null
    }

    @Test
    fun `deserialize empty JSON`() {
        val json = """{}"""

        val result = adapter.fromJson(json)

        result shouldNotBe null
        result!!.currentWifi shouldBe null
    }
}

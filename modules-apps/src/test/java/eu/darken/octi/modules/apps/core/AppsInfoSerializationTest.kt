package eu.darken.octi.modules.apps.core

import eu.darken.octi.common.collections.toByteString
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class AppsInfoSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testPkg = AppsInfo.Pkg(
        packageName = "com.example.app",
        label = "Example App",
        versionCode = 42L,
        versionName = "1.2.3",
        installedAt = Instant.parse("2024-01-15T10:30:00Z"),
        installerPkg = "com.android.vending",
        updatedAt = Instant.parse("2024-06-20T14:00:00Z"),
    )

    @Test
    fun `serialize AppsInfo with updatedAt`() {
        val appsInfo = AppsInfo(installedPackages = listOf(testPkg))

        val encoded = json.encodeToString(appsInfo)

        encoded.toComparableJson() shouldBe """
            {
                "installedPackages": [
                    {
                        "packageName": "com.example.app",
                        "label": "Example App",
                        "versionCode": 42,
                        "versionName": "1.2.3",
                        "installedAt": "2024-01-15T10:30:00Z",
                        "installerPkg": "com.android.vending",
                        "updatedAt": "2024-06-20T14:00:00Z"
                    }
                ]
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize old JSON without updatedAt field`() {
        val oldJson = """
            {
                "installedPackages": [
                    {
                        "packageName": "com.example.app",
                        "label": "Example App",
                        "versionCode": 42,
                        "versionName": "1.2.3",
                        "installedAt": "2024-01-15T10:30:00Z",
                        "installerPkg": "com.android.vending"
                    }
                ]
            }
        """

        val result = json.decodeFromString<AppsInfo>(oldJson)

        result.installedPackages.size shouldBe 1
        val pkg = result.installedPackages.first()
        pkg.packageName shouldBe "com.example.app"
        pkg.label shouldBe "Example App"
        pkg.versionCode shouldBe 42L
        pkg.versionName shouldBe "1.2.3"
        pkg.installedAt shouldBe Instant.parse("2024-01-15T10:30:00Z")
        pkg.installerPkg shouldBe "com.android.vending"
        pkg.updatedAt shouldBe null
    }

    @Test
    fun `deserialize JSON with updatedAt field`() {
        val jsonStr = """
            {
                "installedPackages": [
                    {
                        "packageName": "com.example.app",
                        "label": "Example App",
                        "versionCode": 42,
                        "versionName": "1.2.3",
                        "installedAt": "2024-01-15T10:30:00Z",
                        "installerPkg": "com.android.vending",
                        "updatedAt": "2024-06-20T14:00:00Z"
                    }
                ]
            }
        """

        val result = json.decodeFromString<AppsInfo>(jsonStr)

        val pkg = result.installedPackages.first()
        pkg.updatedAt shouldBe Instant.parse("2024-06-20T14:00:00Z")
    }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val original = AppsInfo(installedPackages = listOf(testPkg))

        val encoded = json.encodeToString(original)
        val deserialized = json.decodeFromString<AppsInfo>(encoded)

        deserialized.installedPackages.size shouldBe 1
        val pkg = deserialized.installedPackages.first()
        pkg shouldBe testPkg
    }

    @Test
    fun `AppsSerializer round-trip via ByteString`() {
        val serializer = AppsSerializer(json)
        val original = AppsInfo(installedPackages = listOf(testPkg))
        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)
        deserialized.installedPackages.first() shouldBe testPkg
    }

    @Test
    fun `AppsSerializer deserializes Moshi-written ByteString payload`() {
        val moshiPayload = """
            {
                "installedPackages": [
                    {
                        "packageName": "com.test.old",
                        "label": "Old App",
                        "versionCode": 10,
                        "versionName": "0.1.0",
                        "installedAt": "2023-06-01T09:00:00Z",
                        "installerPkg": null
                    }
                ]
            }
        """.toByteString()
        val serializer = AppsSerializer(json)
        val result = serializer.deserialize(moshiPayload)
        result.installedPackages.size shouldBe 1
        val pkg = result.installedPackages.first()
        pkg.packageName shouldBe "com.test.old"
        pkg.label shouldBe "Old App"
        pkg.versionCode shouldBe 10L
        pkg.installedAt shouldBe Instant.parse("2023-06-01T09:00:00Z")
        pkg.installerPkg shouldBe null
        pkg.updatedAt shouldBe null
    }

    @Test
    fun `AppsSerializer serialize output matches Moshi wire format`() {
        val serializer = AppsSerializer(json)
        val original = AppsInfo(installedPackages = listOf(testPkg))
        val bytes = serializer.serialize(original)
        bytes.utf8().toComparableJson() shouldBe """
            {
                "installedPackages": [
                    {
                        "packageName": "com.example.app",
                        "label": "Example App",
                        "versionCode": 42,
                        "versionName": "1.2.3",
                        "installedAt": "2024-01-15T10:30:00Z",
                        "installerPkg": "com.android.vending",
                        "updatedAt": "2024-06-20T14:00:00Z"
                    }
                ]
            }
        """.toComparableJson()
    }

    @Test
    fun `updatedAt null is omitted from JSON`() {
        val pkgWithoutUpdate = testPkg.copy(updatedAt = null)
        val appsInfo = AppsInfo(installedPackages = listOf(pkgWithoutUpdate))

        val encoded = json.encodeToString(appsInfo)

        encoded.contains("updatedAt") shouldBe false
    }
}

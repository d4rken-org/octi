package eu.darken.octi.modules.apps.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.octi.common.serialization.adapter.InstantAdapter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class AppsInfoSerializationTest : BaseTest() {

    private val moshi = Moshi.Builder()
        .add(InstantAdapter())
        .build()

    private val adapter by lazy { moshi.adapter<AppsInfo>() }

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

        val json = adapter.toJson(appsInfo)

        json.toComparableJson() shouldBe """
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

        val result = adapter.fromJson(oldJson)

        result shouldNotBe null
        result!!.installedPackages.size shouldBe 1
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
        val json = """
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

        val result = adapter.fromJson(json)

        result shouldNotBe null
        val pkg = result!!.installedPackages.first()
        pkg.updatedAt shouldBe Instant.parse("2024-06-20T14:00:00Z")
    }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val original = AppsInfo(installedPackages = listOf(testPkg))

        val json = adapter.toJson(original)
        val deserialized = adapter.fromJson(json)

        deserialized shouldNotBe null
        deserialized!!.installedPackages.size shouldBe 1
        val pkg = deserialized.installedPackages.first()
        pkg shouldBe testPkg
    }

    @Test
    fun `updatedAt null is omitted from JSON`() {
        val pkgWithoutUpdate = testPkg.copy(updatedAt = null)
        val appsInfo = AppsInfo(installedPackages = listOf(pkgWithoutUpdate))

        val json = adapter.toJson(appsInfo)

        json.contains("updatedAt") shouldBe false
    }
}

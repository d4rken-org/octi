package eu.darken.octi.sync.ui.devices

import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncDevicesSortMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SyncDevicesSortingTest : BaseTest() {

    private val baseInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun item(
        id: String,
        label: String = id,
        addedAt: Instant? = null,
        lastSeen: Instant? = null,
        metaVersion: String? = null,
        serverVersion: String? = null,
    ) = SyncDevicesVM.DeviceItem(
        deviceId = DeviceId(id),
        metaInfo = metaVersion?.let {
            MetaInfo(
                deviceLabel = label,
                deviceId = DeviceId(id),
                octiVersionName = it,
                octiGitSha = "sha",
                deviceManufacturer = "vendor",
                deviceName = label,
                deviceType = MetaInfo.DeviceType.PHONE,
                deviceBootedAt = baseInstant,
                androidVersionName = "14",
                androidApiLevel = 34,
                androidSecurityPatch = null,
            )
        },
        lastSeen = lastSeen,
        error = null,
        serverVersion = serverVersion,
        serverAddedAt = addedAt,
        serverPlatform = "android",
        displayLabel = label,
    )

    @Nested
    inner class `DATE_ADDED` {
        @Test
        fun `descending with nulls last`() {
            val older = item(id = "a", label = "Alpha", addedAt = baseInstant)
            val newer = item(id = "b", label = "Bravo", addedAt = baseInstant.plus(1.days))
            val unknown = item(id = "c", label = "Charlie", addedAt = null)

            val sorted = listOf(older, unknown, newer).sortedWith(comparatorFor(SyncDevicesSortMode.DATE_ADDED))

            sorted.map { it.deviceId.id } shouldBe listOf("b", "a", "c")
        }

        @Test
        fun `nulls fall back to alphabetical tie-break`() {
            val charlie = item(id = "c", label = "Charlie", addedAt = null)
            val alpha = item(id = "a", label = "Alpha", addedAt = null)
            val bravo = item(id = "b", label = "Bravo", addedAt = null)

            val sorted = listOf(charlie, bravo, alpha).sortedWith(comparatorFor(SyncDevicesSortMode.DATE_ADDED))

            sorted.map { it.deviceId.id } shouldBe listOf("a", "b", "c")
        }
    }

    @Nested
    inner class `LAST_SEEN` {
        @Test
        fun `descending with nulls last`() {
            val older = item(id = "a", lastSeen = baseInstant)
            val newer = item(id = "b", lastSeen = baseInstant.plus(1.hours))
            val unknown = item(id = "c", lastSeen = null)

            val sorted = listOf(older, unknown, newer).sortedWith(comparatorFor(SyncDevicesSortMode.LAST_SEEN))

            sorted.map { it.deviceId.id } shouldBe listOf("b", "a", "c")
        }
    }

    @Nested
    inner class `NAME` {
        @Test
        fun `case insensitive ascending then deviceId tie-break`() {
            val a1 = item(id = "z", label = "alpha")
            val a2 = item(id = "a", label = "ALPHA")
            val b = item(id = "m", label = "Bravo")

            val sorted = listOf(b, a1, a2).sortedWith(comparatorFor(SyncDevicesSortMode.NAME))

            // "ALPHA" / "alpha" tie under case-insensitive — deviceId tie-break: "a" < "z"
            sorted.map { it.deviceId.id } shouldBe listOf("a", "z", "m")
        }
    }

    @Nested
    inner class `reversed` {
        @Test
        fun `DATE_ADDED reversed flips primary key but keeps nulls last`() {
            val older = item(id = "a", label = "Alpha", addedAt = baseInstant)
            val newer = item(id = "b", label = "Bravo", addedAt = baseInstant.plus(1.days))
            val unknown = item(id = "c", label = "Charlie", addedAt = null)

            val sorted = listOf(older, unknown, newer).sortedWith(
                comparatorFor(SyncDevicesSortMode.DATE_ADDED, reversed = true)
            )

            sorted.map { it.deviceId.id } shouldBe listOf("a", "b", "c")
        }

        @Test
        fun `NAME reversed sorts Z to A`() {
            val a = item(id = "a", label = "Alpha")
            val b = item(id = "b", label = "Bravo")
            val c = item(id = "c", label = "Charlie")

            val sorted = listOf(a, b, c).sortedWith(comparatorFor(SyncDevicesSortMode.NAME, reversed = true))

            sorted.map { it.deviceId.id } shouldBe listOf("c", "b", "a")
        }

        @Test
        fun `APP_VERSION reversed puts older versions first`() {
            val older = item(id = "old", metaVersion = "0.9.0")
            val newer = item(id = "new", metaVersion = "1.0.0")
            val unknown = item(id = "miss", metaVersion = null)

            val sorted = listOf(newer, older, unknown).sortedWith(
                comparatorFor(SyncDevicesSortMode.APP_VERSION, reversed = true)
            )

            sorted.map { it.deviceId.id } shouldBe listOf("old", "new", "miss")
        }
    }

    @Nested
    inner class `APP_VERSION` {
        @Test
        fun `0_10_0 sorts above 0_9_0 (semver beats lexical)`() {
            val older = item(id = "a", metaVersion = "0.9.0")
            val newer = item(id = "b", metaVersion = "0.10.0")

            val sorted = listOf(older, newer).sortedWith(comparatorFor(SyncDevicesSortMode.APP_VERSION))

            sorted.map { it.deviceId.id } shouldBe listOf("b", "a")
        }

        @Test
        fun `stable release sorts above prereleases`() {
            val rc = item(id = "rc", metaVersion = "1.0.0-rc0")
            val beta = item(id = "beta", metaVersion = "1.0.0-beta1")
            val stable = item(id = "stable", metaVersion = "1.0.0")

            val sorted = listOf(beta, rc, stable).sortedWith(comparatorFor(SyncDevicesSortMode.APP_VERSION))

            sorted.map { it.deviceId.id } shouldBe listOf("stable", "rc", "beta")
        }

        @Test
        fun `unparseable and null versions sort to bottom`() {
            val good = item(id = "good", label = "Good", metaVersion = "1.0.0")
            val unparseable = item(id = "broken", label = "Broken", metaVersion = "not-a-version")
            val nullVer = item(id = "missing", label = "Missing", metaVersion = null)

            val sorted = listOf(unparseable, nullVer, good).sortedWith(comparatorFor(SyncDevicesSortMode.APP_VERSION))

            sorted.first().deviceId.id shouldBe "good"
            sorted.drop(1).map { it.deviceId.id }.toSet() shouldBe setOf("broken", "missing")
        }

        @Test
        fun `falls back to serverVersion when metaInfo is null`() {
            val withMeta = item(id = "meta", metaVersion = "1.0.0", serverVersion = "1.0.0")
            val serverOnly = item(id = "server", metaVersion = null, serverVersion = "2.0.0")

            val sorted = listOf(withMeta, serverOnly).sortedWith(comparatorFor(SyncDevicesSortMode.APP_VERSION))

            sorted.map { it.deviceId.id } shouldBe listOf("server", "meta")
        }
    }
}

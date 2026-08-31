package eu.darken.octi.modules.connectivity.ui.widget

import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Robolectric because the builder formats `lastSeen` via `DateUtils.getRelativeTimeSpanString`,
 * which is unavailable in the plain unit-test android.jar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NetworkWidgetContentTest {

    private val selfDeviceId = DeviceId(id = "self-device")
    private val peerDeviceId = DeviceId(id = "peer-device")
    private val connectivityModuleId = ModuleId("eu.darken.octi.module.core.connectivity")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private val now = Clock.System.now()

    private fun connectivityInfo() = ConnectivityInfo(
        connectionType = ConnectivityInfo.ConnectionType.WIFI,
        publicIp = "203.0.113.5",
        localAddressIpv4 = "192.168.1.10",
        localAddressIpv6 = null,
        gatewayIp = null,
        dnsServers = null,
    )

    private fun connectivityModuleData(deviceId: DeviceId, modifiedAt: Instant): ModuleData<ConnectivityInfo> =
        ModuleData(
            modifiedAt = modifiedAt,
            deviceId = deviceId,
            moduleId = connectivityModuleId,
            data = connectivityInfo(),
        )

    private fun metaModuleData(deviceId: DeviceId, label: String, modifiedAt: Instant): ModuleData<MetaInfo> =
        ModuleData(
            modifiedAt = modifiedAt,
            deviceId = deviceId,
            moduleId = metaModuleId,
            data = MetaInfo(
                deviceLabel = label,
                deviceId = deviceId,
                octiVersionName = "test",
                octiGitSha = "test",
                deviceManufacturer = "test",
                deviceName = label,
                deviceType = MetaInfo.DeviceType.PHONE,
                deviceBootedAt = now,
                androidVersionName = "test",
                androidApiLevel = 34,
                androidSecurityPatch = null,
            ),
        )

    private fun tilesFor(
        selfMetaAt: Instant = now,
        selfConnectivityAt: Instant = now,
        peerMetaAt: Instant? = null,
        peerConnectivityAt: Instant? = null,
    ): List<NetworkDeviceTile> {
        val metaState = BaseModuleRepo.State(
            moduleId = metaModuleId,
            self = metaModuleData(selfDeviceId, "MyPhone", selfMetaAt),
            isOthersInitialized = true,
            others = peerMetaAt?.let { listOf(metaModuleData(peerDeviceId, "Peer", it)) } ?: emptyList(),
        )
        val connectivityState = BaseModuleRepo.State(
            moduleId = connectivityModuleId,
            self = connectivityModuleData(selfDeviceId, selfConnectivityAt),
            isOthersInitialized = true,
            others = peerConnectivityAt?.let { listOf(connectivityModuleData(peerDeviceId, it)) } ?: emptyList(),
        )
        return buildDeviceTiles(metaState, connectivityState, null)
    }

    private fun List<NetworkDeviceTile>.tile(deviceId: DeviceId) = single { it.deviceId == deviceId.id }

    @Test
    fun `a fresh peer is not stale`() {
        val tiles = tilesFor(peerMetaAt = now, peerConnectivityAt = now)
        tiles.tile(peerDeviceId).isStale shouldBe false
    }

    @Test
    fun `an eight day old peer is stale`() {
        val old = now - 8.days
        val tiles = tilesFor(peerMetaAt = old, peerConnectivityAt = old)
        tiles.tile(peerDeviceId).isStale shouldBe true
    }

    @Test
    fun `self is never stale even with an old timestamp`() {
        val old = now - 30.days
        val tiles = tilesFor(selfMetaAt = old, selfConnectivityAt = old)
        tiles.tile(selfDeviceId).isStale shouldBe false
    }

    @Test
    fun `fresh connectivity data keeps a peer with old meta data current`() {
        val tiles = tilesFor(peerMetaAt = now - 8.days, peerConnectivityAt = now)
        tiles.tile(peerDeviceId).isStale shouldBe false
    }
}

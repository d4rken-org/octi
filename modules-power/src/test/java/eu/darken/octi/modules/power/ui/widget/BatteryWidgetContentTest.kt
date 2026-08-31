package eu.darken.octi.modules.power.ui.widget

import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.core.PowerInfo
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
class BatteryWidgetContentTest {

    private val selfDeviceId = DeviceId(id = "self-device")
    private val peerDeviceId = DeviceId(id = "peer-device")
    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private val now = Clock.System.now()

    private fun powerInfo() = PowerInfo(
        status = PowerInfo.Status.DISCHARGING,
        battery = PowerInfo.Battery(level = 80, scale = 100, health = 1, temp = 25f),
        chargeIO = PowerInfo.ChargeIO(
            currentNow = null,
            currenAvg = null,
            fullSince = null,
            fullAt = null,
            emptyAt = null,
        ),
    )

    private fun powerModuleData(deviceId: DeviceId, modifiedAt: Instant): ModuleData<PowerInfo> = ModuleData(
        modifiedAt = modifiedAt,
        deviceId = deviceId,
        moduleId = powerModuleId,
        data = powerInfo(),
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

    private fun rowsFor(
        selfMetaAt: Instant = now,
        selfPowerAt: Instant = now,
        peerMetaAt: Instant? = null,
        peerPowerAt: Instant? = null,
    ): List<BatteryDeviceRow> {
        val metaState = BaseModuleRepo.State(
            moduleId = metaModuleId,
            self = metaModuleData(selfDeviceId, "MyPhone", selfMetaAt),
            isOthersInitialized = true,
            others = peerMetaAt?.let { listOf(metaModuleData(peerDeviceId, "Peer", it)) } ?: emptyList(),
        )
        val powerState = BaseModuleRepo.State(
            moduleId = powerModuleId,
            self = powerModuleData(selfDeviceId, selfPowerAt),
            isOthersInitialized = true,
            others = peerPowerAt?.let { listOf(powerModuleData(peerDeviceId, it)) } ?: emptyList(),
        )
        return buildDeviceRows(metaState, powerState, null)
    }

    private fun List<BatteryDeviceRow>.row(deviceId: DeviceId) = single { it.deviceId == deviceId.id }

    @Test
    fun `a fresh peer is not stale`() {
        val rows = rowsFor(peerMetaAt = now, peerPowerAt = now)
        rows.row(peerDeviceId).isStale shouldBe false
    }

    @Test
    fun `an eight day old peer is stale`() {
        val old = now - 8.days
        val rows = rowsFor(peerMetaAt = old, peerPowerAt = old)
        rows.row(peerDeviceId).isStale shouldBe true
    }

    @Test
    fun `self is never stale even with an old timestamp`() {
        val old = now - 30.days
        val rows = rowsFor(selfMetaAt = old, selfPowerAt = old)
        rows.row(selfDeviceId).isStale shouldBe false
    }

    @Test
    fun `fresh power data keeps a peer with old meta data current`() {
        val rows = rowsFor(peerMetaAt = now - 8.days, peerPowerAt = now)
        rows.row(peerDeviceId).isStale shouldBe false
    }
}

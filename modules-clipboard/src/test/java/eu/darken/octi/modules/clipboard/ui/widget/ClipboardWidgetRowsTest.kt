package eu.darken.octi.modules.clipboard.ui.widget

import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.clipboard.ClipboardInfo
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
class ClipboardWidgetRowsTest {

    private val selfDeviceId = DeviceId(id = "self-device")
    private val peerDeviceId = DeviceId(id = "peer-device")
    private val clipboardModuleId = ModuleId("eu.darken.octi.module.core.clipboard")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private val now = Clock.System.now()

    private fun clipboardModuleData(deviceId: DeviceId, modifiedAt: Instant): ModuleData<ClipboardInfo> =
        ModuleData(
            modifiedAt = modifiedAt,
            deviceId = deviceId,
            moduleId = clipboardModuleId,
            data = ClipboardInfo(),
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

    private fun rowsFor(peerMetaAt: Instant, peerClipboardAt: Instant): List<ClipboardDeviceRow> {
        val metaState = BaseModuleRepo.State(
            moduleId = metaModuleId,
            self = metaModuleData(selfDeviceId, "MyPhone", now),
            isOthersInitialized = true,
            others = listOf(metaModuleData(peerDeviceId, "Peer", peerMetaAt)),
        )
        val clipboardState = BaseModuleRepo.State(
            moduleId = clipboardModuleId,
            self = clipboardModuleData(selfDeviceId, now),
            isOthersInitialized = true,
            others = listOf(clipboardModuleData(peerDeviceId, peerClipboardAt)),
        )
        return buildDeviceRows(metaState, clipboardState, selfDeviceId.id, null)
    }

    private fun List<ClipboardDeviceRow>.peer() = single { it.deviceId == peerDeviceId.id }

    @Test
    fun `a fresh peer is not stale`() {
        rowsFor(peerMetaAt = now, peerClipboardAt = now).peer().isStale shouldBe false
    }

    @Test
    fun `an eight day old peer is stale`() {
        val old = now - 8.days
        rowsFor(peerMetaAt = old, peerClipboardAt = old).peer().isStale shouldBe true
    }

    @Test
    fun `fresh clipboard data keeps a peer with old meta data current`() {
        rowsFor(peerMetaAt = now - 8.days, peerClipboardAt = now).peer().isStale shouldBe false
    }
}

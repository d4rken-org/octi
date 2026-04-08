package eu.darken.octi.modules.clipboard.ui.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ClipboardWidgetContentGlanceTest {

    private val selfDeviceId = DeviceId(id = "self-device")
    private val remoteDeviceId = DeviceId(id = "remote-device")

    private val clipboardModuleId = ModuleId("eu.darken.octi.module.core.clipboard")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private fun clipboardModuleData(
        deviceId: DeviceId,
        info: ClipboardInfo,
    ): ModuleData<ClipboardInfo> = ModuleData(
        modifiedAt = Instant.fromEpochMilliseconds(0),
        deviceId = deviceId,
        moduleId = clipboardModuleId,
        data = info,
    )

    private fun metaModuleData(
        deviceId: DeviceId,
        label: String,
    ): ModuleData<MetaInfo> = ModuleData(
        modifiedAt = Instant.fromEpochMilliseconds(0),
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
            deviceBootedAt = Instant.fromEpochMilliseconds(0),
            androidVersionName = "test",
            androidApiLevel = 34,
            androidSecurityPatch = null,
        ),
    )

    private fun fakeClipboardState(
        self: ClipboardInfo = ClipboardInfo(),
        remote: ClipboardInfo? = null,
    ): BaseModuleRepo.State<ClipboardInfo> = BaseModuleRepo.State(
        moduleId = clipboardModuleId,
        self = clipboardModuleData(selfDeviceId, self),
        isOthersInitialized = true,
        others = listOfNotNull(
            remote?.let { clipboardModuleData(remoteDeviceId, it) },
        ),
    )

    private fun fakeMetaState(
        selfLabel: String = "MyPhone",
        remoteLabel: String? = null,
    ): BaseModuleRepo.State<MetaInfo> = BaseModuleRepo.State(
        moduleId = metaModuleId,
        self = metaModuleData(selfDeviceId, selfLabel),
        isOthersInitialized = true,
        others = listOfNotNull(
            remoteLabel?.let { metaModuleData(remoteDeviceId, it) },
        ),
    )

    @Test
    fun `copy button is hidden when remote clipboard is empty`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            ClipboardWidgetContent(
                metaState = fakeMetaState(remoteLabel = "Pixel 9"),
                clipboardState = fakeClipboardState(remote = ClipboardInfo()),
                themeColors = null,
                maxRows = 5,
            )
        }
        onNode(hasContentDescription("Copy to clipboard")).assertDoesNotExist()
    }

    @Test
    fun `copy button is visible when remote clipboard has text`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            ClipboardWidgetContent(
                metaState = fakeMetaState(remoteLabel = "Pixel 9"),
                clipboardState = fakeClipboardState(
                    remote = ClipboardInfo(
                        type = ClipboardInfo.Type.SIMPLE_TEXT,
                        data = "Hello".encodeUtf8(),
                    ),
                ),
                themeColors = null,
                maxRows = 5,
            )
        }
        onNode(hasContentDescription("Copy to clipboard")).assertExists()
    }

    @Test
    fun `self row renders the You label`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            ClipboardWidgetContent(
                metaState = fakeMetaState(),
                clipboardState = fakeClipboardState(self = ClipboardInfo()),
                themeColors = null,
                maxRows = 5,
            )
        }
        onNode(hasText("You")).assertExists()
    }

    @Test
    fun `device type icon mapping covers every enum value`() {
        MetaInfo.DeviceType.PHONE.widgetIconRes() shouldBe R.drawable.widget_device_phone_24
        MetaInfo.DeviceType.TABLET.widgetIconRes() shouldBe R.drawable.widget_device_tablet_24
        MetaInfo.DeviceType.UNKNOWN.widgetIconRes() shouldBe R.drawable.widget_device_unknown_24
    }
}

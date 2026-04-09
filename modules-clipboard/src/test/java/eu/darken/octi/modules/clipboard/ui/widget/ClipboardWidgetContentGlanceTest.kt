package eu.darken.octi.modules.clipboard.ui.widget

import android.content.Context
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.R as CommonR
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
        extraRemotes: List<Pair<DeviceId, ClipboardInfo>> = emptyList(),
    ): BaseModuleRepo.State<ClipboardInfo> = BaseModuleRepo.State(
        moduleId = clipboardModuleId,
        self = clipboardModuleData(selfDeviceId, self),
        isOthersInitialized = true,
        others = buildList {
            remote?.let { add(clipboardModuleData(remoteDeviceId, it)) }
            extraRemotes.forEach { (id, info) -> add(clipboardModuleData(id, info)) }
        },
    )

    private fun fakeMetaState(
        selfLabel: String = "MyPhone",
        remoteLabel: String? = null,
        extraRemotes: List<Pair<DeviceId, String>> = emptyList(),
    ): BaseModuleRepo.State<MetaInfo> = BaseModuleRepo.State(
        moduleId = metaModuleId,
        self = metaModuleData(selfDeviceId, selfLabel),
        isOthersInitialized = true,
        others = buildList {
            remoteLabel?.let { add(metaModuleData(remoteDeviceId, it)) }
            extraRemotes.forEach { (id, label) -> add(metaModuleData(id, label)) }
        },
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

    @Test
    fun `filter with matching remote id keeps remote row and self row`() = runGlanceAppWidgetUnitTest {
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
                allowedDeviceIds = setOf(remoteDeviceId.id),
            )
        }
        onNode(hasText("Pixel 9")).assertExists()
        onNode(hasText("You")).assertExists()
    }

    @Test
    fun `filter with unknown id hides remote rows but keeps self row`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
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
                allowedDeviceIds = setOf("unknown-device-id"),
            )
        }
        onNode(hasText("Pixel 9")).assertDoesNotExist()
        onNode(hasText("You")).assertExists()
        onNode(hasText(context.getString(CommonR.string.widget_empty_label))).assertExists()
    }

    @Test
    fun `filter targeting self id renders empty state and keeps self share row`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
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
                allowedDeviceIds = setOf(selfDeviceId.id),
            )
        }
        onNode(hasText("Pixel 9")).assertDoesNotExist()
        onNode(hasText("You")).assertExists()
        onNode(hasText(context.getString(CommonR.string.widget_empty_label))).assertExists()
    }

    @Test
    fun `unfiltered empty remote list renders the no-sync-devices label`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        provideComposable {
            ClipboardWidgetContent(
                metaState = fakeMetaState(remoteLabel = null),
                clipboardState = fakeClipboardState(remote = null),
                themeColors = null,
                maxRows = 5,
                allowedDeviceIds = null,
            )
        }
        onNode(hasText(context.getString(CommonR.string.widget_no_sync_devices_label))).assertExists()
        onNode(hasText(context.getString(CommonR.string.widget_empty_label))).assertDoesNotExist()
        onNode(hasText("You")).assertExists()
    }

    @Test
    fun `filter is applied before take so a late-sorting selection survives small maxRows`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        val aId = DeviceId("a-phone")
        val mId = DeviceId("m-phone")
        val zId = DeviceId("z-phone")
        provideComposable {
            ClipboardWidgetContent(
                metaState = fakeMetaState(
                    remoteLabel = null,
                    extraRemotes = listOf(aId to "Alpha", mId to "Mike", zId to "Zulu"),
                ),
                clipboardState = fakeClipboardState(
                    remote = null,
                    extraRemotes = listOf(
                        aId to ClipboardInfo(type = ClipboardInfo.Type.SIMPLE_TEXT, data = "a".encodeUtf8()),
                        mId to ClipboardInfo(type = ClipboardInfo.Type.SIMPLE_TEXT, data = "m".encodeUtf8()),
                        zId to ClipboardInfo(type = ClipboardInfo.Type.SIMPLE_TEXT, data = "z".encodeUtf8()),
                    ),
                ),
                themeColors = null,
                maxRows = 1,
                allowedDeviceIds = setOf(zId.id),
            )
        }
        onNode(hasText("Zulu")).assertExists()
        onNode(hasText("Alpha")).assertDoesNotExist()
        onNode(hasText("Mike")).assertDoesNotExist()
    }
}

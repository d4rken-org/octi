package eu.darken.octi.modules.power.ui.widget

import android.content.Context
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.sync.core.DeviceId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BatteryWidgetContentGlanceTest {

    private val selfDeviceId = DeviceId(id = "self-device")
    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private fun powerInfo(level: Int = 80, scale: Int = 100, charging: Boolean = false) = PowerInfo(
        status = if (charging) PowerInfo.Status.CHARGING else PowerInfo.Status.DISCHARGING,
        battery = PowerInfo.Battery(level = level, scale = scale, health = 1, temp = 25f),
        chargeIO = PowerInfo.ChargeIO(
            currentNow = null,
            currenAvg = null,
            fullSince = null,
            fullAt = null,
            emptyAt = null,
        ),
    )

    private fun powerModuleData(deviceId: DeviceId, info: PowerInfo = powerInfo()): ModuleData<PowerInfo> =
        ModuleData(
            modifiedAt = Instant.fromEpochMilliseconds(0),
            deviceId = deviceId,
            moduleId = powerModuleId,
            data = info,
        )

    private fun metaModuleData(deviceId: DeviceId, label: String): ModuleData<MetaInfo> = ModuleData(
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

    private fun powerState(
        selfInfo: PowerInfo = powerInfo(),
        extras: List<Pair<DeviceId, PowerInfo>> = emptyList(),
    ): BaseModuleRepo.State<PowerInfo> = BaseModuleRepo.State(
        moduleId = powerModuleId,
        self = powerModuleData(selfDeviceId, selfInfo),
        isOthersInitialized = true,
        others = extras.map { (id, info) -> powerModuleData(id, info) },
    )

    private fun metaState(
        selfLabel: String = "MyPhone",
        extras: List<Pair<DeviceId, String>> = emptyList(),
    ): BaseModuleRepo.State<MetaInfo> = BaseModuleRepo.State(
        moduleId = metaModuleId,
        self = metaModuleData(selfDeviceId, selfLabel),
        isOthersInitialized = true,
        others = extras.map { (id, label) -> metaModuleData(id, label) },
    )

    @Test
    fun `device row renders the label and percent`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            BatteryWidgetContent(
                metaState = metaState(),
                powerState = powerState(selfInfo = powerInfo(level = 73)),
                themeColors = null,
                maxRows = 5,
            )
        }
        onNode(hasText("MyPhone")).assertExists()
        onNode(hasText("73%")).assertExists()
    }

    @Test
    fun `more-than-maxRows devices show overflow indicator instead of truncating`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        val extraDevices = (1..5).map { DeviceId("dev-$it") }
        provideComposable {
            BatteryWidgetContent(
                metaState = metaState(extras = extraDevices.mapIndexed { i, id -> id to "Device ${i + 1}" }),
                powerState = powerState(extras = extraDevices.map { it to powerInfo() }),
                themeColors = null,
                maxRows = 3,
            )
        }
        // 6 devices total (self + 5), maxRows=3 → 2 visible + 1 overflow row.
        onNode(hasText("Device 1")).assertExists()
        onNode(hasText("Device 2")).assertExists()
        onNode(hasText("Device 3")).assertDoesNotExist()
        onNode(hasText(context.getString(CommonR.string.widget_more_items, 4))).assertExists()
    }

    @Test
    fun `maxRows=1 with overflow uses the only slot for the indicator`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        val extras = listOf(
            DeviceId("a") to "Alpha",
            DeviceId("b") to "Bravo",
        )
        provideComposable {
            BatteryWidgetContent(
                metaState = metaState(extras = extras),
                powerState = powerState(extras = extras.map { (id, _) -> id to powerInfo() }),
                themeColors = null,
                maxRows = 1,
            )
        }
        // 3 devices total, maxRows=1 → 0 visible + overflow taking the only slot.
        onNode(hasText("MyPhone")).assertDoesNotExist()
        onNode(hasText("Alpha")).assertDoesNotExist()
        onNode(hasText(context.getString(CommonR.string.widget_more_items, 3))).assertExists()
    }

    @Test
    fun `filter with unknown id renders the empty state`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        provideComposable {
            BatteryWidgetContent(
                metaState = metaState(),
                powerState = powerState(),
                themeColors = null,
                maxRows = 5,
                allowedDeviceIds = setOf("unknown-device-id"),
            )
        }
        onNode(hasText(context.getString(CommonR.string.widget_empty_label))).assertExists()
        onNode(hasText("MyPhone")).assertDoesNotExist()
    }

    @Test
    fun `unfiltered empty state renders no-sync-devices label`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        provideComposable {
            BatteryWidgetContent(
                metaState = BaseModuleRepo.State<MetaInfo>(moduleId = metaModuleId, self = null),
                powerState = BaseModuleRepo.State<PowerInfo>(moduleId = powerModuleId, self = null),
                themeColors = null,
                maxRows = 5,
            )
        }
        onNode(hasText(context.getString(CommonR.string.widget_no_sync_devices_label))).assertExists()
    }
}

package eu.darken.octi.modules.connectivity.ui.widget

import android.content.Context
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NetworkWidgetContentGlanceTest {

    private val selfDeviceId = DeviceId(id = "self-device")
    private val connectivityModuleId = ModuleId("eu.darken.octi.module.core.connectivity")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")

    private fun connectivityInfo(
        type: ConnectivityInfo.ConnectionType? = ConnectivityInfo.ConnectionType.WIFI,
        localIp: String? = "192.168.1.10",
        publicIp: String? = "203.0.113.5",
    ) = ConnectivityInfo(
        connectionType = type,
        publicIp = publicIp,
        localAddressIpv4 = localIp,
        localAddressIpv6 = null,
        gatewayIp = null,
        dnsServers = null,
    )

    private fun connectivityModuleData(
        deviceId: DeviceId,
        info: ConnectivityInfo = connectivityInfo(),
    ): ModuleData<ConnectivityInfo> = ModuleData(
        modifiedAt = Instant.fromEpochMilliseconds(0),
        deviceId = deviceId,
        moduleId = connectivityModuleId,
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

    private fun connectivityState(
        selfInfo: ConnectivityInfo = connectivityInfo(),
        extras: List<Pair<DeviceId, ConnectivityInfo>> = emptyList(),
    ): BaseModuleRepo.State<ConnectivityInfo> = BaseModuleRepo.State(
        moduleId = connectivityModuleId,
        self = connectivityModuleData(selfDeviceId, selfInfo),
        isOthersInitialized = true,
        others = extras.map { (id, info) -> connectivityModuleData(id, info) },
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
    fun `two-column tile renders the device label and IPs`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            NetworkWidgetContent(
                metaState = metaState(selfLabel = "Pixel 9"),
                connectivityState = connectivityState(
                    selfInfo = connectivityInfo(localIp = "10.0.0.5", publicIp = "1.2.3.4"),
                ),
                themeColors = null,
                maxRows = 4,
                widthDp = 300f,
                heightDp = 200f,
            )
        }
        onNode(hasText("Pixel 9")).assertExists()
        onNode(hasText("10.0.0.5")).assertExists()
        onNode(hasText("1.2.3.4")).assertExists()
    }

    @Test
    fun `compact row renders name and combined IP string`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            NetworkWidgetContent(
                metaState = metaState(selfLabel = "Pixel 9"),
                connectivityState = connectivityState(
                    selfInfo = connectivityInfo(localIp = "10.0.0.5", publicIp = "1.2.3.4"),
                ),
                themeColors = null,
                maxRows = 4,
                widthDp = 150f,
                heightDp = 200f,
            )
        }
        onNode(hasText("Pixel 9")).assertExists()
        // Compact row joins local and public into a single string.
        onNode(hasText("10.0.0.5 · 1.2.3.4")).assertExists()
    }

    @Test
    fun `wide-short widget falls back to compact rows`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        // 220 wide, 50 tall — width hits the two-column threshold but height doesn't.
        provideComposable {
            NetworkWidgetContent(
                metaState = metaState(selfLabel = "Pixel 9"),
                connectivityState = connectivityState(
                    selfInfo = connectivityInfo(localIp = "10.0.0.5", publicIp = "1.2.3.4"),
                ),
                themeColors = null,
                maxRows = 1,
                widthDp = 220f,
                heightDp = 50f,
            )
        }
        // Compact-row signature: name + joined-IP single string. The tile layout would split
        // them and not produce this combined string.
        onNode(hasText("10.0.0.5 · 1.2.3.4")).assertExists()
    }

    @Test
    fun `more-than-maxRows devices show overflow indicator`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        val extras = (1..5).map { DeviceId("dev-$it") to "Device $it" }
        provideComposable {
            NetworkWidgetContent(
                metaState = metaState(extras = extras),
                connectivityState = connectivityState(
                    extras = extras.map { (id, _) -> id to connectivityInfo() },
                ),
                themeColors = null,
                maxRows = 3,
                widthDp = 150f,
                heightDp = 200f,
            )
        }
        // 6 devices, maxRows=3 → 2 visible + 1 overflow.
        onNode(hasText("Device 1")).assertExists()
        onNode(hasText("Device 2")).assertExists()
        onNode(hasText("Device 3")).assertDoesNotExist()
        onNode(hasText(context.getString(CommonR.string.widget_more_items, 4))).assertExists()
    }

    @Test
    fun `single slot overflow takes the only slot`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        val extras = listOf(
            DeviceId("a") to "Alpha",
            DeviceId("b") to "Bravo",
        )
        provideComposable {
            NetworkWidgetContent(
                metaState = metaState(extras = extras),
                connectivityState = connectivityState(
                    extras = extras.map { (id, _) -> id to connectivityInfo() },
                ),
                themeColors = null,
                maxRows = 1,
                widthDp = 150f,
                heightDp = 200f,
            )
        }
        onNode(hasText("MyPhone")).assertDoesNotExist()
        onNode(hasText("Alpha")).assertDoesNotExist()
        onNode(hasText(context.getString(CommonR.string.widget_more_items, 3))).assertExists()
    }

    @Test
    fun `filter with unknown id renders the empty state`() = runGlanceAppWidgetUnitTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContext(context)
        provideComposable {
            NetworkWidgetContent(
                metaState = metaState(),
                connectivityState = connectivityState(),
                themeColors = null,
                maxRows = 5,
                widthDp = 300f,
                heightDp = 200f,
                allowedDeviceIds = setOf("unknown-device-id"),
            )
        }
        onNode(hasText(context.getString(CommonR.string.widget_empty_label))).assertExists()
        onNode(hasText("MyPhone")).assertDoesNotExist()
    }
}

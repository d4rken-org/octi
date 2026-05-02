package eu.darken.octi.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.widget.WidgetConfigDevice
import eu.darken.octi.common.widget.WidgetConfigScreen
import eu.darken.octi.common.widget.WidgetInstanceConfig
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.common.widget.widgetDefaultColors
import eu.darken.octi.main.ui.dashboard.DashboardVM
import eu.darken.octi.main.ui.dashboard.DashboardScreen
import eu.darken.octi.main.ui.dashboard.LocalDashboardBuildChannelLabel
import eu.darken.octi.main.ui.dashboard.LocalDashboardShowDebugBuildDetails
import eu.darken.octi.main.ui.dashboard.TileLayoutConfig
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.apps.AppsModule
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.AppsSortMode
import eu.darken.octi.modules.apps.ui.appslist.AppsListScreen
import eu.darken.octi.modules.apps.ui.appslist.AppsListVM
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.ClipboardModule
import eu.darken.octi.modules.connectivity.ConnectivityModule
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.modules.files.core.FileKey
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.modules.files.ui.list.FileShareListScreen
import eu.darken.octi.modules.files.ui.list.FileShareListVM
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.PowerModule
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.ui.dashboard.LocalPowerTemperatureUnit
import eu.darken.octi.modules.wifi.WifiModule
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.sync.core.ConnectorCapabilities
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.ConnectorOperation
import eu.darken.octi.sync.core.ConnectorPauseReason
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.LocalConnectorContributions
import eu.darken.octi.sync.core.OperationId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.blob.StorageSnapshot
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.ui.list.SyncListScreen
import eu.darken.octi.sync.ui.list.SyncListVM
import eu.darken.octi.syncs.gdrive.R as GDriveR
import eu.darken.octi.syncs.octiserver.R as OctiServerR
import eu.darken.octi.syncs.octiserver.ui.OctiServerIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.clipboard.R as ClipboardR
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.power.R as PowerR

internal const val DS = "spec:width=1080px,height=2400px,dpi=428"

private val NOW = Instant.parse("2026-05-01T12:00:00Z")
private val SELF = DeviceId("pixel-8")
private val TABLET = DeviceId("galaxy-tab-s9")

private data class PreviewUpgradeInfo(
    override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY,
    override val isPro: Boolean = true,
    override val upgradedAt: Instant? = NOW - 30.hours,
) : UpgradeRepo.Info

@Composable
internal fun DashboardContent() = PreviewWrapper {
    val liveSyncAt = Clock.System.now()
    CompositionLocalProvider(
        LocalConnectorContributions provides previewConnectorContributions,
        LocalDashboardBuildChannelLabel provides null,
        LocalDashboardShowDebugBuildDetails provides false,
        LocalPowerTemperatureUnit provides TemperatureFormatter.TemperatureUnit.CELSIUS,
    ) {
        DashboardScreen(
            state = DashboardVM.State(
                devices = listOf(
                    previewDevice(SELF, "Pixel 8", MetaInfo.DeviceType.PHONE, 76, isCollapsed = false),
                    previewDevice(TABLET, "Galaxy Tab S9", MetaInfo.DeviceType.TABLET, 42, isCollapsed = false),
                ),
                deviceCount = 2,
                syncStatus = DashboardVM.SyncStatus.Idle(
                    lastSyncAt = liveSyncAt,
                    connectorTypes = listOf(ConnectorType.OCTISERVER),
                    syncDetail = DashboardVM.SyncDetail(
                        modules = emptyList(),
                        connectors = listOf(
                            DashboardVM.ConnectorDetail(
                                connectorId = octiServerId,
                                type = ConnectorType.OCTISERVER,
                                isBusy = false,
                                lastSyncAt = liveSyncAt,
                                accountLabel = "octi.example.com",
                            ),
                        ),
                    ),
                    orchestratorState = previewLiveOrchestratorState(),
                    now = liveSyncAt,
                    totalDeviceCount = 2,
                ),
                isOffline = false,
                showSyncSetup = false,
                missingPermissions = emptyList(),
                update = null,
                upgradeInfo = PreviewUpgradeInfo(),
                deviceLimitReached = false,
            ),
            onRefresh = {},
            onSyncServices = {},
            onDegradedClick = {},
            onIssueClick = {},
            onConnectorDevices = {},
            onUpgrade = {},
            onSettings = {},
            onDismissSyncSetup = {},
            onSetupSync = {},
            onGrantPermission = {},
            onDismissPermission = {},
            onDismissUpdate = {},
            onViewUpdate = {},
            onStartUpdate = {},
            onToggleSyncExpanded = {},
            onToggleDeviceCollapsed = {},
            onPowerAlerts = {},
            onAppsList = {},
            onInstallLatestApp = {},
            onClearClipboard = {},
            onShareClipboard = {},
            onCopyClipboard = {},
            onFileShareClicked = {},
            onWifiPermissionGrant = {},
        )
    }
}

@Composable
internal fun FileSharingContent() = PreviewWrapper {
    val file1 = FileShareInfo.SharedFile(
        name = "Travel documents.pdf",
        mimeType = "application/pdf",
        size = 2_400_000,
        blobKey = "travel-documents",
        checksum = "checksum-1",
        sharedAt = NOW - 15.hours,
        expiresAt = NOW + 33.hours,
        availableOn = setOf(gdriveId.idString, octiServerId.idString),
    )
    val file2 = FileShareInfo.SharedFile(
        name = "Router config.json",
        mimeType = "application/json",
        size = 84_000,
        blobKey = "router-config",
        checksum = "checksum-2",
        sharedAt = NOW - 2.hours,
        expiresAt = NOW + 46.hours,
        availableOn = setOf(octiServerId.idString),
    )

    FileShareListScreen(
        state = FileShareListVM.State(
            files = listOf(
                FileShareListVM.FileItem(
                    key = FileKey.of(SELF, file1.blobKey),
                    sharedFile = file1,
                    ownerDeviceId = SELF,
                    ownerDeviceLabel = "Pixel 8",
                    isOwn = true,
                    canOpenOrSave = true,
                ),
                FileShareListVM.FileItem(
                    key = FileKey.of(TABLET, file2.blobKey),
                    sharedFile = file2,
                    ownerDeviceId = TABLET,
                    ownerDeviceLabel = "Galaxy Tab S9",
                    isOwn = false,
                    canOpenOrSave = true,
                ),
            ),
            availableDevices = listOf(
                FileShareListVM.DeviceOption(SELF, "Pixel 8", isOwn = true),
                FileShareListVM.DeviceOption(TABLET, "Galaxy Tab S9", isOwn = false),
            ),
            quotaItems = listOf(previewStorageStatus(octiServerId, used = 42_000_000, total = 100_000_000)),
        ),
    )
}

@Composable
internal fun AppsContent() = PreviewWrapper {
    AppsListScreen(
        state = AppsListVM.State(
            deviceLabel = "Pixel 8",
            items = PREVIEW_PACKAGES.map { it.toPkgItem() },
            sortMode = AppsSortMode.NAME,
        ),
        onNavigateUp = {},
        onSort = {},
    )
}

@Composable
internal fun SyncServicesContent() = PreviewWrapper {
    CompositionLocalProvider(LocalConnectorContributions provides previewConnectorContributions) {
        SyncListScreen(
            state = SyncListVM.State(
                connectors = listOf(
                    SyncListVM.ConnectorItem(
                        connectorId = gdriveId,
                        connector = PreviewConnector(gdriveId, "octi@example.com"),
                        ourState = previewSyncState(lastActionAt = NOW - 5.seconds),
                        storageStatus = previewStorageStatus(gdriveId, used = 6_000_000, total = 15_000_000_000),
                        otherStates = listOf(previewSyncState(lastActionAt = NOW - 40.seconds)),
                        pauseReason = null,
                        isPaused = false,
                        isBusy = false,
                    ),
                    SyncListVM.ConnectorItem(
                        connectorId = octiServerId,
                        connector = PreviewConnector(octiServerId, "octi.example.com"),
                        ourState = previewSyncState(lastActionAt = NOW - 2.hours),
                        storageStatus = previewStorageStatus(octiServerId, used = 4_300, total = 50_000_000),
                        otherStates = listOf(previewSyncState(lastActionAt = NOW - 12.minutes)),
                        pauseReason = null,
                        isPaused = false,
                        isBusy = false,
                    ),
                ),
                isPro = true,
                deviceId = SELF.id,
            ),
            onNavigateUp = {},
            onAddConnector = {},
            onTogglePause = {},
            onForceSync = {},
            onViewDevices = {},
            onLinkNewDevice = {},
            onReset = {},
            onDisconnect = {},
        )
    }
}

@Composable
internal fun WidgetGalleryContent() = PreviewWrapper {
    val preset = WidgetTheme.BLUE
    WidgetConfigScreen(
        initialMode = WidgetInstanceConfig.MODE_CUSTOM,
        initialPresetName = preset.name,
        initialBgColor = preset.presetBg,
        initialAccentColor = preset.presetAccent,
        availableDevices = listOf(
            WidgetConfigDevice(
                id = SELF.id,
                label = "Pixel 8",
                subtitle = "5 min ago",
                icon = Icons.TwoTone.PhoneAndroid,
            ),
            WidgetConfigDevice(
                id = TABLET.id,
                label = "Galaxy Tab S9",
                subtitle = "12 min ago",
                icon = Icons.TwoTone.Tablet,
            ),
        ),
        initialSelectedDeviceIds = setOf(SELF.id, TABLET.id),
        onClose = {},
        onApply = { _, _, _, _, _ -> },
        previewContent = { colors ->
            WidgetPreviewStack(colors ?: widgetDefaultColors())
        },
    )
}

private fun previewDevice(
    deviceId: DeviceId,
    label: String,
    type: MetaInfo.DeviceType,
    batteryLevel: Int,
    isCollapsed: Boolean,
) = DashboardVM.DeviceItem(
    now = NOW,
    deviceId = deviceId,
    meta = ModuleData(
        modifiedAt = NOW - 5.seconds,
        deviceId = deviceId,
        moduleId = eu.darken.octi.modules.meta.MetaModule.MODULE_ID,
        data = MetaInfo(
            deviceLabel = label,
            deviceId = deviceId,
            octiVersionName = "1.0.0",
            octiGitSha = "preview",
            deviceManufacturer = if (type == MetaInfo.DeviceType.TABLET) "Samsung" else "Google",
            deviceName = label,
            deviceType = type,
            deviceBootedAt = NOW - 36.hours,
            androidVersionName = "15",
            androidApiLevel = 35,
            androidSecurityPatch = "2026-04-05",
        ),
    ),
    moduleItems = listOf(
        DashboardVM.ModuleItem.Power(
            data = previewModuleData(
                deviceId = deviceId,
                moduleId = PowerModule.MODULE_ID,
                data = previewPowerInfo(batteryLevel),
            ),
            showSettings = true,
        ),
        DashboardVM.ModuleItem.Wifi(
            data = previewModuleData(
                deviceId = deviceId,
                moduleId = WifiModule.MODULE_ID,
                data = WifiInfo(
                    currentWifi = WifiInfo.Wifi(
                        ssid = if (deviceId == SELF) "Octi Office" else "Octi Guest",
                        reception = if (deviceId == SELF) 0.82f else 0.63f,
                        freqType = WifiInfo.Wifi.Type.FIVE_GHZ,
                    ),
                ),
            ),
            showPermissionAction = false,
        ),
        DashboardVM.ModuleItem.Connectivity(
            data = previewModuleData(
                deviceId = deviceId,
                moduleId = ConnectivityModule.MODULE_ID,
                data = ConnectivityInfo(
                    connectionType = ConnectivityInfo.ConnectionType.WIFI,
                    publicIp = "203.0.113.${if (deviceId == SELF) 42 else 77}",
                    localAddressIpv4 = if (deviceId == SELF) "192.168.1.42" else "192.168.1.77",
                    localAddressIpv6 = null,
                    gatewayIp = "192.168.1.1",
                    dnsServers = listOf("1.1.1.1", "8.8.8.8"),
                ),
            ),
        ),
        DashboardVM.ModuleItem.Clipboard(
            data = previewModuleData(
                deviceId = deviceId,
                moduleId = ClipboardModule.MODULE_ID,
                data = ClipboardInfo(
                    type = ClipboardInfo.Type.SIMPLE_TEXT,
                    data = if (deviceId == SELF) {
                        "octi.example.com/link".encodeUtf8()
                    } else {
                        "Meeting room Wi-Fi code".encodeUtf8()
                    },
                ),
            ),
            isOurDevice = deviceId == SELF,
        ),
        DashboardVM.ModuleItem.FileShare(
            data = previewModuleData(
                deviceId = deviceId,
                moduleId = FileShareModule.MODULE_ID,
                data = previewFileShareInfo(),
            ),
            isOurDevice = deviceId == SELF,
            isSharingAvailable = true,
            configuredConnectorIds = setOf(gdriveId.idString, octiServerId.idString),
        ),
        DashboardVM.ModuleItem.Apps(
            data = previewModuleData(
                deviceId = deviceId,
                moduleId = AppsModule.MODULE_ID,
                data = previewAppsInfo(),
            ),
        ),
    ),
    tileLayout = if (deviceId == TABLET) {
        TileLayoutConfig(wideModules = emptySet())
    } else {
        TileLayoutConfig()
    },
    isCollapsed = isCollapsed,
    isLimited = false,
    isCurrentDevice = deviceId == SELF,
)

private fun <T> previewModuleData(
    deviceId: DeviceId,
    moduleId: eu.darken.octi.module.core.ModuleId,
    data: T,
) = ModuleData(
    modifiedAt = NOW - 60.seconds,
    deviceId = deviceId,
    moduleId = moduleId,
    data = data,
)

private fun previewPowerInfo(level: Int) = PowerInfo(
    status = PowerInfo.Status.DISCHARGING,
    battery = PowerInfo.Battery(level = level, scale = 100, health = 2, temp = 28.5f),
    chargeIO = PowerInfo.ChargeIO(
        currentNow = -820_000,
        currenAvg = -760_000,
        fullSince = null,
        fullAt = null,
        emptyAt = Clock.System.now() + if (level >= 70) 9.hours else 5.hours,
    ),
)

private fun previewAppsInfo() = AppsInfo(
    installedPackages = PREVIEW_PACKAGES,
)

private fun previewFileShareInfo() = FileShareInfo(
    files = listOf(
        FileShareInfo.SharedFile(
            name = "Travel documents.pdf",
            mimeType = "application/pdf",
            size = 2_400_000,
            blobKey = "travel-documents",
            checksum = "checksum-1",
            sharedAt = NOW - 15.hours,
            expiresAt = NOW + 33.hours,
            availableOn = setOf(gdriveId.idString, octiServerId.idString),
        ),
        FileShareInfo.SharedFile(
            name = "Router config.json",
            mimeType = "application/json",
            size = 84_000,
            blobKey = "router-config",
            checksum = "checksum-2",
            sharedAt = NOW - 2.hours,
            expiresAt = NOW + 46.hours,
            availableOn = setOf(octiServerId.idString),
        ),
    ),
)

private fun previewOrchestratorState() = eu.darken.octi.sync.core.SyncOrchestrator.State(
    quickSync = eu.darken.octi.sync.core.SyncOrchestrator.QuickSyncState(
        isActive = false,
        connectorModes = emptyMap(),
    ),
    backgroundSync = eu.darken.octi.sync.core.SyncOrchestrator.BackgroundSyncState(
        defaultWorker = eu.darken.octi.sync.core.SyncOrchestrator.BackgroundSyncState.WorkerInfo(
            isEnabled = true,
            isRunning = false,
            isBlocked = false,
            nextRunAt = NOW + 1.hours,
        ),
        chargingWorker = eu.darken.octi.sync.core.SyncOrchestrator.BackgroundSyncState.WorkerInfo(
            isEnabled = true,
            isRunning = false,
            isBlocked = false,
            nextRunAt = null,
        ),
    ),
)

private fun previewLiveOrchestratorState() = eu.darken.octi.sync.core.SyncOrchestrator.State(
    quickSync = eu.darken.octi.sync.core.SyncOrchestrator.QuickSyncState(
        isActive = true,
        connectorModes = mapOf(octiServerId to SyncConnector.EventMode.LIVE),
    ),
    backgroundSync = eu.darken.octi.sync.core.SyncOrchestrator.BackgroundSyncState(
        defaultWorker = eu.darken.octi.sync.core.SyncOrchestrator.BackgroundSyncState.WorkerInfo(
            isEnabled = false,
            isRunning = false,
            isBlocked = false,
            nextRunAt = null,
        ),
        chargingWorker = eu.darken.octi.sync.core.SyncOrchestrator.BackgroundSyncState.WorkerInfo(
            isEnabled = false,
            isRunning = false,
            isBlocked = false,
            nextRunAt = null,
        ),
    ),
)

private val PREVIEW_PACKAGES = listOf(
    previewPkg(
        packageName = "eu.darken.bluemusic",
        label = "Bluetooth Volume Manager",
        versionName = "3.1.2",
        versionCode = 30102,
        installedAgoHours = 2,
    ),
    previewPkg(
        packageName = "eu.darken.capod",
        label = "CAPod",
        versionName = "2.16.0",
        versionCode = 21600,
        installedAgoHours = 3,
    ),
    previewPkg(
        packageName = "eu.darken.octi",
        label = "Octi",
        versionName = "1.0.0-rc1",
        versionCode = 10000011,
        installedAgoHours = 4,
    ),
    previewPkg(
        packageName = "eu.darken.sdmse",
        label = "SD Maid 2/SE",
        versionName = "1.4.6-rc0",
        versionCode = 104060,
        installedAgoHours = 5,
    ),
    previewPkg(
        packageName = "com.google.android.apps.maps",
        label = "Google Maps",
        versionName = "11.98.0",
        versionCode = 119800001,
        installedAgoHours = 18,
    ),
    previewPkg(
        packageName = "org.mozilla.firefox",
        label = "Firefox",
        versionName = "122.0",
        versionCode = 1220000,
        installedAgoHours = 22,
    ),
    previewPkg(
        packageName = "org.fdroid.fdroid",
        label = "F-Droid",
        versionName = "1.19.0",
        versionCode = 1019000,
        installedAgoHours = 26,
        installerPkg = null,
    ),
    previewPkg(
        packageName = "com.spotify.music",
        label = "Spotify",
        versionName = "8.9.10",
        versionCode = 80910000,
        installedAgoHours = 30,
    ),
    previewPkg(
        packageName = "com.whatsapp",
        label = "WhatsApp",
        versionName = "2.24.1",
        versionCode = 224010000,
        installedAgoHours = 34,
    ),
    previewPkg(
        packageName = "com.google.android.keep",
        label = "Google Keep",
        versionName = "5.24.0",
        versionCode = 5240000,
        installedAgoHours = 38,
    ),
)

private fun previewPkg(
    packageName: String,
    label: String,
    versionName: String,
    versionCode: Long,
    installedAgoHours: Int,
    installerPkg: String? = "com.android.vending",
) = AppsInfo.Pkg(
    packageName = packageName,
    label = label,
    versionCode = versionCode,
    versionName = versionName,
    installedAt = NOW - installedAgoHours.hours,
    installerPkg = installerPkg,
)

private fun AppsInfo.Pkg.toPkgItem() = AppsListVM.PkgItem(
    pkg = this,
    onClick = {},
)

private val gdriveId = ConnectorId(ConnectorType.GDRIVE, "appdata", "octi@example.com")
private val octiServerId = ConnectorId(ConnectorType.OCTISERVER, "http://192.168.1.42:8080", "acc-preview")

private val previewConnectorContributions = mapOf(
    ConnectorType.GDRIVE to PreviewContribution(
        type = ConnectorType.GDRIVE,
        labelRes = GDriveR.string.sync_gdrive_type_label,
        descriptionRes = GDriveR.string.sync_gdrive_type_appdata_description,
        subtitle = { stringResource(GDriveR.string.sync_gdrive_appdata_label) },
        icon = { modifier, tint ->
            Icon(
                painter = painterResource(GDriveR.drawable.ic_baseline_gdrive_24),
                contentDescription = null,
                modifier = modifier,
                tint = tint,
            )
        },
    ),
    ConnectorType.OCTISERVER to PreviewContribution(
        type = ConnectorType.OCTISERVER,
        labelRes = OctiServerR.string.sync_octiserver_type_label,
        descriptionRes = OctiServerR.string.sync_octiserver_type_description,
        subtitle = { connector -> connector.identifier.subtype },
        icon = { modifier, tint ->
            OctiServerIcon(modifier = modifier, tint = tint)
        },
    ),
)

private class PreviewContribution(
    override val type: ConnectorType,
    override val labelRes: Int,
    override val descriptionRes: Int,
    private val subtitle: @Composable (SyncConnector) -> String? = { null },
    private val icon: @Composable (Modifier, Color) -> Unit,
) : ConnectorUiContribution {
    override val displayOrder: Int = 0

    @Composable
    override fun Icon(modifier: Modifier, tint: Color) {
        icon(modifier, tint)
    }

    override fun addAccountDestination(): NavigationDestination = object : NavigationDestination {}

    @Composable
    override fun listCardTitle(connector: SyncConnector): String = stringResource(labelRes)

    @Composable
    override fun listCardSubtitle(connector: SyncConnector): String? = subtitle(connector)

    @Composable
    override fun listCardAccountValue(connector: SyncConnector): String = connector.accountLabel

    @Composable
    override fun ActionsSheet(
        connector: SyncConnector,
        state: SyncConnectorState,
        isPaused: Boolean,
        pauseReason: ConnectorPauseReason?,
        isPro: Boolean,
        onDismiss: () -> Unit,
        onTogglePause: () -> Unit,
        onForceSync: () -> Unit,
        onViewDevices: () -> Unit,
        onLinkNewDevice: () -> Unit,
        onReset: () -> Unit,
        onDisconnect: () -> Unit,
    ) = Unit
}

private class PreviewConnector(
    override val identifier: ConnectorId,
    override val accountLabel: String,
) : SyncConnector {
    override val capabilities: ConnectorCapabilities = ConnectorCapabilities.DEFAULT_FOR_TEST
    override val state: Flow<SyncConnectorState> = MutableStateFlow(previewSyncState(lastActionAt = NOW - 5.seconds))
    override val data: Flow<SyncRead?> = MutableStateFlow(null)
    override val operations: StateFlow<List<ConnectorOperation>> = MutableStateFlow(emptyList())
    override val completions: SharedFlow<ConnectorOperation.Terminal> = MutableSharedFlow()

    override fun submit(command: ConnectorCommand): OperationId = OperationId.create()

    override suspend fun await(id: OperationId): ConnectorOperation.Terminal = ConnectorOperation.Succeeded(
        id = id,
        command = ConnectorCommand.Sync(),
        submittedAt = NOW,
        startedAt = NOW,
        finishedAt = NOW,
    )

    override fun dismiss(id: OperationId) = Unit
}

private fun previewSyncState(lastActionAt: Instant) = object : SyncConnectorState {
    override val lastActionAt: Instant = lastActionAt
    override val lastError: Exception? = null
    override val deviceMetadata: List<DeviceMetadata> = listOf(
        DeviceMetadata(deviceId = SELF, version = "1.0.0", platform = "Android", label = "Pixel 8", lastSeen = NOW),
        DeviceMetadata(deviceId = TABLET, version = "1.0.0", platform = "Android", label = "Galaxy Tab S9", lastSeen = NOW),
    )
    override val issues: List<ConnectorIssue> = emptyList()
    override val isAvailable: Boolean = true
}

private fun previewStorageStatus(connectorId: ConnectorId, used: Long, total: Long) = StorageStatus.Ready(
    connectorId = connectorId,
    snapshot = StorageSnapshot(
        connectorId = connectorId,
        accountLabel = null,
        usedBytes = used,
        totalBytes = total,
        availableBytes = total - used,
        maxFileBytes = 25_000_000,
        perFileOverheadBytes = 1024,
        updatedAt = NOW,
    ),
)

@Composable
private fun WidgetPreviewStack(colors: WidgetTheme.Colors) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BatteryWidgetPreview(colors = colors)
        ClipboardWidgetPreview(colors = colors)
        NetworkWidgetPreview(colors = colors)
    }
}

@Composable
private fun BatteryWidgetPreview(colors: WidgetTheme.Colors) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(colors.containerBg))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BatteryWidgetRow(
                name = "Pixel 8",
                lastSeen = "5 min ago",
                percent = 76,
                colors = colors,
            )
            BatteryWidgetRow(
                name = "Galaxy Tab S9",
                lastSeen = "12 min ago",
                percent = 42,
                colors = colors,
            )
        }
    }
}

@Composable
private fun BatteryWidgetRow(
    name: String,
    lastSeen: String,
    percent: Int,
    colors: WidgetTheme.Colors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(colors.tileBg)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(colors.accentBg)),
            )
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(PowerR.drawable.widget_battery_full_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.onAccent),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(colors.onAccent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "\u00b7 $lastSeen",
                    fontSize = 11.sp,
                    color = Color(colors.onAccent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$percent%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(colors.onContainer),
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ClipboardWidgetPreview(colors: WidgetTheme.Colors) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(colors.containerBg))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ClipboardWidgetRow(
                iconRes = ClipboardR.drawable.widget_device_phone_24,
                title = "Pixel 8",
                detail = "octi.example.com/link",
                colors = colors,
                isAccent = false,
            )
            ClipboardWidgetRow(
                iconRes = ClipboardR.drawable.widget_clipboard_24,
                title = stringResource(ClipboardR.string.module_clipboard_widget_self_label),
                detail = "Meeting room Wi-Fi code",
                colors = colors,
                isAccent = true,
            )
        }
    }
}

@Composable
private fun ClipboardWidgetRow(
    iconRes: Int,
    title: String,
    detail: String,
    colors: WidgetTheme.Colors,
    isAccent: Boolean,
) {
    val background = if (isAccent) colors.accentBg else colors.tileBg
    val titleColor = if (isAccent) colors.onAccent else colors.onTile
    val detailColor = if (isAccent) colors.onAccent else colors.onTileVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(background)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(titleColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(titleColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = detail,
                fontSize = 10.sp,
                color = Color(detailColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NetworkWidgetPreview(colors: WidgetTheme.Colors) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(colors.containerBg))
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(colors.tileBg))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(ConnectivityR.drawable.widget_network_wifi_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(colors.onTile),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pixel 8",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(colors.onTile),
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "\u00b7 ${stringResource(ConnectivityR.string.module_connectivity_type_wifi_label)}",
                            fontSize = 10.sp,
                            color = Color(colors.onTileVariant),
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "192.168.1.42",
                        fontSize = 10.sp,
                        color = Color(colors.onTileVariant),
                        maxLines = 1,
                    )
                    Text(
                        text = "203.0.113.42",
                        fontSize = 10.sp,
                        color = Color(colors.onTileVariant),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

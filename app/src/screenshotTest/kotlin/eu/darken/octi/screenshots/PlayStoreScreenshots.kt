package eu.darken.octi.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PlayStoreLocales
@Composable
fun DashboardLight() = DashboardContent()

@PreviewTest
@PlayStoreLocalesDark
@Composable
fun DashboardDark() = DashboardContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun FileSharing() = FileSharingContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun Apps() = AppsContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun SyncServices() = SyncServicesContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun Widgets() = WidgetGalleryContent()

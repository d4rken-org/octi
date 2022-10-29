package eu.darken.octi.modules.apps.core

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.isSystemApp
import eu.darken.octi.module.core.ModuleInfoSource
import eu.darken.octi.modules.apps.core.PackageEventListener.Event.PackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsInfoSource @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    packageEventListener: PackageEventListener,
) : ModuleInfoSource<AppsInfo> {

    private val updateTrigger = MutableStateFlow(UUID.randomUUID())

    private val pm = context.packageManager

    override val info: Flow<AppsInfo> = combine(
        updateTrigger,
        packageEventListener.events.onStart { emit(PackageInstalled(BuildConfigWrap.APPLICATION_ID)) }
    ) { _, event ->
        val installedApps = pm.getInstalledPackages(0)
            .filter { !it.isSystemApp }
            .map { pkgInfo ->
                AppsInfo.Pkg(
                    packageName = pkgInfo.packageName,
                    installedAt = Instant.ofEpochMilli(pkgInfo.firstInstallTime),
                    versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
                    versionName = pkgInfo.versionName,
                    label = pkgInfo.applicationInfo?.loadLabel(pm)?.toString()
                )
            }

        AppsInfo(
            installedPackages = installedApps,
        )
    }
        .setupCommonEventHandlers(TAG) { "info" }
        .replayingShare(appScope)

    companion object {
        internal val TAG = logTag("Module", "Apps", "InfoSource")
    }
}
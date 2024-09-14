package eu.darken.octi.modules.apps.core

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.hasApiLevel

data class InstallerInfo(
    val installingPkg: String? = null,
    val initiatingPkg: String? = null,
    val originatingPkg: String? = null,
    val sourceType: SourceType = SourceType.UNSPECIFIED,
) {

    val allInstallers: List<String>
        get() = listOfNotNull(installingPkg, initiatingPkg, originatingPkg)

    val installer: String?
        get() = allInstallers.firstOrNull()

    enum class SourceType {
        UNSPECIFIED,
        STORE,
        LOCAL_FILE,
        DOWNLOADED_FILE,
        OTHER,
    }
}

@SuppressLint("NewApi")
fun PackageInfo.getInstallerInfo(
    packageManager: PackageManager,
): InstallerInfo = if (hasApiLevel(Build.VERSION_CODES.R)) {
    getInstallerInfoApi30(packageManager)
} else {
    getInstallerInfoLegacy(packageManager)
}

@RequiresApi(Build.VERSION_CODES.R)
private fun PackageInfo.getInstallerInfoApi30(packageManager: PackageManager): InstallerInfo {
    val sourceInfo = try {
        packageManager.getInstallSourceInfo(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    val sourceType = if (hasApiLevel(33)) {
        @SuppressLint("NewApi")
        when (sourceInfo?.packageSource) {
            PackageInstaller.PACKAGE_SOURCE_OTHER -> InstallerInfo.SourceType.OTHER
            PackageInstaller.PACKAGE_SOURCE_STORE -> InstallerInfo.SourceType.STORE
            PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE -> InstallerInfo.SourceType.LOCAL_FILE
            PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE -> InstallerInfo.SourceType.DOWNLOADED_FILE
            else -> InstallerInfo.SourceType.UNSPECIFIED
        }
    } else {
        InstallerInfo.SourceType.UNSPECIFIED
    }

    return InstallerInfo(
        initiatingPkg = sourceInfo?.initiatingPackageName,
        installingPkg = sourceInfo?.installingPackageName,
        originatingPkg = sourceInfo?.originatingPackageName,
        sourceType = sourceType
    )
}

private fun PackageInfo.getInstallerInfoLegacy(packageManager: PackageManager): InstallerInfo {
    val installingPkg = try {
        packageManager.getInstallerPackageName(packageName)
    } catch (e: IllegalArgumentException) {
        log(WARN) { "OS race condition, package ($packageName) was uninstalled?: ${e.asLog()}" }
        null
    }

    return InstallerInfo(installingPkg = installingPkg)
}
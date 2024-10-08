package eu.darken.octi.main.core.updater

import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import io.github.z4kn4fein.semver.Version

fun UpdateChecker.Update.isNewer(): Boolean = try {
    val current = Version.parse(BuildConfigWrap.VERSION_NAME, strict = false)
    val latest = Version.parse(versionName, strict = false)
    latest > current
} catch (e: Exception) {
    log(ERROR) { "Failed version check: ${e.asLog()}" }
    false
}

suspend fun UpdateChecker.getUpdate(betaConsent: Boolean): UpdateChecker.Update? {
    if (!isCheckSupported()) {
        log(TAG, INFO) { "Update check is not supported" }
        return null
    }

    val currentChannel = when (BuildConfigWrap.BUILD_TYPE) {
        BuildConfigWrap.BuildType.RELEASE -> if (betaConsent) UpdateChecker.Channel.BETA else UpdateChecker.Channel.PROD
        BuildConfigWrap.BuildType.BETA -> UpdateChecker.Channel.BETA
        BuildConfigWrap.BuildType.DEV -> UpdateChecker.Channel.BETA
    }
    val update = getLatest(currentChannel)

    if (update == null) {
        log(TAG) { "No update available: ($currentChannel)" }
        return null
    }

    if (!update.isNewer()) {
        log(TAG) { "Latest update isn't newer: $update" }
        return null
    }

    if (isDismissed(update)) {
        log(TAG) { "Update was previously dismissed: $update" }
        return null
    }

    return update
}


private val TAG = logTag("Updater", "Checker")
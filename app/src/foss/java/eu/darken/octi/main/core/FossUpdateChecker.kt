package eu.darken.octi.main.core

import dagger.Reusable
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.main.core.updater.UpdateChecker
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Reusable
class FossUpdateChecker @Inject constructor(
    private val checker: GithubReleaseCheck,
    private val webpageTool: WebpageTool,
    private val settings: FossUpdateSettings,
) : UpdateChecker {

    override suspend fun getLatest(channel: UpdateChecker.Channel): UpdateChecker.Update? {
        log(TAG) { "getLatest($channel) checking..." }

        val release: GithubApi.ReleaseInfo? = try {
            if (Duration.between(settings.lastReleaseCheck.value(), Instant.now()) < UPDATE_CHECK_INTERVAL) {
                log(TAG) { "Using cached release data" }
                when (channel) {
                    UpdateChecker.Channel.BETA -> settings.lastReleaseBeta.value()
                    UpdateChecker.Channel.PROD -> settings.lastReleaseProd.value()
                }
            } else {
                log(TAG) { "Fetching new release data" }
                when (channel) {
                    UpdateChecker.Channel.BETA -> checker.allReleases(OWNER, REPO).first()
                    UpdateChecker.Channel.PROD -> checker.latestRelease(OWNER, REPO)
                }.also {
                    log(TAG, INFO) { "getLatest($channel) new data is $it" }
                    settings.lastReleaseCheck.value(Instant.now())
                    when (channel) {
                        UpdateChecker.Channel.BETA -> settings.lastReleaseBeta.value(it)
                        UpdateChecker.Channel.PROD -> settings.lastReleaseProd.value(it)
                    }
                }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "getLatest($channel) failed: ${e.asLog()}" }
            null
        }

        log(TAG, INFO) { "getLatest($channel) is ${release?.tagName}" }

        val update = release?.let { rel ->
            Update(
                channel = channel,
                versionName = rel.tagName,
                changelogLink = rel.htmlUrl,
                downloadLink = rel.assets.singleOrNull { it.name.endsWith(".apk") }?.downloadUrl,
            )
        }

        return update
    }

    override suspend fun startUpdate(update: UpdateChecker.Update) {
        log(TAG, INFO) { "startUpdate($update)" }
        update as Update
        if (update.downloadLink != null) {
            webpageTool.open(update.downloadLink)
        } else {
            log(TAG, WARN) { "No download link available for $update" }
        }
    }

    override suspend fun viewUpdate(update: UpdateChecker.Update) {
        log(TAG, INFO) { "viewUpdate($update)" }
        update as Update
        webpageTool.open(update.changelogLink)
    }

    override suspend fun dismissUpdate(update: UpdateChecker.Update) {
        log(TAG, INFO) { "dismissUpdate($update)" }
        update as Update
        settings.dismiss(update)
    }

    override suspend fun isDismissed(update: UpdateChecker.Update): Boolean {
        update as Update
        return settings.isDismissed(update)
    }

    override fun isEnabledByDefault(): Boolean {
        val isEnabled = false
        log(TAG, INFO) { "Update check default isEnabled=$isEnabled" }
        return isEnabled
    }

    override suspend fun isCheckSupported(): Boolean {
        return true
    }

    data class Update(
        override val channel: UpdateChecker.Channel,
        override val versionName: String,
        val changelogLink: String,
        val downloadLink: String?,
    ) : UpdateChecker.Update

    companion object {
        private val UPDATE_CHECK_INTERVAL = Duration.ofHours(6)
        private const val OWNER = "d4rken-org"
        private const val REPO = "octi"
        private val TAG = logTag("Updater", "Checker", "FOSS")
    }
}
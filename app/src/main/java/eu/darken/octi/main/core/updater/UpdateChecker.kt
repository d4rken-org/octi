package eu.darken.octi.main.core.updater

interface UpdateChecker {

    suspend fun getLatest(channel: Channel): Update?

    suspend fun startUpdate(update: Update)

    suspend fun viewUpdate(update: Update)

    suspend fun dismissUpdate(update: Update)

    suspend fun isDismissed(update: Update): Boolean

    suspend fun isCheckSupported(): Boolean

    fun isEnabledByDefault(): Boolean

    interface Update {
        val channel: Channel
        val versionName: String
    }

    enum class Channel {
        BETA,
        PROD
    }

}
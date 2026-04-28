package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.ca.caString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.syncs.octiserver.R
import retrofit2.HttpException

class OctiServerHttpException(
    override val cause: HttpException,
) : Exception(), HasLocalizedError {

    val httpCode: Int get() = cause.code()

    val isDeviceUnknown: Boolean get() = httpCode == 401 || httpCode == 404 || httpCode == 410

    /** `X-Octi-Reason` from the response when the server distinguishes 507 sub-cases.
     *  Captured eagerly because the retrofit response body / headers are one-shot. */
    val octiReason: String? = cause.response()?.headers()?.get(HEADER_OCTI_REASON)

    /** `Retry-After` delta-seconds when the server emits a 429. Captured eagerly because
     *  the Retrofit response body/headers are one-shot. The Octi server only emits the
     *  delta-seconds form (per `HttpExtensions.kt`); HTTP-date form yields null. */
    val retryAfterSeconds: Long? = cause.response()
        ?.headers()
        ?.get(HEADER_RETRY_AFTER)
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it >= 0 }

    /** Captured eagerly because Retrofit's response body is one-shot. Exposed read-only so
     *  callers can pattern-match on server-emitted text (e.g. size-mismatch detection). */
    val errorBody: String = cause.response()?.errorBody()?.string() ?: cause.toString()
    private val errorMessage: String get() = errorBody

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = caString { cause.message() },
        description = when {
            isDeviceUnknown -> caString { it.getString(R.string.sync_octiserver_error_device_not_registered) }
            else -> caString { errorMessage }
        },
    )

    companion object {
        const val HEADER_OCTI_REASON = "X-Octi-Reason"
        const val HEADER_RETRY_AFTER = "Retry-After"
        const val REASON_SERVER_DISK_LOW = "server_disk_low"
        const val REASON_ACCOUNT_QUOTA_EXCEEDED = "account_quota_exceeded"
    }
}
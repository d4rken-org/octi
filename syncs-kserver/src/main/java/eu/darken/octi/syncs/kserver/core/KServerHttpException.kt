package eu.darken.octi.syncs.kserver.core

import eu.darken.octi.common.ca.caString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.syncs.kserver.R
import retrofit2.HttpException

class KServerHttpException(
    override val cause: HttpException,
) : Exception(), HasLocalizedError {

    val httpCode: Int get() = cause.code()

    val isDeviceUnknown: Boolean get() = httpCode == 401 || httpCode == 404 || httpCode == 410

    private var errorMessage: String = cause.response()?.errorBody()?.string() ?: cause.toString()

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = caString { cause.message() },
        description = when {
            isDeviceUnknown -> caString { it.getString(R.string.sync_kserver_error_device_not_registered) }
            else -> caString { errorMessage }
        },
    )
}
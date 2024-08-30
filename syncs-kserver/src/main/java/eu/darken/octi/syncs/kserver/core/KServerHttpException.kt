package eu.darken.octi.syncs.kserver.core

import android.content.Context
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import retrofit2.HttpException

class KServerHttpException(
    override val cause: HttpException,
) : Exception(), HasLocalizedError {

    private var errorMessage: String = cause.response()?.errorBody()?.string() ?: cause.toString()

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = cause.message(),
        description = errorMessage,
    )
}
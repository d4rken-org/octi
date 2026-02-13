package eu.darken.octi.syncs.kserver.core

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.HttpException
import retrofit2.Response

class KServerHttpExceptionTest {

    private fun createException(code: Int): KServerHttpException {
        val response = Response.error<Any>(code, "error".toResponseBody())
        return KServerHttpException(HttpException(response))
    }

    @Test
    fun `httpCode returns the HTTP status code`() {
        createException(404).httpCode shouldBe 404
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 404, 410])
    fun `isDeviceUnknown is true for auth and not-found codes`(code: Int) {
        createException(code).isDeviceUnknown shouldBe true
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 403, 500, 502, 503])
    fun `isDeviceUnknown is false for other error codes`(code: Int) {
        createException(code).isDeviceUnknown shouldBe false
    }

    @Test
    fun `cause is preserved`() {
        val httpException = HttpException(Response.error<Any>(404, "body".toResponseBody()))
        val exception = KServerHttpException(httpException)
        exception.cause shouldBe httpException
    }
}

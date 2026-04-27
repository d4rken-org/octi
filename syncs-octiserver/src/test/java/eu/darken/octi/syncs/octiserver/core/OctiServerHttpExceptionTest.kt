package eu.darken.octi.syncs.octiserver.core

import io.kotest.matchers.shouldBe
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.HttpException
import retrofit2.Response

class OctiServerHttpExceptionTest {

    private fun createException(
        code: Int,
        octiReason: String? = null,
        retryAfter: String? = null,
    ): OctiServerHttpException {
        val rawBuilder = okhttp3.Response.Builder()
            .request(Request.Builder().url("http://localhost/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("err")
            .body("error".toResponseBody("text/plain".toMediaType()))
        if (octiReason != null) rawBuilder.header(OctiServerHttpException.HEADER_OCTI_REASON, octiReason)
        if (retryAfter != null) rawBuilder.header(OctiServerHttpException.HEADER_RETRY_AFTER, retryAfter)
        val raw = rawBuilder.build()
        val response = Response.error<Any>("error".toResponseBody("text/plain".toMediaType()), raw)
        return OctiServerHttpException(HttpException(response))
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
        val exception = OctiServerHttpException(httpException)
        exception.cause shouldBe httpException
    }

    @Test
    fun `octiReason captures X-Octi-Reason header value`() {
        createException(507, OctiServerHttpException.REASON_SERVER_DISK_LOW)
            .octiReason shouldBe OctiServerHttpException.REASON_SERVER_DISK_LOW
        createException(507, OctiServerHttpException.REASON_ACCOUNT_QUOTA_EXCEEDED)
            .octiReason shouldBe OctiServerHttpException.REASON_ACCOUNT_QUOTA_EXCEEDED
    }

    @Test
    fun `octiReason is null when header is absent`() {
        createException(507).octiReason shouldBe null
    }

    @Test
    fun `retryAfterSeconds parses delta-seconds form`() {
        createException(429, retryAfter = "30").retryAfterSeconds shouldBe 30L
    }

    @Test
    fun `retryAfterSeconds is null when header is absent`() {
        createException(429).retryAfterSeconds shouldBe null
    }

    @Test
    fun `retryAfterSeconds is null for HTTP-date form`() {
        createException(429, retryAfter = "Wed, 21 Oct 2015 07:28:00 GMT").retryAfterSeconds shouldBe null
    }

    @Test
    fun `retryAfterSeconds is null for malformed value`() {
        createException(429, retryAfter = "notanumber").retryAfterSeconds shouldBe null
    }

    @Test
    fun `retryAfterSeconds is null for negative value`() {
        createException(429, retryAfter = "-5").retryAfterSeconds shouldBe null
    }

    @Test
    fun `retryAfterSeconds parses zero as zero`() {
        createException(429, retryAfter = "0").retryAfterSeconds shouldBe 0L
    }
}

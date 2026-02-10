package eu.darken.octi.common.http

import eu.darken.octi.common.BuildConfigWrap
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class UserAgentInterceptorTest : BaseTest() {

    @BeforeEach
    fun setup() {
        mockkObject(BuildConfigWrap)
        every { BuildConfigWrap.VERSION_NAME } returns "1.2.3-rc0"
        every { BuildConfigWrap.FLAVOR } returns BuildConfigWrap.Flavor.FOSS
    }

    private fun intercept(): String {
        val instance = UserAgentInterceptor()

        val requestSlot = slot<Request>()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns Request.Builder().url("https://example.com").build()
            every { proceed(capture(requestSlot)) } returns mockk<Response>()
        }

        instance.intercept(chain)
        return requestSlot.captured.header("User-Agent")!!
    }

    @Test
    fun `release build uses base format`() {
        every { BuildConfigWrap.BUILD_TYPE } returns BuildConfigWrap.BuildType.RELEASE
        every { BuildConfigWrap.GIT_SHA } returns "a1b2c3d"

        intercept() shouldBe "octi/1.2.3-rc0/FOSS"
    }

    @Test
    fun `beta build uses base format`() {
        every { BuildConfigWrap.BUILD_TYPE } returns BuildConfigWrap.BuildType.BETA
        every { BuildConfigWrap.GIT_SHA } returns "a1b2c3d"

        intercept() shouldBe "octi/1.2.3-rc0/FOSS"
    }

    @Test
    fun `dev build includes git sha`() {
        every { BuildConfigWrap.BUILD_TYPE } returns BuildConfigWrap.BuildType.DEV
        every { BuildConfigWrap.GIT_SHA } returns "a1b2c3d"

        intercept() shouldBe "octi/1.2.3-rc0/FOSS/dev-a1b2c3d"
    }

    @Test
    fun `dev build with empty git sha falls back to dev`() {
        every { BuildConfigWrap.BUILD_TYPE } returns BuildConfigWrap.BuildType.DEV
        every { BuildConfigWrap.GIT_SHA } returns ""

        intercept() shouldBe "octi/1.2.3-rc0/FOSS/dev"
    }

    @Test
    fun `dev build with blank git sha falls back to dev`() {
        every { BuildConfigWrap.BUILD_TYPE } returns BuildConfigWrap.BuildType.DEV
        every { BuildConfigWrap.GIT_SHA } returns "   "

        intercept() shouldBe "octi/1.2.3-rc0/FOSS/dev"
    }
}

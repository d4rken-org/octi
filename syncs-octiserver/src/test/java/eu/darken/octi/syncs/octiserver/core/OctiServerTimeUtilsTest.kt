package eu.darken.octi.syncs.octiserver.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import testhelpers.BaseTest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

class OctiServerTimeUtilsTest : BaseTest() {

    @ParameterizedTest
    @MethodSource("rfc1123Cases")
    fun `parseRfc1123ToInstant matches java time RFC_1123 behavior`(input: String) {
        input.parseRfc1123ToInstant() shouldBe referenceParse(input)
    }

    companion object {
        private fun referenceParse(input: String): Instant? = runCatching {
            val ji = ZonedDateTime.parse(input, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            Instant.fromEpochSeconds(ji.epochSecond, ji.nano.toLong())
        }.getOrNull()

        @JvmStatic
        fun rfc1123Cases(): List<String> = listOf(
            // Canonical accepted forms — server-emitted shapes
            "Tue, 15 Nov 1994 08:12:31 GMT",
            "Sun, 06 Nov 1994 08:49:37 GMT",
            "Wed, 31 Dec 2025 23:59:59 GMT",
            "Thu, 01 Jan 1970 00:00:00 GMT",       // epoch
            // Numeric offsets
            "Tue, 15 Nov 1994 03:12:31 -0500",     // same instant as canonical
            "Tue, 15 Nov 1994 08:12:31 +0000",     // some servers emit +0000 not GMT
            "Tue, 15 Nov 1994 09:12:31 +0100",
            // Likely-divergent edge cases (probe parser leniency)
            "Sun, 6 Nov 1994 08:49:37 GMT",        // single-digit day
            "Tue, 15 Nov 1994 08:12:31 UTC",       // UTC token instead of GMT
            "tue, 15 nov 1994 08:12:31 gmt",       // lowercase
            "Tue, 15 Nov 1994 08:12 GMT",          // missing seconds
            " Tue, 15 Nov 1994 08:12:31 GMT ",     // surrounding whitespace
            // Malformed
            "not a date",
            "",
            "Tue, 32 Nov 1994 08:12:31 GMT",       // invalid day
        )
    }
}

package eu.darken.octi.common.serialization.serializer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import testhelpers.BaseTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/**
 * Wire-format compatibility between kotlin.time.Duration and java.time.Duration.
 *
 * After migration, the codebase uses kotlin.time.Duration for serialization via toIsoString()/parseIsoString().
 * Both forward (kotlin parses java's output) and downgrade (java parses kotlin's output) compatibility
 * is required so old/new app versions can read each other's persisted DataStore payloads.
 */
class DurationSerializerCompatTest : BaseTest() {

    @Nested
    inner class `forward compat - kotlin parses java's output` {

        @Test
        fun `simple durations`() {
            // Each entry: a kotlin Duration value used in the codebase, and the java toString() form
            val cases = listOf(
                2.minutes to "PT2M",        // SyncSettings.clockSkewThreshold default
                5.minutes to "PT5M",        // SyncSettings.FIRST_SYNC_GRACE_PERIOD
                15.minutes to "PT15M",
                60.minutes to "PT1H",
                30.seconds to "PT30S",
                500.milliseconds to "PT0.5S",
                Duration.ZERO to "PT0S",
                1.days to "PT24H",          // java.time.Duration.ofDays(1).toString() emits PT24H
                2.hours to "PT2H",
            )
            for ((kotlinDuration, javaString) in cases) {
                val parsed = Duration.parseIsoString(javaString)
                parsed shouldBe kotlinDuration
            }
        }

        @Test
        fun `negative durations`() {
            // java.time.Duration.ofMinutes(-5).toString() emits "PT-5M"
            // kotlin.time.Duration.parseIsoString must accept this form
            val parsed = Duration.parseIsoString("PT-5M")
            parsed shouldBe (-5).minutes
        }

        @Test
        fun `period-day form is also accepted`() {
            // java.time.Duration.parse() accepts P1D even though toString() emits PT24H.
            // kotlin must accept the same input.
            Duration.parseIsoString("P1D") shouldBe 1.days
        }
    }

    @Nested
    inner class `downgrade compat - java parses kotlin's output` {

        @Test
        fun `simple durations`() {
            val values = listOf(
                2.minutes,
                5.minutes,
                15.minutes,
                60.minutes,
                1.seconds,
                30.seconds,
                500.milliseconds,
                1.days,
                2.hours,
                Duration.ZERO,
                90.minutes,
            )
            for (kotlinDuration in values) {
                val serialized = kotlinDuration.toIsoString()
                val parsedByJava = java.time.Duration.parse(serialized)
                parsedByJava shouldBe kotlinDuration.toJavaDuration()
            }
        }

        @Test
        fun `negative duration`() {
            val serialized = (-5).minutes.toIsoString()
            val parsedByJava = java.time.Duration.parse(serialized)
            parsedByJava shouldBe (-5).minutes.toJavaDuration()
        }
    }

    @Nested
    inner class `round-trip identity` {

        @Test
        fun `via java form`() {
            // kotlin -> string -> java -> string -> kotlin must yield identity
            val values = listOf(2.minutes, 5.minutes, 90.minutes, 30.seconds, 1.days, (-5).minutes, Duration.ZERO)
            for (kotlinDuration in values) {
                val viaKotlin = kotlinDuration.toIsoString()
                val viaJava = java.time.Duration.parse(viaKotlin).toString()
                val backToKotlin = Duration.parseIsoString(viaJava)
                backToKotlin shouldBe kotlinDuration
            }
        }

        @Test
        fun `legacy java strings round-trip via kotlin`() {
            // Strings already on disk (written by java.time before migration) must roundtrip
            val javaStrings = listOf("PT2M", "PT5M", "PT0S", "PT-30S", "PT24H", "P1D")
            for (javaString in javaStrings) {
                val viaKotlin = Duration.parseIsoString(javaString)
                val backToJava = viaKotlin.toIsoString()
                java.time.Duration.parse(backToJava) shouldBe java.time.Duration.parse(javaString)
            }
        }
    }
}

package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.datetime.format.DateTimeComponents
import kotlin.time.Instant

private val TAG = logTag("OctiServer", "TimeUtils")

// kotlinx-datetime's RFC_1123 format is case-sensitive for day-of-week, month, and "GMT",
// but the JDK's RFC 1123 formatter is case-insensitive (parseCaseInsensitive).
// Normalize these tokens so we match the JDK's leniency for incoming server headers.
private val RFC1123_DAY_OF_WEEK = Regex("""\b(mon|tue|wed|thu|fri|sat|sun)\b""", RegexOption.IGNORE_CASE)
private val RFC1123_MONTH = Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\b""", RegexOption.IGNORE_CASE)
private val RFC1123_GMT = Regex("""\bgmt\b""", RegexOption.IGNORE_CASE)

private fun String.titleCaseRfc1123Token(): String = lowercase().replaceFirstChar { it.uppercase() }

private fun String.normalizeRfc1123Case(): String = this
    .replace(RFC1123_DAY_OF_WEEK) { it.value.titleCaseRfc1123Token() }
    .replace(RFC1123_MONTH) { it.value.titleCaseRfc1123Token() }
    .replace(RFC1123_GMT) { "GMT" }

internal fun String.parseRfc1123ToInstant(): Instant? = runCatching {
    DateTimeComponents.Formats.RFC_1123.parse(normalizeRfc1123Case()).toInstantUsingOffset()
}.onFailure { e ->
    log(TAG, WARN) { "Failed to parse RFC 1123 timestamp '$this': ${e.asLog()}" }
}.getOrNull()

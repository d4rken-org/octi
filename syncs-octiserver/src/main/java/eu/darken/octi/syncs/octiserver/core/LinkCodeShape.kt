package eu.darken.octi.syncs.octiserver.core

/** Base64 alphabet accepted by okio's decoder, both the standard and the URL-safe variant. */
private val BASE64_ALPHABET = Regex("^[A-Za-z0-9+/\\-_=]*$")

/** Base64 of the gzip magic `1f 8b 08`, so every complete link code starts with it. */
private const val GZIP_BASE64_PREFIX = "H4sI"

/**
 * A loggable description of a link code that contains none of its characters.
 *
 * A link code carries the account's payload encryption keyset and the server link credential, so it
 * must never reach a debug recording. These three facts are what actually diagnose a failed link and
 * none of them reveal any of the code:
 *  - `len`: a complete code is ~496 chars, so a short one was truncated.
 *  - `gzipPrefix`: false means the start of the code was lost, which whitespace-tolerant base64
 *    decoding cannot detect on its own. Deliberately a boolean rather than the leading characters:
 *    when it is false those characters are arbitrary payload bytes, not the known constant.
 *  - `alphabet`: false means a stray character got in (autocorrect, a smart quote, a space inside
 *    the code), true narrows it to a length or ordering problem.
 */
fun String.linkCodeShape(): String =
    "len=$length, gzipPrefix=${startsWith(GZIP_BASE64_PREFIX)}, alphabet=${BASE64_ALPHABET.matches(this)}"

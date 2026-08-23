package eu.darken.octi.common

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt

fun colorString(@ColorInt color: Int, string: String): SpannableString {
    val colored = SpannableString(string)
    colored.setSpan(ForegroundColorSpan(color), 0, string.length, 0)
    return colored
}

private val IDENTIFIER_SEPARATORS = Regex("[_\\-\\s]+")

/**
 * Turns a machine identifier into a human readable label.
 *
 * The input is trimmed and split on `_`, `-` and whitespace runs, empty tokens are dropped, the
 * first character of each remaining token is uppercased and the token tail is kept **exactly as-is**,
 * then the tokens are joined with single spaces. If no token survives, the trimmed input is returned.
 *
 * Token tails are preserved on purpose, lowercasing them would turn "FreeBSD" into "Freebsd".
 *
 * `home_assistant` -> "Home Assistant", `desktop-linux` -> "Desktop Linux", `FreeBSD` -> "FreeBSD",
 * `HOME_ASSISTANT` -> "HOME ASSISTANT", `"  home__assistant  "` -> "Home Assistant".
 */
fun humanizeIdentifier(raw: String): String {
    val trimmed = raw.trim()
    val tokens = trimmed.split(IDENTIFIER_SEPARATORS).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return trimmed
    return tokens.joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
}

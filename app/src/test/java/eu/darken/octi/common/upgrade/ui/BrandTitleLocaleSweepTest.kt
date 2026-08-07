package eu.darken.octi.common.upgrade.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.R
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.octi.common.R as CommonR

/**
 * Resolves the title template through the real resource merger for every locale the app ships,
 * because a damaged template is a crash, not a cosmetic bug: a stray `%`, a `%3$s` or a `%1$d` in
 * a pulled translation throws inside `getString` *before* `spliceTitleTemplate`'s fallback can run.
 *
 * The per-locale span assertion goes through `spliceTitleTemplate` directly rather than the
 * composable, so all locales fit in one test — `composeRule.setContent` may only be called once.
 * Ordering behaviour itself is covered exhaustively by [TitleTemplateSpliceTest]; what this adds is
 * that the *shipped* templates are the ones being spliced.
 */
class BrandTitleLocaleSweepTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // Every locale with a shipped strings.xml, plus "en" for the base resources. A new locale must
    // be added here deliberately — that is the point, not an oversight.
    private val locales = listOf(
        "en",
        "af-rZA", "ar", "bg", "ca", "cs", "da", "de", "el", "es", "et", "fi", "fr", "ga", "hr",
        "hu", "id", "is", "it", "iw", "ja", "ka", "kk", "ko", "lt", "lv", "ms", "nb", "nl", "pl",
        "pt", "pt-rBR", "ro", "ru", "sk", "sl", "sr", "sv", "th", "tr", "uk", "vi", "zh-rCN",
        "zh-rTW",
    )

    @Test
    fun `every shipped locale resolves a well-formed title template`() {
        locales.forEach { locale ->
            RuntimeEnvironment.setQualifiers("+$locale")

            val template = context.getString(R.string.app_name_upgraded_template)

            withClue(locale) {
                // Exactly one of each slot, and nothing else that getString would try to expand.
                Regex(Regex.escape("%1\$s")).findAll(template).count() shouldBe 1
                Regex(Regex.escape("%2\$s")).findAll(template).count() shouldBe 1
                template.replace("%1\$s", "").replace("%2\$s", "") shouldNotContain "%"
            }
        }
    }

    /**
     * Guards the two tests above against passing vacuously. If `setQualifiers` silently stopped
     * switching locales, every iteration would resolve the base resources and the sweep would still
     * be green while testing exactly one locale.
     */
    @Test
    fun `the sweep actually switches locales`() {
        RuntimeEnvironment.setQualifiers("+ar")
        context.getString(CommonR.string.app_name) shouldBe "أوكتي"

        RuntimeEnvironment.setQualifiers("+en")
        context.getString(CommonR.string.app_name) shouldBe "Octi"
    }

    @Test
    fun `every shipped locale composes a title whose highlight covers the qualifier`() {
        locales.forEach { locale ->
            RuntimeEnvironment.setQualifiers("+$locale")

            val name = context.getString(CommonR.string.app_name)
            val qualifier = context.getString(R.string.app_name_upgrade_postfix)
            val composed = context.getString(R.string.app_name_upgraded_template, name, qualifier)

            val result = spliceTitleTemplate(
                formatted = context.getString(
                    R.string.app_name_upgraded_template,
                    BRAND_TITLE_MARKER,
                    BRAND_QUALIFIER_MARKER,
                ),
                name = AnnotatedString(name),
                qualifier = buildAnnotatedString {
                    pushStyle(SpanStyle(color = Color.Red))
                    append(qualifier)
                    pop()
                },
            )

            withClue(locale) {
                name.isNotBlank() shouldBe true
                qualifier.isNotBlank() shouldBe true
                result.text shouldBe composed
                result.text shouldNotContain BRAND_TITLE_MARKER
                result.text shouldNotContain BRAND_QUALIFIER_MARKER
                result.spanStyles.size shouldBe 1
                val span = result.spanStyles.single()
                result.text.substring(span.start, span.end) shouldBe qualifier
            }
        }
    }

    // Kotest's own withClue pulls in the assertions-core dependency; this keeps the failure message
    // locale-tagged without adding one, since a bare failure in a 44-iteration loop is unreadable.
    private inline fun withClue(clue: String, block: () -> Unit) = try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("[locale=$clue] ${e.message}", e)
    }
}

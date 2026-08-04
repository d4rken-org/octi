package eu.darken.octi.common.upgrade.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The brand is spliced into the already-formatted translation, so the styled postfix has to land on
 * the right offsets no matter where the pattern put the placeholder.
 */
class BrandTitleSpliceTest : BaseTest() {

    private val brandColor = Color.Red

    // "Octi Pro" with the postfix (5..8) colored, like upgradeScreenTitle(upgraded = true).
    private val brand: AnnotatedString = buildAnnotatedString {
        append("Octi ")
        pushStyle(SpanStyle(color = brandColor))
        append("Pro")
        pop()
    }

    @Test
    fun `marker in the middle shifts the styled postfix by the prefix`() {
        val result = spliceBrandTitle("Get $BRAND_TITLE_MARKER", brand)

        result.text shouldBe "Get Octi Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe brandColor
        result.spanStyles.single().start shouldBe 9
        result.spanStyles.single().end shouldBe 12
        result.text.substring(9, 12) shouldBe "Pro"
    }

    @Test
    fun `marker at the start keeps the postfix offsets inside the brand`() {
        val result = spliceBrandTitle("$BRAND_TITLE_MARKER holen", brand)

        result.text shouldBe "Octi Pro holen"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 5
        result.spanStyles.single().end shouldBe 8
        result.text.substring(5, 8) shouldBe "Pro"
    }

    @Test
    fun `a duplicated marker renders the brand twice`() {
        val result = spliceBrandTitle("$BRAND_TITLE_MARKER und $BRAND_TITLE_MARKER", brand)

        result.text shouldBe "Octi Pro und Octi Pro"
        result.spanStyles.size shouldBe 2
        result.spanStyles[0].start shouldBe 5
        result.spanStyles[0].end shouldBe 8
        result.spanStyles[1].start shouldBe 18
        result.spanStyles[1].end shouldBe 21
        result.text.substring(18, 21) shouldBe "Pro"
    }

    @Test
    fun `a translation that lost the placeholder still shows the brand`() {
        val result = spliceBrandTitle("Get Pro", brand)

        result.text shouldBe "Get Pro Octi Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe brandColor
        result.spanStyles.single().start shouldBe 13
        result.spanStyles.single().end shouldBe 16
    }
}

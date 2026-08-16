package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError
import eu.darken.octi.syncs.octiserver.R

/**
 * A link code that could not be turned back into [LinkingData].
 *
 * The three decode stages fail with wildly different exceptions for what is, to the user, always the
 * same situation: the code that arrived is not the code that was generated. Without this, a partial
 * paste surfaces okio's internal gzip wording ("ID1ID2: actual 0x... != expected 0x...") in the
 * error dialog, which tells the user nothing they can act on.
 *
 * Note that base64 alone catches almost nothing: okio's decoder does no length or alignment
 * validation and skips whitespace, so any string built purely from alphabet characters decodes
 * "successfully" no matter how much of it is missing. Truncation therefore surfaces at [Stage.GZIP],
 * not [Stage.BASE64].
 *
 * The triggering exception is deliberately **not** chained. [eu.darken.octi.common.uix.ViewModel4]
 * logs the whole cause chain with `asLog()`, and a decoder message can carry payload bytes:
 * kotlinx.serialization embeds a "JSON input:" excerpt around the failure offset, and the plaintext
 * being parsed at [Stage.JSON] contains `encryptionKeySet.key` in full. [stage] plus the exception
 * type is the entire diagnostic value those messages had, without the leak.
 */
class InvalidLinkCodeException private constructor(
    val stage: Stage,
    private val causeType: String,
) : IllegalArgumentException("Link code could not be decoded at $stage ($causeType)"), HasLocalizedError {

    internal constructor(stage: Stage, cause: Throwable) : this(stage, cause::class.simpleName ?: "Unknown")

    enum class Stage { BASE64, GZIP, JSON }

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.sync_octiserver_link_code_invalid_label.toCaString(),
        description = R.string.sync_octiserver_link_code_invalid_description.toCaString(),
    )
}

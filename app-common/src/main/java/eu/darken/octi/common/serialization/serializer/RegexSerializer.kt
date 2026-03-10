package eu.darken.octi.common.serialization.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RegexSerializer : KSerializer<Regex> {
    override val descriptor: SerialDescriptor = RegexSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Regex) {
        val surrogate = RegexSurrogate(
            pattern = value.pattern,
            options = value.options.map { it.toSurrogateOption() }.toSet(),
        )
        encoder.encodeSerializableValue(RegexSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Regex {
        val surrogate = decoder.decodeSerializableValue(RegexSurrogate.serializer())
        return Regex(
            pattern = surrogate.pattern,
            options = surrogate.options.map { it.toRegexOption() }.toSet(),
        )
    }

    private fun RegexOption.toSurrogateOption() = when (this) {
        RegexOption.IGNORE_CASE -> RegexSurrogate.Option.IGNORE_CASE
        RegexOption.MULTILINE -> RegexSurrogate.Option.MULTILINE
        RegexOption.LITERAL -> RegexSurrogate.Option.LITERAL
        RegexOption.UNIX_LINES -> RegexSurrogate.Option.UNIX_LINES
        RegexOption.COMMENTS -> RegexSurrogate.Option.COMMENTS
        RegexOption.DOT_MATCHES_ALL -> RegexSurrogate.Option.DOT_MATCHES_ALL
        RegexOption.CANON_EQ -> RegexSurrogate.Option.CANON_EQ
    }

    private fun RegexSurrogate.Option.toRegexOption() = when (this) {
        RegexSurrogate.Option.IGNORE_CASE -> RegexOption.IGNORE_CASE
        RegexSurrogate.Option.MULTILINE -> RegexOption.MULTILINE
        RegexSurrogate.Option.LITERAL -> RegexOption.LITERAL
        RegexSurrogate.Option.UNIX_LINES -> RegexOption.UNIX_LINES
        RegexSurrogate.Option.COMMENTS -> RegexOption.COMMENTS
        RegexSurrogate.Option.DOT_MATCHES_ALL -> RegexOption.DOT_MATCHES_ALL
        RegexSurrogate.Option.CANON_EQ -> RegexOption.CANON_EQ
    }

    @Serializable
    data class RegexSurrogate(
        @SerialName("pattern") val pattern: String,
        @SerialName("options") val options: Set<Option>,
    ) {
        @Serializable
        enum class Option {
            @SerialName("IGNORE_CASE") IGNORE_CASE,
            @SerialName("MULTILINE") MULTILINE,
            @SerialName("LITERAL") LITERAL,
            @SerialName("UNIX_LINES") UNIX_LINES,
            @SerialName("COMMENTS") COMMENTS,
            @SerialName("DOT_MATCHES_ALL") DOT_MATCHES_ALL,
            @SerialName("CANON_EQ") CANON_EQ,
        }
    }
}

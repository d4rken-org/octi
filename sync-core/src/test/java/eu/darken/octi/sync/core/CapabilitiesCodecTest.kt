package eu.darken.octi.sync.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CapabilitiesCodecTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }
    private val codec = CapabilitiesCodec(json)

    @Test
    fun `encodeToHeader produces canonical sorted JSON array`() {
        val caps = setOf("encryption:AES256_SIV", "encryption:_reported", "encryption:AES256_GCM_SIV")
        val header = codec.encodeToHeader(caps)
        header shouldBe """["encryption:AES256_GCM_SIV","encryption:AES256_SIV","encryption:_reported"]"""
    }

    @Test
    fun `encodeToHeader rejects oversized set`() {
        val caps = (0 until CapabilitiesCodec.MAX_TAGS + 1).map { "ns:value$it" }.toSet()
        shouldThrow<IllegalArgumentException> { codec.encodeToHeader(caps) }
    }

    @Test
    fun `encodeToHeader rejects invalid tag shape`() {
        shouldThrow<IllegalArgumentException> { codec.encodeToHeader(setOf("not a tag")) }
        shouldThrow<IllegalArgumentException> { codec.encodeToHeader(setOf("Encryption:GCM_SIV")) } // uppercase namespace
        shouldThrow<IllegalArgumentException> { codec.encodeToHeader(setOf("encryption:")) }
        shouldThrow<IllegalArgumentException> { codec.encodeToHeader(setOf(":value")) }
    }

    @Test
    fun `decodeFromString roundtrips an encoded set`() {
        val caps = setOf("encryption:AES256_GCM_SIV", "encryption:_reported", "encryption:AES256_SIV")
        val header = codec.encodeToHeader(caps)
        codec.decodeFromString(header)!! shouldContainExactlyInAnyOrder caps
    }

    @Test
    fun `decode returns null for null input`() {
        codec.decode(null).shouldBeNull()
        codec.decode(JsonNull).shouldBeNull()
    }

    @Test
    fun `decode returns null for non-array element`() {
        codec.decode(json.parseToJsonElement("\"a string\"")).shouldBeNull()
        codec.decode(json.parseToJsonElement("{\"a\": 1}")).shouldBeNull()
        codec.decode(json.parseToJsonElement("42")).shouldBeNull()
    }

    @Test
    fun `decode returns null for array with non-string elements`() {
        codec.decode(json.parseToJsonElement("""["encryption:AES256_GCM_SIV", 42]""")).shouldBeNull()
        codec.decode(json.parseToJsonElement("""["encryption:AES256_GCM_SIV", null]""")).shouldBeNull()
    }

    @Test
    fun `decode rejects oversized array before allocating`() {
        val items = (0 until CapabilitiesCodec.MAX_TAGS + 1).joinToString(",") { "\"ns:value$it\"" }
        codec.decode(json.parseToJsonElement("[$items]")).shouldBeNull()
    }

    @Test
    fun `decode rejects tag exceeding MAX_TAG_LENGTH`() {
        val tooLong = "encryption:" + "a".repeat(CapabilitiesCodec.MAX_TAG_LENGTH)
        codec.decode(json.parseToJsonElement("""["$tooLong"]""")).shouldBeNull()
    }

    @Test
    fun `decode rejects tag with bad shape`() {
        codec.decode(json.parseToJsonElement("""["bad tag"]""")).shouldBeNull()
        codec.decode(json.parseToJsonElement("""[":value"]""")).shouldBeNull()
    }

    @Test
    fun `decode returns empty set for empty array`() {
        codec.decode(json.parseToJsonElement("[]")) shouldContainExactly emptySet()
    }

    @Test
    fun `decodeFromString returns null for blank or empty input`() {
        codec.decodeFromString(null).shouldBeNull()
        codec.decodeFromString("").shouldBeNull()
        codec.decodeFromString("   ").shouldBeNull()
    }

    @Test
    fun `decodeFromString returns null for header exceeding MAX_HEADER_LENGTH`() {
        codec.decodeFromString("[" + "x".repeat(CapabilitiesCodec.MAX_HEADER_LENGTH + 1) + "]").shouldBeNull()
    }

    @Test
    fun `decodeFromString returns null for malformed JSON`() {
        codec.decodeFromString("not json").shouldBeNull()
        codec.decodeFromString("[unclosed").shouldBeNull()
    }

    @Test
    fun `encode of same set is deterministic across calls`() {
        val caps = setOf("encryption:AES256_GCM_SIV", "encryption:_reported", "encryption:AES256_SIV")
        codec.encodeToHeader(caps) shouldBe codec.encodeToHeader(caps)
    }
}

package eu.darken.octi.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class DataStoreKotlinxExtensionsTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private fun createDataStore(scope: TestScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    @Serializable
    data class TestData(
        val string: String = "",
        val boolean: Boolean = true,
        val float: Float = 1.0f,
        val int: Int = 1,
        val long: Long = 1L,
    )

    @Test
    fun `reading and writing using manual reader and writer`() = runTest {
        val testStore = createDataStore(this)

        val testData1 = TestData(string = "teststring")
        val testData2 = TestData(string = "update")

        testStore.createValue<TestData?>(
            key = stringPreferencesKey("testKey"),
            reader = kotlinxReader(json, testData1),
            writer = kotlinxWriter(json),
        ).apply {

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            flow.first() shouldBe testData2
            testStore.data.first()[stringPreferencesKey(keyName)]!!.toComparableJson() shouldBe """
                {
                    "string":"update",
                    "boolean":true,
                    "float":1.0,
                    "int":1,
                    "long":1
                }
            """.toComparableJson()

            update {
                it shouldBe testData2
                null
            }

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing sets`() = runTest {
        val testStore = createDataStore(this)

        val testData = TestData(string = "teststring")

        testStore.createSetValue<TestData>(
            key = "testKey",
            defaultValue = emptySet(),
            json = json,
        ).apply {
            flow.first() shouldBe emptySet()
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                setOf<TestData>() + testData + testData
            }
            testStore.data.first()[stringPreferencesKey(keyName)]!!.toComparableJson() shouldBe """
                [
                    {
                        "string":"teststring",
                        "boolean":true,
                        "float":1.0,
                        "int":1,
                        "long":1
                    }
                ]
            """.toComparableJson()

            flow.first() shouldBe setOf(testData)
            flow.first().contains(testData) shouldBe true
        }
    }

    @Test
    fun `reading and writing lists`() = runTest {
        val testStore = createDataStore(this)

        val testData = TestData(string = "teststring")

        testStore.createListValue<TestData>(
            key = "testKey",
            defaultValue = emptyList(),
            json = json,
        ).apply {
            flow.first() shouldBe emptyList()
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                listOf<TestData>() + testData + testData
            }
            testStore.data.first()[stringPreferencesKey(keyName)]!!.toComparableJson() shouldBe """
                [
                    {
                        "string":"teststring",
                        "boolean":true,
                        "float":1.0,
                        "int":1,
                        "long":1
                    }, {
                        "string":"teststring",
                        "boolean":true,
                        "float":1.0,
                        "int":1,
                        "long":1
                    }
                ]
            """.toComparableJson()

            flow.first() shouldBe listOf(testData, testData)
            flow.first().contains(testData) shouldBe true
        }
    }

    @Test
    fun `reading and writing using autocreated reader and writer`() = runTest {
        val testStore = createDataStore(this)

        val testData1 = TestData(string = "teststring")
        val testData2 = TestData(string = "update")

        testStore.createValue<TestData?>(
            key = "testKey",
            defaultValue = testData1,
            json = json,
        ).apply {

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            flow.first() shouldBe testData2
            testStore.data.first()[stringPreferencesKey(keyName)]!!.toComparableJson() shouldBe """
                {
                    "string":"update",
                    "boolean":true,
                    "float":1.0,
                    "int":1,
                    "long":1
                }
            """.toComparableJson()

            update {
                it shouldBe testData2
                null
            }

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null
        }
    }

    @Serializable
    enum class Anum {
        @SerialName("a") A,
        @SerialName("b") B,
    }

    @Test
    fun `enum serialization`() = runTest {
        val testStore = createDataStore(this)

        val monitorMode = testStore.createValue(
            "test.enum",
            Anum.A,
            json,
        )

        monitorMode.flow.first() shouldBe Anum.A
        monitorMode.update { Anum.B }
        monitorMode.flow.first() shouldBe Anum.B
    }

    @Test
    fun `malformed JSON throws without fallback flag`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("testKey")

        testStore.edit { it[key] = "not valid json{{{" }

        val dsv = testStore.createValue<TestData?>(
            key = "testKey",
            defaultValue = TestData(),
            json = json,
            onErrorFallbackToDefault = false,
        )

        shouldThrow<Exception> { dsv.flow.first() }
    }

    @Test
    fun `malformed JSON falls back to default with fallback flag`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("testKey")
        val defaultValue = TestData(string = "fallback")

        testStore.edit { it[key] = "not valid json{{{" }

        val dsv = testStore.createValue<TestData?>(
            key = "testKey",
            defaultValue = defaultValue,
            json = json,
            onErrorFallbackToDefault = true,
        )

        dsv.flow.first() shouldBe defaultValue
    }

    @Test
    fun `schema forward compatibility - extra unknown fields are ignored`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("testKey")

        testStore.edit {
            it[key] = """{"string":"hello","boolean":true,"float":1.0,"int":1,"long":1,"unknownField":"surprise"}"""
        }

        val dsv = testStore.createValue<TestData?>(
            key = "testKey",
            defaultValue = TestData(),
            json = json,
        )

        dsv.flow.first() shouldBe TestData(string = "hello")
    }

    @Test
    fun `schema backward compatibility - missing fields use data class defaults`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("testKey")

        testStore.edit { it[key] = """{"string":"partial"}""" }

        val dsv = testStore.createValue<TestData?>(
            key = "testKey",
            defaultValue = TestData(),
            json = json,
        )

        val result = dsv.flow.first()!!
        result.string shouldBe "partial"
        result.boolean shouldBe true
        result.float shouldBe 1.0f
        result.int shouldBe 1
        result.long shouldBe 1L
    }

    @Test
    fun `enum with unknown value throws without fallback`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("test.enum")

        testStore.edit { it[key] = "\"unknown_value\"" }

        val dsv = testStore.createValue(
            "test.enum",
            Anum.A,
            json,
            onErrorFallbackToDefault = false,
        )

        shouldThrow<SerializationException> { dsv.flow.first() }
    }

    @Test
    fun `enum with unknown value falls back to default with fallback flag`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("test.enum")

        testStore.edit { it[key] = "\"unknown_value\"" }

        val dsv = testStore.createValue(
            "test.enum",
            Anum.A,
            json,
            onErrorFallbackToDefault = true,
        )

        dsv.flow.first() shouldBe Anum.A
    }

    @Test
    fun `large collection round-trips correctly`() = runTest {
        val testStore = createDataStore(this)

        val largeSet = (1..500).map { TestData(string = "item-$it") }.toSet()

        val dsv = testStore.createSetValue<TestData>(
            key = "largeSet",
            defaultValue = emptySet(),
            json = json,
        )

        dsv.update { largeSet }
        dsv.flow.first().size shouldBe 500
    }
}

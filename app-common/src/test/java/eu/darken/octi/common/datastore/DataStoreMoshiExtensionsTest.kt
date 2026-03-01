package eu.darken.octi.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class DataStoreMoshiExtensionsTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private fun createDataStore(scope: TestScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    @JsonClass(generateAdapter = true)
    data class TestGson(
        val string: String = "",
        val boolean: Boolean = true,
        val float: Float = 1.0f,
        val int: Int = 1,
        val long: Long = 1L,
    )

    @Test
    fun `reading and writing using manual reader and writer`() = runTest {
        val testStore = createDataStore(this)

        val testData1 = TestGson(string = "teststring")
        val testData2 = TestGson(string = "update")
        val moshi = Moshi.Builder().build()

        testStore.createValue<TestGson?>(
            key = stringPreferencesKey("testKey"),
            reader = moshiReader(moshi, testData1),
            writer = moshiWriter(moshi)
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

        val testData = TestGson(string = "teststring")

        testStore.createSetValue<TestGson>(
            key = "testKey",
            defaultValue = emptySet(),
            moshi = Moshi.Builder().build()
        ).apply {
            flow.first() shouldBe emptySet()
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                setOf<TestGson>() + testData + testData
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

        val testData = TestGson(string = "teststring")

        testStore.createListValue<TestGson>(
            key = "testKey",
            defaultValue = emptyList(),
            moshi = Moshi.Builder().build()
        ).apply {
            flow.first() shouldBe emptyList()
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                listOf<TestGson>() + testData + testData
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

        val testData1 = TestGson(string = "teststring")
        val testData2 = TestGson(string = "update")
        val moshi = Moshi.Builder().build()

        testStore.createValue<TestGson?>(
            key = "testKey",
            defaultValue = testData1,
            moshi = moshi
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

    enum class Anum {
        @Json(name = "a") A,
        @Json(name = "b") B,
    }

    @Test
    fun `enum serialization`() = runTest {
        val testStore = createDataStore(this)

        val moshi = Moshi.Builder().build()
        val monitorMode = testStore.createValue(
            "test.enum",
            Anum.A,
            moshi
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

        val dsv = testStore.createValue<TestGson?>(
            key = "testKey",
            defaultValue = TestGson(),
            moshi = Moshi.Builder().build(),
            onErrorFallbackToDefault = false,
        )

        shouldThrow<Exception> { dsv.flow.first() }
    }

    @Test
    fun `malformed JSON falls back to default with fallback flag`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("testKey")
        val defaultValue = TestGson(string = "fallback")

        testStore.edit { it[key] = "not valid json{{{" }

        val dsv = testStore.createValue<TestGson?>(
            key = "testKey",
            defaultValue = defaultValue,
            moshi = Moshi.Builder().build(),
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

        val dsv = testStore.createValue<TestGson?>(
            key = "testKey",
            defaultValue = TestGson(),
            moshi = Moshi.Builder().build(),
        )

        dsv.flow.first() shouldBe TestGson(string = "hello")
    }

    @Test
    fun `schema backward compatibility - missing fields use data class defaults`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("testKey")

        testStore.edit { it[key] = """{"string":"partial"}""" }

        val dsv = testStore.createValue<TestGson?>(
            key = "testKey",
            defaultValue = TestGson(),
            moshi = Moshi.Builder().build(),
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
        val moshi = Moshi.Builder().build()

        testStore.edit { it[key] = "\"unknown_value\"" }

        val dsv = testStore.createValue(
            "test.enum",
            Anum.A,
            moshi,
            onErrorFallbackToDefault = false,
        )

        shouldThrow<JsonDataException> { dsv.flow.first() }
    }

    @Test
    fun `enum with unknown value falls back to default with fallback flag`() = runTest {
        val testStore = createDataStore(this)
        val key = stringPreferencesKey("test.enum")
        val moshi = Moshi.Builder().build()

        testStore.edit { it[key] = "\"unknown_value\"" }

        val dsv = testStore.createValue(
            "test.enum",
            Anum.A,
            moshi,
            onErrorFallbackToDefault = true,
        )

        dsv.flow.first() shouldBe Anum.A
    }

    @Test
    fun `large collection round-trips correctly`() = runTest {
        val testStore = createDataStore(this)
        val moshi = Moshi.Builder().build()

        val largeSet = (1..500).map { TestGson(string = "item-$it") }.toSet()

        val dsv = testStore.createSetValue<TestGson>(
            key = "largeSet",
            defaultValue = emptySet(),
            moshi = moshi,
        )

        dsv.update { largeSet }
        dsv.flow.first().size shouldBe 500
    }
}

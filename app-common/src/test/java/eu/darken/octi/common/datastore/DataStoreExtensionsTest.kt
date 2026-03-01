package eu.darken.octi.common.datastore

import androidx.datastore.preferences.core.*
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File

class DataStoreExtensionsTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private fun createDataStore(scope: TestScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    @Test
    fun `reading and writing strings`() = runTest {
        val testStore = createDataStore(this)

        testStore.createValue<String?>(
            key = "testKey",
            defaultValue = "default"
        ).apply {
            keyName shouldBe "testKey"

            flow.first() shouldBe "default"
            valueBlocking shouldBe "default"
            value() shouldBe "default"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe "default"
                "newvalue"
            } shouldBe DataStoreValue.Updated("default", "newvalue")

            valueBlocking shouldBe "newvalue"
            flow.first() shouldBe "newvalue"
            value() shouldBe "newvalue"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe "newvalue"

            update {
                it shouldBe "newvalue"
                null
            } shouldBe DataStoreValue.Updated("newvalue", "default")

            flow.first() shouldBe "default"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            value("newsecond")
            value() shouldBe "newsecond"
        }
    }

    @Test
    fun `reading and writing boolean`() = runTest {
        val testStore = createDataStore(this)

        testStore.createValue<Boolean>(
            key = "testKey",
            defaultValue = true
        ).apply {
            keyName shouldBe "testKey"

            flow.first() shouldBe true
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe true
                false
            } shouldBe DataStoreValue.Updated(old = true, new = false)

            flow.first() shouldBe false
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe false

            update {
                it shouldBe false
                null
            } shouldBe DataStoreValue.Updated(old = false, new = true)

            flow.first() shouldBe true
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null
        }

        testStore.createValue<Boolean?>(
            key = "testKey2",
            defaultValue = null
        ).apply {
            keyName shouldBe "testKey2"

            flow.first() shouldBe null
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe null
                false
            } shouldBe DataStoreValue.Updated(old = null, new = false)

            flow.first() shouldBe false
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe false

            update {
                it shouldBe false
                null
            } shouldBe DataStoreValue.Updated(old = false, new = null)

            flow.first() shouldBe null
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing long`() = runTest {
        val testStore = createDataStore(this)

        testStore.createValue<Long?>(
            key = "testKey",
            defaultValue = 9000L
        ).apply {
            flow.first() shouldBe 9000L
            testStore.data.first()[longPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe 9000L
                9001L
            }

            flow.first() shouldBe 9001L
            testStore.data.first()[longPreferencesKey(keyName)] shouldBe 9001L

            update {
                it shouldBe 9001L
                null
            }

            flow.first() shouldBe 9000L
            testStore.data.first()[longPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing integer`() = runTest {
        val testStore = createDataStore(this)

        testStore.createValue<Long?>(
            key = "testKey",
            defaultValue = 123
        ).apply {

            flow.first() shouldBe 123
            testStore.data.first()[intPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe 123
                44
            }

            flow.first() shouldBe 44
            testStore.data.first()[intPreferencesKey(keyName)] shouldBe 44

            update {
                it shouldBe 44
                null
            }

            flow.first() shouldBe 123
            testStore.data.first()[intPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing float`() = runTest {
        val testStore = createDataStore(this)

        testStore.createValue<Float?>(
            key = "testKey",
            defaultValue = 3.6f
        ).apply {
            flow.first() shouldBe 3.6f
            testStore.data.first()[floatPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe 3.6f
                15000f
            }

            flow.first() shouldBe 15000f
            testStore.data.first()[floatPreferencesKey(keyName)] shouldBe 15000f

            update {
                it shouldBe 15000f
                null
            }

            flow.first() shouldBe 3.6f
            testStore.data.first()[floatPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `concurrent updates are atomic`() = runTest {
        val testStore = createDataStore(this)

        val dsv = testStore.createValue(
            key = "counter",
            defaultValue = 0
        )

        val iterations = 100
        coroutineScope {
            val jobs = (1..iterations).map {
                async {
                    dsv.update { current -> current + 1 }
                }
            }
            jobs.forEach { it.await() }
        }

        dsv.value() shouldBe iterations
    }

    @Test
    fun `empty string vs null are distinct`() = runTest {
        val testStore = createDataStore(this)

        val dsv = testStore.createValue<String?>(
            key = "testKey",
            defaultValue = null
        )

        dsv.value() shouldBe null

        dsv.value("")
        dsv.value() shouldBe ""
        testStore.data.first()[stringPreferencesKey("testKey")] shouldBe ""

        dsv.value(null)
        dsv.value() shouldBe null
        testStore.data.first()[stringPreferencesKey("testKey")] shouldBe null
    }

    @Test
    fun `multiple DataStoreValue instances for same key are consistent`() = runTest {
        val testStore = createDataStore(this)

        val dsv1 = testStore.createValue<String?>(
            key = "sharedKey",
            defaultValue = "default"
        )
        val dsv2 = testStore.createValue<String?>(
            key = "sharedKey",
            defaultValue = "default"
        )

        dsv1.update { "written-by-1" }
        dsv2.value() shouldBe "written-by-1"

        dsv2.update { "written-by-2" }
        dsv1.value() shouldBe "written-by-2"
    }

    @Test
    fun `update returning null on non-nullable type falls back to default`() = runTest {
        val testStore = createDataStore(this)

        val dsv = testStore.createValue(
            key = "testKey",
            defaultValue = true
        )

        dsv.value() shouldBe true

        @Suppress("UNCHECKED_CAST")
        val result = dsv.update { null }
        result.new shouldBe true
    }

    @Test
    fun `rapid sequential updates preserve final state`() = runTest {
        val testStore = createDataStore(this)

        val dsv = testStore.createValue(
            key = "testKey",
            defaultValue = 0
        )

        for (i in 1..50) {
            dsv.update { i }
        }

        dsv.value() shouldBe 50
    }

}

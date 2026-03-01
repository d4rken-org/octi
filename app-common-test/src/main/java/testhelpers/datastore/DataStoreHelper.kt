package testhelpers.datastore

import eu.darken.octi.common.datastore.DataStoreValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

fun <T> mockDataStoreValue(value: T): DataStoreValue<T> = mockk<DataStoreValue<T>>().apply {
    every { flow } returns flowOf(value)
    every { keyName } returns "mocked"
}

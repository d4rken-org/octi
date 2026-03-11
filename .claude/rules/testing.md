# Testing Guidelines

## Framework

- **JUnit 5** for unit tests (must extend `BaseTest`)
- `useJUnitPlatform()` is configured in all module build files

## Base Test Class

- **`BaseTest`**: Base class for all unit tests
  - Location: `app-common-test/src/main/java/testhelpers/BaseTest.kt`
  - Provides custom logging setup, MockK cleanup via `@AfterAll`, and `IO_TEST_BASEDIR` constant
  - `HiltTestRunner` (`eu.darken.octi.HiltTestRunner`) for instrumented tests

## Testing Patterns

```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.every

class ExampleTest : BaseTest() {
    @BeforeEach
    fun setup() {
        // Test setup
    }

    @Nested
    inner class `some feature` {
        @Test
        fun `descriptive test name with backticks`() = runTest2 {
            val input = "test"
            val result = functionUnderTest(input)
            result shouldBe "expected"
        }
    }
}
```

## Testing Libraries

- **Assertions**: Kotest matchers (`shouldBe`, `shouldThrow`, etc.)
- **Mocking**: MockK (`mockk<Class>()`, `every { ... } returns ...`, `coEvery`)
- **Coroutine Testing**: `kotlinx-coroutines-test`

## Coroutine Testing Helpers

- `runTest2()`: Wraps `kotlinx.coroutines.test.runTest` with auto-cancellation and expected error support
- `TestCollector<T>`: Flow testing via `flow.test(scope)` — provides `latestValue`, `await()`, `awaitFinal()`

## Test Organization

- Tests mirror the main source structure (package by feature)
- Always extend `BaseTest`
- Use backtick syntax for readable test names
- Use `@Nested` inner classes for logical test grouping
- Use `@TempDir` for temporary directory support
- Test utilities shared via `:app-common-test` module

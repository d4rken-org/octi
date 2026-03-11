package eu.darken.octi.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NavigationControllerTest : BaseTest() {

    @Serializable
    data object DestA : NavigationDestination

    @Serializable
    data object DestB : NavigationDestination

    @Serializable
    data object DestC : NavigationDestination

    @Serializable
    data object DestD : NavigationDestination

    private lateinit var controller: NavigationController
    private lateinit var backStack: NavBackStack<NavKey>

    @BeforeEach
    fun setup() {
        controller = NavigationController()
        backStack = NavBackStack(DestA)
        controller.setup(backStack)
    }

    @Nested
    inner class `popTo` {

        @Test
        fun `pops to existing destination`() {
            backStack.add(DestB)
            backStack.add(DestC)
            backStack.add(DestD)

            val result = controller.popTo(DestB)

            result shouldBe true
            backStack.toList() shouldBe listOf(DestA, DestB)
        }

        @Test
        fun `pops to existing destination with inclusive`() {
            backStack.add(DestB)
            backStack.add(DestC)
            backStack.add(DestD)

            val result = controller.popTo(DestB, inclusive = true)

            result shouldBe true
            backStack.toList() shouldBe listOf(DestA)
        }

        @Test
        fun `returns false when destination not found`() {
            backStack.add(DestB)

            val result = controller.popTo(DestC)

            result shouldBe false
            backStack.toList() shouldBe listOf(DestA, DestB)
        }

        @Test
        fun `never removes the last backstack entry`() {
            val result = controller.popTo(DestA, inclusive = true)

            result shouldBe true
            backStack.toList() shouldBe listOf(DestA)
        }

        @Test
        fun `no-op when already at destination`() {
            backStack.add(DestB)

            val result = controller.popTo(DestB)

            result shouldBe true
            backStack.toList() shouldBe listOf(DestA, DestB)
        }

        @Test
        fun `pops to first entry non-inclusive preserves it`() {
            backStack.add(DestB)
            backStack.add(DestC)

            val result = controller.popTo(DestA)

            result shouldBe true
            backStack.toList() shouldBe listOf(DestA)
        }
    }
}

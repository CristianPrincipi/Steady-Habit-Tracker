package com.cristianprincipi.habits.core.testing

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Proves the test toolchain itself works. This is not a placeholder — it is a regression guard.
 *
 * This project pins an unusual combination on purpose: Kotlin 2.2.10 held back to match AGP 9's
 * built-in compiler (ADR-011), KSP on the matching 2.2.10 line (ADR-014), and a Gradle daemon on
 * JDK 21 (ADR-013). MockK in particular does bytecode manipulation and is sensitive to exactly
 * those things. If a future version bump breaks one of these libraries, this test fails
 * immediately and points at the cause, instead of the breakage surfacing as a confusing failure
 * inside some unrelated feature test months later.
 */
class TestToolchainTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // `TestScope.currentTime` is still an opt-in API in coroutines 1.11.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `runTest skips delays using virtual time`() =
        runTest {
            val start = currentTime

            delay(TEN_SECONDS_MS)

            // A real 10s sleep would make the suite unusable. Asserting on virtual time proves the
            // test scheduler is actually in control, rather than the delay merely being short.
            assertEquals(TEN_SECONDS_MS, currentTime - start)
        }

    @Test
    fun `turbine reads every emission of a flow in order`() =
        runTest {
            val flow: Flow<Int> = flowOf(1, 2, 3)

            flow.test {
                assertEquals(1, awaitItem())
                assertEquals(2, awaitItem())
                assertEquals(3, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `mockk stubs a return value and verifies the call`() {
        val greeter = mockk<Greeter>()
        every { greeter.greet("habits") } returns "hello habits"

        val result = greeter.greet("habits")

        assertEquals("hello habits", result)
        verify(exactly = 1) { greeter.greet("habits") }
    }

    @Test
    fun `the main dispatcher rule makes Dispatchers Main usable on the JVM`() =
        runTest {
            var ranOnMain = false

            withContext(Dispatchers.Main) { ranOnMain = true }

            // Without MainDispatcherRule this line throws: there is no Android main looper on the JVM.
            // That is the whole reason every ViewModel test will declare the rule.
            assertTrue(ranOnMain)
        }

    /** Mocking target for the MockK check above. Interfaces are what this project actually mocks. */
    private interface Greeter {
        fun greet(name: String): String
    }

    private companion object {
        const val TEN_SECONDS_MS = 10_000L
    }
}

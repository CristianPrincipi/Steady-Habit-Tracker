package com.cristianprincipi.habits.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The one place a local write is allowed to fail without taking the app down — M13.12.
 *
 * Small, because [launchWrite] is small. It is here because the two things it gets right are both
 * invisible at the call sites that depend on them: a failure is **reported**, and a cancellation is
 * **not a failure**.
 */
class LocalWriteTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Subject : ViewModel() {
        val failures = mutableListOf<Unit>()

        fun cancelScope() = viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun aWriteThatThrowsIsReportedRatherThanThrown() =
        runTest {
            // The whole point. Before this, the exception left `viewModelScope` — whose
            // `SupervisorJob` stops siblings being cancelled but does nothing about the throw — and
            // reached the thread's uncaught handler.
            val subject = Subject()

            subject.launchWrite(onFailure = { subject.failures += Unit }) {
                throw IOException("disk full")
            }

            assertEquals(1, subject.failures.size)
        }

    @Test
    fun aWriteThatSucceedsReportsNothing() =
        runTest {
            val subject = Subject()
            var ran = false

            subject.launchWrite(onFailure = { subject.failures += Unit }) { ran = true }

            assertTrue("the block ran", ran)
            assertTrue("and said nothing", subject.failures.isEmpty())
        }

    @Test
    fun aCancelledWriteIsNotAFailure() =
        runTest {
            // ⚠⚠ **The subtle one, and it would have been a lie on screen.**
            // `kotlinx.coroutines.CancellationException` is a `java.util.concurrent`
            // `CancellationException`, which is an `IllegalStateException` — so it arrives at a
            // `catch (e: Exception)` like any other. Leaving the Week tab cancels `viewModelScope`,
            // and without the rethrow the user would be told "couldn't save" about a write they had
            // simply navigated away from.
            val subject = Subject()
            val started = CompletableDeferred<Unit>()

            val job =
                subject.launchWrite(onFailure = { subject.failures += Unit }) {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await() // never finishes; only cancellation ends it
                }
            started.await()
            subject.cancelScope()

            assertTrue("the write stopped", job.isCancelled)
            assertTrue("and reported nothing", subject.failures.isEmpty())
        }

    @Test
    fun aCancellationThrownByTheBlockItselfIsAlsoNotAFailure() =
        runTest {
            // The same claim reached the other way: it is the exception *type* that is rethrown, not
            // some property of who cancelled. A block that raises one directly must not be reported
            // as a device that would not write.
            val subject = Subject()

            val job =
                subject.launchWrite(
                    onFailure = { subject.failures += Unit },
                ) { throw CancellationException("gone") }

            assertTrue(subject.failures.isEmpty())
            assertFalse("and it did not complete normally", job.isActive)
        }
}

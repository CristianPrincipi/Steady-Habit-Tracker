package com.cristianprincipi.habits.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * The throttle, driven by the coroutine scheduler's **virtual** clock.
 *
 * `runTest` fast-forwards `delay`, so a test covering ten seconds of spacing finishes instantly —
 * and, crucially, deterministically. The limiter takes its `nowMillis` from `testScheduler` so its
 * idea of time and the scheduler's are the same one; a limiter reading `System.nanoTime` here would
 * see no time pass at all while the scheduler skipped forward, and would let every request through.
 */
class RateLimiterTest {
    private val interval = 100.milliseconds

    @Test
    fun theFirstRequestIsNotDelayed() =
        runTest {
            val limiter = RateLimiter(interval) { testScheduler.currentTime }

            limiter.withPermit { }

            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun aSecondRequestWaitsOutTheInterval() =
        runTest {
            val limiter = RateLimiter(interval) { testScheduler.currentTime }

            limiter.withPermit { }
            limiter.withPermit { }

            assertEquals(100L, testScheduler.currentTime)
        }

    @Test
    fun requestsAreSpacedEvenlyRatherThanBunched() =
        runTest {
            val limiter = RateLimiter(interval) { testScheduler.currentTime }
            val startedAt = mutableListOf<Long>()

            repeat(5) { limiter.withPermit { startedAt += testScheduler.currentTime } }

            assertEquals(listOf(0L, 100L, 200L, 300L, 400L), startedAt)
        }

    @Test
    fun aRequestThatArrivesLateIsNotMadeToWait() =
        runTest {
            // The common case in this app: check-offs are minutes apart, so the throttle should be
            // invisible. A limiter that always waited would add latency to every single sync.
            val limiter = RateLimiter(interval) { testScheduler.currentTime }
            limiter.withPermit { }

            testScheduler.advanceTimeBy(10_000)
            limiter.withPermit { }

            assertEquals(10_000L, testScheduler.currentTime)
        }

    @Test
    fun aPauseDoesNotBankUpCreditForABurst() =
        runTest {
            // The bug a naive "next = now + interval" produces: after a long idle period, several
            // queued requests all read a `now` far past their due time and fire together —
            // precisely the burst the throttle exists to prevent.
            val limiter = RateLimiter(interval) { testScheduler.currentTime }
            limiter.withPermit { }
            testScheduler.advanceTimeBy(10_000)

            val startedAt = mutableListOf<Long>()
            repeat(3) { limiter.withPermit { startedAt += testScheduler.currentTime } }

            assertEquals(listOf(10_000L, 10_100L, 10_200L), startedAt)
        }

    @Test
    fun concurrentCallersAreSpacedToo() =
        runTest {
            // Nothing serialises calls into the sync engine, so the limiter has to hold under
            // concurrency rather than relying on callers taking turns.
            val limiter = RateLimiter(interval) { testScheduler.currentTime }
            val startedAt = mutableListOf<Long>()

            (1..4)
                .map { async { limiter.withPermit { startedAt += testScheduler.currentTime } } }
                .awaitAll()

            assertEquals(listOf(0L, 100L, 200L, 300L), startedAt.sorted())
        }

    @Test
    fun theBlockRunsOutsideTheLockSoSlowCallsDoNotQueueBehindEachOther() =
        runTest {
            // Holding the lock across the request would turn the limiter into a queue of one: four
            // 1-second requests would take 4 seconds instead of overlapping. This asserts they do
            // not — total elapsed stays near the spacing, not the sum of the call durations.
            val limiter = RateLimiter(interval) { testScheduler.currentTime }

            (1..4)
                .map {
                    async {
                        limiter.withPermit { kotlinx.coroutines.delay(1_000) }
                    }
                }.awaitAll()

            // Last permit at 300ms + a 1000ms call = 1300ms. Serialised it would be ~4000ms.
            assertTrue("Took ${testScheduler.currentTime}ms; expected ~1300ms", testScheduler.currentTime < 2_000)
        }

    @Test
    fun theDefaultIntervalStaysUnderThreePerSecond() =
        runTest {
            // docs/notion-api.md §6: "an average of three requests per second". 333ms would be
            // 3.003/s — over the limit on the only side that matters, which is why it is 334.
            val limiter = RateLimiter(nowMillis = { testScheduler.currentTime })

            repeat(4) { limiter.withPermit { } }

            assertTrue(
                "Three intervals took ${testScheduler.currentTime}ms, which is faster than 3/s",
                testScheduler.currentTime >= 1_000L,
            )
        }

    @Test
    fun theBlocksResultComesBack() =
        runTest {
            val limiter = RateLimiter(interval) { testScheduler.currentTime }

            assertEquals("ok", limiter.withPermit { "ok" })
        }
}

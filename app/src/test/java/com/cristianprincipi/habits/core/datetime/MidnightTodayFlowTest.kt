package com.cristianprincipi.habits.core.datetime

import app.cash.turbine.test
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The ticker behind M6.9: when it looks at the calendar again, and what it says when it does.
 *
 * The clock here is **driven by the test scheduler**, so `delay` and "what time is it" share one
 * timeline. That is the only arrangement in which these assertions mean anything: a clock the test
 * moved by hand while virtual time stood still would let a broken wait pass, and virtual time moved
 * while the clock stood still would let a broken *reading* pass.
 */
class MidnightTodayFlowTest {
    private val zone = ZoneOffset.UTC

    /**
     * A clock at [base] plus however much virtual time has elapsed, plus any [skew] a test has added.
     *
     * [skew] is how a test says "the wall clock jumped" — a corrected date, or a flight into another
     * zone — which is the case the wait's cap exists for.
     */
    private class ScheduledClock(
        private val base: Instant,
        private val scheduler: TestCoroutineScheduler,
        private val zone: ZoneId,
    ) : Clock() {
        var skew: Duration = Duration.ZERO

        override fun instant(): Instant = base.plusMillis(scheduler.currentTime).plus(skew)

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = ScheduledClock(base, scheduler, zone)
    }

    private fun TestCoroutineScheduler.clockAt(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ) = ScheduledClock(date.atTime(hour, minute).toInstant(zone), this, zone)

    @Test
    fun itSaysWhatDayItIsWithoutBeingWaitedFor() =
        runTest {
            // The landing screen renders from this, so a first emission that had to wait for
            // anything would be a spinner in disguise.
            val clock = testScheduler.clockAt(DAY, hour = 9)

            MidnightTodayFlow(clock)().test {
                assertEquals(DAY, awaitItem())
            }
        }

    @Test
    fun midnightProducesTheNewDate() =
        runTest {
            val clock = testScheduler.clockAt(DAY, hour = 23, minute = 59)

            MidnightTodayFlow(clock)().test {
                assertEquals(DAY, awaitItem())

                advanceTimeBy(Duration.ofMinutes(1).toMillis() + 1)

                assertEquals(DAY.plusDays(1), awaitItem())
            }
        }

    @Test
    fun theLastWaitBeforeMidnightIsTheRealRemainderRatherThanARoundedPoll() =
        runTest {
            // A minute before midnight the wait is a minute, not the fifteen-minute cap — so the
            // ring moves as the day does, not up to a quarter of an hour afterwards.
            val clock = testScheduler.clockAt(DAY, hour = 23, minute = 59)

            MidnightTodayFlow(clock)().test {
                assertEquals(DAY, awaitItem())

                advanceTimeBy(Duration.ofSeconds(59).toMillis())

                expectNoEvents()
            }
        }

    @Test
    fun aDayThatHasNotChangedSaysNothing() =
        runTest {
            // Several wake-ups happen inside this hour, because of the cap. None of them is news,
            // and `WeekViewModel` re-subscribes a Room query on every one that is.
            val clock = testScheduler.clockAt(DAY, hour = 0)

            MidnightTodayFlow(clock)().test {
                assertEquals(DAY, awaitItem())

                advanceTimeBy(Duration.ofHours(1).toMillis())

                expectNoEvents()
            }
        }

    @Test
    fun aClockThatJumpsIsNoticedWithoutWaitingForMidnight() =
        runTest {
            // ⭐ What the cap is for. Just after midnight the honest wait is nearly twenty-four
            // hours, and a ticker that committed to it would report the wrong date all day after a
            // time-zone change or a corrected clock. Capped, it is wrong for at most one interval.
            val clock = testScheduler.clockAt(DAY, hour = 0, minute = 5)

            MidnightTodayFlow(clock)().test {
                assertEquals(DAY, awaitItem())

                clock.skew = Duration.ofDays(2)
                advanceTimeBy(Duration.ofMinutes(15).toMillis() + 1)

                assertEquals(DAY.plusDays(2), awaitItem())
            }
        }

    private companion object {
        /** A Wednesday, for no reason other than that nothing here is week-sensitive. */
        val DAY: LocalDate = LocalDate.of(2026, 8, 26)
    }
}

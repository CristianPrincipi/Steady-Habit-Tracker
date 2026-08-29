package com.cristianprincipi.habits.core.datetime

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Today, and again every time it changes — **the thing an injected [Clock] cannot give you**.
 *
 * `CLAUDE.md` §6 bans `LocalDate.now()` below the UI so that tests can control time, and ADR-015
 * settled that the abstraction is `java.time.Clock`. That solves *asking* what day it is. It does
 * not solve **being told when the answer changes**, and a screen built on a clock alone is correct
 * only at the instant it renders.
 *
 * `CLAUDE.md` §6 asks for the day-rollover case to be handled explicitly. This is the input that
 * makes it possible: a `Flow` rather than a function, so a screen can be a function *of* today
 * instead of taking a reading of it.
 *
 * **An interface with one real implementation**, so a ViewModel test can drive a rollover from a
 * `MutableStateFlow` in one line instead of arranging virtual time around a ticker. The ticker's own
 * arithmetic is tested against [MidnightTodayFlow] directly.
 */
interface TodayFlow {
    /**
     * The current date, emitted immediately, and again on each change.
     *
     * Distinct by contract: a subscriber may treat every emission as a real day change.
     */
    operator fun invoke(): Flow<LocalDate>
}

/**
 * [TodayFlow] over the wall clock, waking at each local midnight.
 *
 * ### ⚠ It does not sleep all the way to midnight, and the cap is the interesting part
 *
 * Sleeping exactly until the next local midnight is the obvious implementation and it is one
 * unverifiable assumption long: that nothing moves the target while we are waiting. Three things
 * can, and all of them are ordinary.
 *
 * - **The time zone changes.** [SystemDefaultZoneClock] resolves the zone per call precisely because
 *   a user can fly somewhere, so "today" can change without the wall clock advancing at all. A
 *   `delay` computed in Madrid is wrong the moment the phone lands in Auckland.
 * - **The user sets the clock.** Correcting a wrong date is exactly when the app is most visibly
 *   wrong, and a ticker that has committed to a twenty-hour sleep will not notice.
 * - **`delay` on Android is not wall-clock time.** The main dispatcher schedules through a `Handler`,
 *   whose timebase stops while the device is in deep sleep. A long sleep can therefore land late by
 *   however long the phone dozed.
 *
 * So the wait is `min(time until midnight, `[MAX_WAIT]`)`. At the boundary it is still exact — the
 * last hop before midnight is the real remainder, not a rounded-up poll — while any of the above
 * costs at most one extra interval of staleness instead of a whole day. Extra wake-ups are free:
 * `distinctUntilChanged` means an unchanged date emits nothing, and a subscriber is only ever
 * collecting while a screen is on it (`WhileSubscribed`).
 *
 * ⚠ **The deep-sleep case is bounded rather than solved**, and knowingly. Handling it properly means
 * a `BroadcastReceiver` for `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED`, which puts a
 * `Context` into `core/datetime` for a screen that is, by definition, not being looked at while the
 * device sleeps. If a rollover ever has to be exact while the app is backgrounded — a reminder, a
 * widget — that is when to pay for it.
 */
@Singleton
class MidnightTodayFlow
    @Inject
    constructor(
        private val clock: Clock,
    ) : TodayFlow {
        override fun invoke(): Flow<LocalDate> =
            flow {
                while (true) {
                    emit(LocalDate.now(clock))
                    delay(waitMillis())
                }
            }.distinctUntilChanged()

        /**
         * How long to wait before looking again.
         *
         * `coerceIn` rather than `coerceAtMost`: the lower bound is what stops a busy loop if the
         * remainder ever comes back as zero or negative. It should not — the next midnight is
         * strictly after now, and `atStartOfDay(zone)` resolves forward through a DST gap rather
         * than producing an earlier instant — but a spin here would be a flat battery, and one
         * millisecond of floor costs nothing to guarantee.
         */
        private fun waitMillis(): Long {
            val now = ZonedDateTime.now(clock)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            return Duration.between(now, nextMidnight).toMillis().coerceIn(1L, MAX_WAIT)
        }

        private companion object {
            /**
             * The longest this will ever wait between readings.
             *
             * Fifteen minutes is a compromise with nothing clever behind it: short enough that a
             * changed zone or a corrected clock is picked up while the user is still looking at the
             * screen, long enough to be nothing at all on a battery.
             */
            const val MAX_WAIT = 15L * 60L * 1000L
        }
    }

package com.cristianprincipi.habits.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The completion rate, including the divide-by-zero `docs/design-spec.md` §4 asks to be guarded.
 *
 * Eligible days are the trailing 365 ending today, from the habit's start date onward, both ends
 * inclusive. Every case below is really about one of those three boundaries.
 */
class GetCompletionRateTest {
    private val today = LocalDate.of(2026, 8, 22)
    private val rate = GetCompletionRate(Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))

    private fun daysEndingToday(count: Int): List<LocalDate> = (0 until count).map { today.minusDays(it.toLong()) }

    @Test
    fun aHabitThatStartsTodayHasOneEligibleDayNotZero() {
        // Inclusive at both ends. Zero would make the first day of every habit a divide-by-zero.
        val result = rate(completedDates = emptyList(), startDate = today)

        assertEquals(1, result.eligibleDays)
        assertEquals(0, result.completedDays)
        assertEquals(0, result.percent)
    }

    @Test
    fun aHabitCheckedOffOnItsFirstDayIsAtOneHundredPercent() {
        val result = rate(completedDates = listOf(today), startDate = today)

        assertEquals(1, result.completedDays)
        assertEquals(1, result.eligibleDays)
        assertEquals(100, result.percent)
    }

    @Test
    fun aStartDateInTheFutureIsZeroPercentAndNotACrash() {
        // The guarded case. Nothing has been missed, so 0% is the honest answer — 100% would claim
        // a perfect record for a habit that has not begun.
        val result = rate(completedDates = emptyList(), startDate = today.plusDays(30))

        assertEquals(0, result.eligibleDays)
        assertEquals(0, result.percent)
    }

    @Test
    fun halfTheDaysKeptIsFiftyPercent() {
        val result = rate(completedDates = daysEndingToday(10), startDate = today.minusDays(19))

        assertEquals(10, result.completedDays)
        assertEquals(20, result.eligibleDays)
        assertEquals(50, result.percent)
    }

    @Test
    fun theRateRoundsToTheNearestWholePercent() {
        val oneOfThree = rate(completedDates = listOf(today), startDate = today.minusDays(2))
        val twoOfThree = rate(completedDates = daysEndingToday(2), startDate = today.minusDays(2))

        assertEquals(33, oneOfThree.percent)
        assertEquals(67, twoOfThree.percent)
    }

    @Test
    fun aHabitOlderThanTheWindowIsMeasuredOverTheTrailingYearOnly() {
        // Where this departs from Plan.md M3.10's "startDate to today" wording. The design spec's
        // window wins, and it is also the one the Year grid above the figure is drawn from — a
        // percentage measured over a different span than the dots beside it would look wrong.
        val result = rate(completedDates = emptyList(), startDate = today.minusYears(3))

        assertEquals(365, result.eligibleDays)
    }

    @Test
    fun completionsOlderThanTheWindowDoNotCount() {
        val insideWindow = daysEndingToday(5)
        val longBefore = listOf(today.minusDays(400), today.minusDays(500))

        val result = rate(completedDates = insideWindow + longBefore, startDate = today.minusYears(3))

        assertEquals(5, result.completedDays)
    }

    @Test
    fun aCompletionOnTheOldestEligibleDayStillCounts() {
        // The inclusive lower bound. Off by one here silently shaves a day off every rate.
        val oldest = today.minusDays(364)

        val result = rate(completedDates = listOf(oldest), startDate = today.minusYears(3))

        assertEquals(1, result.completedDays)
        assertEquals(365, result.eligibleDays)
    }

    @Test
    fun aCompletionOneDayOlderThanTheWindowDoesNot() {
        val justOutside = today.minusDays(365)

        val result = rate(completedDates = listOf(justOutside), startDate = today.minusYears(3))

        assertEquals(0, result.completedDays)
    }

    @Test
    fun completionsBeforeTheHabitStartedDoNotCount() {
        val result = rate(completedDates = listOf(today.minusDays(30)), startDate = today.minusDays(9))

        assertEquals(0, result.completedDays)
        assertEquals(10, result.eligibleDays)
    }

    @Test
    fun duplicateCompletionsForOneDayCountOnce() {
        // Without the de-duplication, CompletionRate's own invariant would reject the pair.
        val result = rate(completedDates = listOf(today, today, today), startDate = today)

        assertEquals(1, result.completedDays)
        assertEquals(100, result.percent)
    }
}

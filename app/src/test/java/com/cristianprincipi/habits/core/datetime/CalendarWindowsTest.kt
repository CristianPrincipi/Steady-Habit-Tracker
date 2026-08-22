package com.cristianprincipi.habits.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The three grid shapes, in concrete numbers rather than in the formula that produces them.
 *
 * August 2026 is the worked example throughout, and its facts are fixed: the 1st is a **Saturday**,
 * the month has 31 days, and 22 August — the day this was written — is a Saturday too. Asserting
 * against those rather than against a recomputed `dayOfWeek` is the point; a test that derives its
 * expectation the same way the code does agrees with the code whether or not either is right.
 */
class CalendarWindowsTest {
    private val august = YearMonth.of(2026, 8)
    private val saturday = LocalDate.of(2026, 8, 22)

    @Test
    fun augustTwentySixStartsOnASaturday() {
        // The premise every other assertion in this file rests on. If java.time ever disagrees with
        // it, everything below is measuring the wrong month.
        assertEquals(DayOfWeek.SATURDAY, august.atDay(1).dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
    }

    @Test
    fun aWeekIsSevenDaysStartingMonday() {
        val window = weekWindow(saturday)

        assertEquals(7, window.size)
        assertEquals(LocalDate.of(2026, 8, 17), window.first())
        assertEquals(LocalDate.of(2026, 8, 23), window.last())
        assertEquals(DayOfWeek.MONDAY, window.first()!!.dayOfWeek)
    }

    @Test
    fun everyDayOfAWeekGivesTheSameWeek() {
        // Callers step ±7 days from an arbitrary anchor and must never drift off the boundary.
        val fromMonday = weekWindow(LocalDate.of(2026, 8, 17))
        val fromSunday = weekWindow(LocalDate.of(2026, 8, 23))

        assertEquals(fromMonday, weekWindow(saturday))
        assertEquals(fromMonday, fromSunday)
    }

    @Test
    fun aWeekHasNoPadding() {
        assertTrue(weekWindow(saturday).none { it == null })
    }

    @Test
    fun startOfWeekOnAMondayIsThatMonday() {
        // The case a signed modulo gets wrong: on the week start itself the shift must be 0, and on
        // a Sunday it must be 6 rather than -1.
        val monday = LocalDate.of(2026, 8, 17)

        assertEquals(monday, startOfWeek(monday))
        assertEquals(monday, startOfWeek(LocalDate.of(2026, 8, 23)))
    }

    @Test
    fun aMonthLeadsWithBlanksThatPushTheFirstOntoItsWeekday() {
        // The 1st is a Saturday, which is the sixth column Monday-first, so five blanks precede it.
        val window = monthWindow(august)

        assertEquals(5, window.takeWhile { it == null }.size)
        assertEquals(august.atDay(1), window[5])
    }

    @Test
    fun aMonthHasOneCellPerDayAndNoTrailingPadding() {
        // The design leaves the last row ragged; padding it would change the shape of every month
        // (docs/design-spec.md §2).
        val window = monthWindow(august)

        assertEquals(5 + 31, window.size)
        assertEquals(august.atEndOfMonth(), window.last())
        assertEquals(31, window.count { it != null })
    }

    @Test
    fun aMonthBeginningOnAMondayNeedsNoBlanks() {
        // June 2026 begins on a Monday.
        val june = YearMonth.of(2026, 6)

        val window = monthWindow(june)

        assertEquals(DayOfWeek.MONDAY, june.atDay(1).dayOfWeek)
        assertEquals(june.atDay(1), window.first())
        assertEquals(30, window.size)
    }

    @Test
    fun februaryInALeapYearHasTwentyNineCells() {
        val window = monthWindow(YearMonth.of(2028, 2))

        assertEquals(29, window.count { it != null })
        assertEquals(LocalDate.of(2028, 2, 29), window.last())
    }

    @Test
    fun theYearWindowIsThreeHundredAndSixtyFiveDaysEndingToday() {
        val window = trailingYearWindow(saturday)

        assertEquals(365, window.count { it != null })
        assertEquals(saturday, window.last { it != null })
        assertEquals(LocalDate.of(2025, 8, 23), window.first { it != null })
    }

    @Test
    fun theYearWindowIsPaddedToWholeColumns() {
        // The grid is column-major with seven rows, so a partial column would shear the whole thing
        // by one row (docs/design-spec.md §4).
        val window = trailingYearWindow(saturday)

        assertEquals(0, window.size % 7)
        assertEquals(371, window.size)
    }

    @Test
    fun theYearWindowStartsItsFirstColumnOnAMonday() {
        // 364 days is exactly 52 weeks, so a Saturday today means a Saturday 364 days ago — five
        // blanks to put row one on the Monday.
        val window = trailingYearWindow(saturday)

        assertEquals(5, window.takeWhile { it == null }.size)
        assertEquals(DayOfWeek.MONDAY, window.first { it != null }!!.minusDays(5).dayOfWeek)
    }

    @Test
    fun theYearWindowEndsWithTrailingBlanks() {
        val window = trailingYearWindow(saturday)

        assertEquals(1, window.reversed().takeWhile { it == null }.size)
        assertNull(window.last())
    }

    @Test
    fun aYearWindowEndingOnASundayNeedsNoTrailingBlank() {
        // The boundary the trailing-pad arithmetic gets wrong if it is written as `7 - n % 7`
        // without the second modulo: a whole column would gain seven phantom cells.
        val sunday = LocalDate.of(2026, 8, 23)

        val window = trailingYearWindow(sunday)

        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(sunday, window.last())
        assertEquals(0, window.size % 7)
    }
}

package com.cristianprincipi.habits.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * One assertion, and it earns its place.
 *
 * Changing this constant silently shifts the Week strip, the Month grid's leading blanks and every
 * week boundary a streak is drawn against — all of which would still render, plausibly, and wrong.
 * A failing test is a cheaper way to find out than a screenshot.
 */
class WeekStartTest {
    @Test
    fun theWeekStartsOnMonday() {
        assertEquals(DayOfWeek.MONDAY, WeekStart.day)
    }
}

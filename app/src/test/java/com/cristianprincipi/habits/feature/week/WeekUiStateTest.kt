package com.cristianprincipi.habits.feature.week

import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.datetime.startOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The only arithmetic in [WeekUiState], and the one place an off-by-one could hide.
 *
 * `weekEndInclusive` is `+6`, not `+7`, and the difference is invisible on screen until a week
 * renders eight columns or the `‹ Aug 17 – Aug 23 ›` label reads `Aug 17 – Aug 24`. Cheap to pin,
 * and the boundary cases below are the ones that catch a naive `plusDays` on a raw day-of-month.
 */
class WeekUiStateTest {
    private fun content(weekStart: LocalDate) =
        WeekUiState.Content(weekStart = weekStart, habits = emptyList(), isCurrentWeek = false)

    @Test
    fun theWeekEndsSixDaysAfterItStarts() {
        val monday = LocalDate.of(2026, 8, 17)

        assertEquals(LocalDate.of(2026, 8, 23), content(monday).weekEndInclusive)
    }

    @Test
    fun theWeekSpansExactlySevenDaysInclusive() {
        val state = content(LocalDate.of(2026, 8, 17))

        // Inclusive of both ends, so the *count* is the span plus one. Stated this way rather than
        // as "six", because seven is the number the design cares about (`design-spec.md` §3).
        val days = ChronoUnit.DAYS.between(state.weekStart, state.weekEndInclusive) + 1

        assertEquals(DAYS_PER_WEEK.toLong(), days)
    }

    @Test
    fun aWeekCrossingAMonthEndDoesNotWrap() {
        // 31 August 2026 is a Monday, so this week runs into September.
        val state = content(LocalDate.of(2026, 8, 31))

        assertEquals(LocalDate.of(2026, 9, 6), state.weekEndInclusive)
    }

    @Test
    fun aWeekCrossingAYearEndDoesNotWrap() {
        // 28 December 2026 is a Monday; the week ends in 2027.
        val state = content(LocalDate.of(2026, 12, 28))

        assertEquals(LocalDate.of(2027, 1, 3), state.weekEndInclusive)
    }

    @Test
    fun aWeekContainingALeapDayCountsIt() {
        // 2028 is a leap year and 28 February 2028 is a Monday, so 29 February falls inside.
        val state = content(LocalDate.of(2028, 2, 28))

        assertEquals(LocalDate.of(2028, 3, 5), state.weekEndInclusive)
    }

    @Test
    fun theEndAgreesWithTheWeekMathTheRestOfTheAppUses() {
        // ⭐ The assertion that would survive someone "simplifying" either side. `startOfWeek` is
        // what `weekWindow` builds the seven cells from, so if this state's end ever disagreed with
        // it, the label above the pills would describe a different week from the pills below it.
        var date = LocalDate.of(2026, 1, 1)
        repeat(DAYS_PER_WEEK * 2) {
            val state = content(startOfWeek(date))

            assertEquals(startOfWeek(date), startOfWeek(state.weekEndInclusive))

            date = date.plusDays(1)
        }
    }
}

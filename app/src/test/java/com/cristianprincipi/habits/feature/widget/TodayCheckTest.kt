package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.PeriodCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The corner square reads today off the **same cells the grid draws** — M15.11.
 *
 * ⚠ The property worth holding is agreement: a square saying *done* beside a grid whose last dot
 * says *missed* would be two answers to one question on one card.
 */
class TodayCheckTest {
    private val habit = testHabit()
    private val today = LocalDate.of(2026, 10, 15)

    private fun period(
        todayState: DayState,
        todayIndex: Int = 1,
    ) = HabitPeriod(
        habit = habit,
        cells =
            listOf(
                PeriodCell.Day(today.minusDays(1), DayState.COMPLETED),
                PeriodCell.Day(today, todayState),
                PeriodCell.Day(today.plusDays(1), DayState.FUTURE),
            ),
        todayIndex = todayIndex,
    )

    @Test
    fun aCheckedTodayIsAFilledSquareForTodaysDate() {
        assertEquals(
            TodayCheck(habitId = habit.id, date = today, completed = true),
            period(DayState.COMPLETED).todayCheck(),
        )
    }

    @Test
    fun anOpenTodayIsAnOutlineEvenWhenYesterdayWasDone() {
        assertEquals(
            TodayCheck(habitId = habit.id, date = today, completed = false),
            period(DayState.INCOMPLETE).todayCheck(),
        )
    }

    @Test
    fun aHabitThatHasNeverBeenCheckedIsStillOffered() {
        // A habit's start is its first check-off (ADR-046), so today is BEFORE_START until the
        // square is tapped. That tap is how such a habit gets started — it must not be hidden.
        assertEquals(false, period(DayState.BEFORE_START).todayCheck()?.completed)
    }

    @Test
    fun aPeriodWithoutTodayDrawsNoSquare() {
        assertNull(period(DayState.COMPLETED, todayIndex = HabitPeriod.TODAY_ABSENT).todayCheck())
    }
}

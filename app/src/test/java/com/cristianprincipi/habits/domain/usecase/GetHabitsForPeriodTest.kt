package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.PeriodCell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * The three grid use cases: what each cell means, and where today sits.
 *
 * `CalendarWindowsTest` already proves the shapes, so nothing here re-counts blanks. What is tested
 * is the layer above: that a date becomes the right [DayState], that completions are attributed to
 * the habit that earned them, and that a completion outranks the two inferred states.
 */
class GetHabitsForPeriodTest {
    private val today = LocalDate.of(2026, 8, 22)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val read = HabitId("read")
    private val water = HabitId("water")

    private fun days(period: HabitPeriod): List<PeriodCell.Day> = period.cells.filterIsInstance<PeriodCell.Day>()

    private fun stateOn(
        period: HabitPeriod,
        date: LocalDate,
    ): DayState = days(period).first { it.date == date }.state

    @Test
    fun aWeekMarksCompletedPastAndFutureDays() =
        runTest {
            // Today is Saturday, so the week runs Mon 17 – Sun 23 and only Sunday is still to come.
            val repository =
                FakeHabitRepository(
                    habits = listOf(testHabit(id = "read", startDate = LocalDate.of(2026, 1, 1))),
                    completions = listOf(Completion(read, LocalDate.of(2026, 8, 18))),
                )

            val period = GetHabitsForWeek(repository, clock)(today).first().single()

            assertEquals(DayState.INCOMPLETE, stateOn(period, LocalDate.of(2026, 8, 17)))
            assertEquals(DayState.COMPLETED, stateOn(period, LocalDate.of(2026, 8, 18)))
            assertEquals(DayState.INCOMPLETE, stateOn(period, today))
            assertEquals(DayState.FUTURE, stateOn(period, LocalDate.of(2026, 8, 23)))
        }

    @Test
    fun aWeekReportsWhereTodaySits() =
        runTest {
            // The ring on the Week pills is drawn from this, and the UI must never ask a clock
            // itself. Saturday is the sixth pill, index 5.
            val repository = FakeHabitRepository(habits = listOf(testHabit()))

            val period = GetHabitsForWeek(repository, clock)(today).first().single()

            assertEquals(5, period.todayIndex)
        }

    @Test
    fun aWeekThatDoesNotContainTodaySaysSo() =
        runTest {
            val repository = FakeHabitRepository(habits = listOf(testHabit()))

            val period = GetHabitsForWeek(repository, clock)(today.minusWeeks(3)).first().single()

            assertEquals(HabitPeriod.TODAY_ABSENT, period.todayIndex)
        }

    @Test
    fun anyDayOfTheWeekAsksForTheSameWeek() =
        runTest {
            val repository = FakeHabitRepository(habits = listOf(testHabit()))
            val useCase = GetHabitsForWeek(repository, clock)

            val fromMonday = useCase(LocalDate.of(2026, 8, 17)).first().single()
            val fromSunday = useCase(LocalDate.of(2026, 8, 23)).first().single()

            assertEquals(fromMonday, fromSunday)
        }

    @Test
    fun daysBeforeAHabitStartedAreNotMisses() =
        runTest {
            // BEFORE_START rather than INCOMPLETE. startDate is inferred from the earliest day
            // synced from Notion, so this is "no record", not "the user failed".
            val repository =
                FakeHabitRepository(habits = listOf(testHabit(startDate = LocalDate.of(2026, 8, 20))))

            val period = GetHabitsForWeek(repository, clock)(today).first().single()

            assertEquals(DayState.BEFORE_START, stateOn(period, LocalDate.of(2026, 8, 19)))
            assertEquals(DayState.INCOMPLETE, stateOn(period, LocalDate.of(2026, 8, 20)))
        }

    @Test
    fun aCompletionOutranksBeforeStartAndFuture() =
        runTest {
            // A recorded check-off is a fact; the other two are inferences. If they ever collide,
            // the tick the user earned is what gets drawn.
            val beforeStart = LocalDate.of(2026, 8, 17)
            val future = LocalDate.of(2026, 8, 23)
            val repository =
                FakeHabitRepository(
                    habits = listOf(testHabit(startDate = LocalDate.of(2026, 8, 20))),
                    completions = listOf(Completion(read, beforeStart), Completion(read, future)),
                )

            val period = GetHabitsForWeek(repository, clock)(today).first().single()

            assertEquals(DayState.COMPLETED, stateOn(period, beforeStart))
            assertEquals(DayState.COMPLETED, stateOn(period, future))
        }

    @Test
    fun eachHabitOnlyGetsItsOwnCompletions() =
        runTest {
            val day = LocalDate.of(2026, 8, 18)
            val repository =
                FakeHabitRepository(
                    habits = listOf(testHabit(id = "read"), testHabit(id = "water", name = "Water")),
                    completions = listOf(Completion(water, day)),
                )

            val periods = GetHabitsForWeek(repository, clock)(today).first()

            assertEquals(DayState.INCOMPLETE, stateOn(periods.first { it.habit.id == read }, day))
            assertEquals(DayState.COMPLETED, stateOn(periods.first { it.habit.id == water }, day))
        }

    @Test
    fun inactiveHabitsAreLeftOutOfTheGrids() =
        runTest {
            val repository =
                FakeHabitRepository(
                    habits = listOf(testHabit(id = "read"), testHabit(id = "gone", isActive = false)),
                )

            val periods = GetHabitsForWeek(repository, clock)(today).first()

            assertEquals(listOf(read), periods.map { it.habit.id })
        }

    @Test
    fun aMonthCoversEveryDayOfThatMonth() =
        runTest {
            val repository =
                FakeHabitRepository(
                    habits = listOf(testHabit(startDate = LocalDate.of(2026, 1, 1))),
                    completions = listOf(Completion(read, LocalDate.of(2026, 8, 3))),
                )

            val period = GetHabitsForMonth(repository, clock)(YearMonth.of(2026, 8)).first().single()

            assertEquals(31, days(period).size)
            assertEquals(DayState.COMPLETED, stateOn(period, LocalDate.of(2026, 8, 3)))
            assertEquals(DayState.FUTURE, stateOn(period, LocalDate.of(2026, 8, 31)))
        }

    @Test
    fun aMonthKeepsItsLeadingPaddingAsPadding() =
        runTest {
            val repository = FakeHabitRepository(habits = listOf(testHabit()))

            val period = GetHabitsForMonth(repository, clock)(YearMonth.of(2026, 8)).first().single()

            assertTrue(period.cells.take(5).all { it == PeriodCell.Padding })
            assertTrue(period.cells.drop(5).none { it == PeriodCell.Padding })
        }

    @Test
    fun aPastMonthHasNoFutureDays() =
        runTest {
            val repository =
                FakeHabitRepository(habits = listOf(testHabit(startDate = LocalDate.of(2020, 1, 1))))

            val period = GetHabitsForMonth(repository, clock)(YearMonth.of(2026, 7)).first().single()

            assertTrue(days(period).none { it.state == DayState.FUTURE })
            assertEquals(HabitPeriod.TODAY_ABSENT, period.todayIndex)
        }

    @Test
    fun theYearCarriesItsCompletionRateBesideItsGrid() =
        runTest {
            // The two are computed from one query over one window, so the dots and the percentage
            // beside them can never disagree.
            val completions = (0 until 10).map { Completion(read, today.minusDays(it.toLong())) }
            val repository =
                FakeHabitRepository(
                    habits = listOf(testHabit(startDate = today.minusDays(19))),
                    completions = completions,
                )

            val year = GetHabitsForYear(repository, GetCompletionRate(clock), clock)().first().single()

            assertEquals(365, year.period.cells.count { it is PeriodCell.Day })
            assertEquals(10, year.rate.completedDays)
            assertEquals(20, year.rate.eligibleDays)
            assertEquals(50, year.rate.percent)
        }

    @Test
    fun theYearEndsOnTodayAndSaysWhereItIs() =
        runTest {
            val repository = FakeHabitRepository(habits = listOf(testHabit()))

            val year = GetHabitsForYear(repository, GetCompletionRate(clock), clock)().first().single()
            val lastDay =
                year.period.cells
                    .filterIsInstance<PeriodCell.Day>()
                    .last()

            assertEquals(today, lastDay.date)
            assertEquals(year.period.cells.indexOf(lastDay), year.period.todayIndex)
        }
}

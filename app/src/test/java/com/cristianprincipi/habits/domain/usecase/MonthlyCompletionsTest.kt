package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.MonthlyCompletions
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
 * The monthly chart's buckets — `Plan.md` M9.5, `docs/design-spec.md` §5.4.
 *
 * ### What is actually at stake here
 *
 * One thing, and it is the reason the step calls it out: **the prototype gives every month in the
 * window a bucket and increments only the eligible ones**, so a habit that started in March renders
 * a `0` for January that looks exactly like a January the user missed. Half of this file is about
 * the months that must *not* appear.
 *
 * The other half is the invariant that catches a window off by a day in either direction: the
 * buckets and the "completed days" tile are two readings of the same history and have to agree.
 */
class MonthlyCompletionsTest {
    private val today = LocalDate.of(2026, 8, 22)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private suspend fun statsFor(
        habit: Habit,
        completions: List<Completion>,
    ): HabitStats =
        GetHabitStats(
            habits = FakeHabitRepository(listOf(habit), completions),
            calculateStreak = CalculateStreak(clock),
            completionRate = GetCompletionRate(clock),
            clock = clock,
        )(habit).first()

    private fun countIn(
        months: List<MonthlyCompletions>,
        month: YearMonth,
    ): Int? = months.firstOrNull { it.month == month }?.completions

    @Test
    fun `a full year of history is thirteen buckets, oldest first`() =
        runTest {
            // 365 days ending 22 August 2026 start on 23 August 2025, so the window touches thirteen
            // calendar months and the first and last are both partial.
            val habit = testHabit(startDate = LocalDate.of(2020, 1, 1))

            val months = statsFor(habit, completions = emptyList()).months

            assertEquals(13, months.size)
            assertEquals(YearMonth.of(2025, 8), months.first().month)
            assertEquals(YearMonth.of(2026, 8), months.last().month)
            assertEquals(months.map { it.month }.sorted(), months.map { it.month })
        }

    @Test
    fun `a bucket counts the days that were checked off, not the days in the month`() =
        runTest {
            val habit = testHabit(startDate = LocalDate.of(2020, 1, 1))
            val march = listOf(3, 7, 8, 20).map { day -> Completion(habit.id, LocalDate.of(2026, 3, day)) }

            val months = statsFor(habit, completions = march).months

            assertEquals(4, countIn(months, YearMonth.of(2026, 3)))
            assertEquals(0, countIn(months, YearMonth.of(2026, 4)))
        }

    @Test
    fun `months before the habit started are omitted, not drawn as zero`() =
        runTest {
            // ⭐ The prototype's bug, stated as a test. It would render January and February as a
            // legitimate-looking 0 — indistinguishable from two months the user missed entirely.
            val habit = testHabit(startDate = LocalDate.of(2026, 3, 10))

            val months = statsFor(habit, completions = emptyList()).months

            assertEquals(YearMonth.of(2026, 3), months.first().month)
            assertEquals(null, countIn(months, YearMonth.of(2026, 1)))
            assertEquals(null, countIn(months, YearMonth.of(2026, 2)))
        }

    @Test
    fun `the month the habit started in is kept, counted over the part it was there for`() =
        runTest {
            // Not the whole of March — the habit did not exist for the first nine days of it, and
            // dropping the month altogether would lose the days it did exist for.
            val habit = testHabit(startDate = LocalDate.of(2026, 3, 10))
            val march = listOf(11, 12, 30).map { day -> Completion(habit.id, LocalDate.of(2026, 3, day)) }

            val months = statsFor(habit, completions = march).months

            assertEquals(3, countIn(months, YearMonth.of(2026, 3)))
        }

    @Test
    fun `a completion older than the inferred start date pulls its own month back in`() =
        runTest {
            // `startDate` is inferred from the earliest day synced, so this is possible, and
            // `buildPeriod` resolves it in the completion's favour (ADR-094). The chart has to agree
            // with the year grid drawn above it — a filled dot there and no bucket here is the exact
            // disagreement M9.3 widened the query to prevent.
            val habit = testHabit(startDate = LocalDate.of(2026, 3, 10))
            val early = listOf(Completion(habit.id, LocalDate.of(2026, 1, 15)))

            val months = statsFor(habit, completions = early).months

            assertEquals(YearMonth.of(2026, 1), months.first().month)
            assertEquals(1, countIn(months, YearMonth.of(2026, 1)))
        }

    @Test
    fun `the buckets are contiguous, so the axis never draws across a hole`() =
        runTest {
            // The case above is what makes this worth asserting: the leading *days* are dropped
            // rather than the leading months, so February survives between a stray January
            // completion and a March start date, at zero, and the curve stays a curve.
            val habit = testHabit(startDate = LocalDate.of(2026, 3, 10))
            val early = listOf(Completion(habit.id, LocalDate.of(2026, 1, 15)))

            val months = statsFor(habit, completions = early).months

            val expected = generateSequence(months.first().month) { it.plusMonths(1) }.take(months.size).toList()
            assertEquals(expected, months.map { it.month })
            assertEquals(0, countIn(months, YearMonth.of(2026, 2)))
        }

    @Test
    fun `a habit whose start date is in the future has no bucket at all`() =
        runTest {
            // Every cell is BEFORE_START, so there is no month it could have been kept in. The
            // screen hides the card rather than drawing an empty one — `StatsContent`, M9.6.
            val habit = testHabit(startDate = today.plusDays(1))

            assertEquals(emptyList<MonthlyCompletions>(), statsFor(habit, completions = emptyList()).months)
        }

    @Test
    fun `the buckets and the completed-days tile count the same history`() =
        runTest {
            // ⭐ The invariant that catches a window off by one at either end. Both figures come off
            // the same `HabitStats`, and the two screens' worth of arithmetic between them —
            // `GetCompletionRate`'s max(startDate, 365 ago) and this file's drop-the-leading-days —
            // have to arrive at the same total. Five real habits, staggered start dates.
            SampleHabits.habits().forEach { habit ->
                val stats = statsFor(habit, SampleHabits.completions().filter { it.habitId == habit.id })

                assertEquals(
                    "${habit.name} disagrees with its own completed-days tile",
                    stats.rate.completedDays,
                    stats.months.sumOf { it.completions },
                )
            }
        }

    @Test
    fun `no bucket is ever negative or longer than the month it describes`() =
        runTest {
            SampleHabits.habits().forEach { habit ->
                val stats = statsFor(habit, SampleHabits.completions().filter { it.habitId == habit.id })

                stats.months.forEach { month ->
                    assertTrue(
                        "${habit.name} kept ${month.completions} days in a ${month.month} of ${month.month.lengthOfMonth()}",
                        month.completions in 0..month.month.lengthOfMonth(),
                    )
                }
            }
        }
}

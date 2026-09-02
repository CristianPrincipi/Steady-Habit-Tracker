package com.cristianprincipi.habits.domain.usecase

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.system.measureTimeMillis

/**
 * What one Stats screen costs — `Plan.md` M9.7.
 *
 * ### ⚠ The step asks for aggregation "in SQL", and this app does it in Kotlin on purpose
 *
 * `GetHabitStats` takes **one** range query and folds it four ways: a streak, a rate, 365 grid
 * cells and the monthly buckets. Splitting those into four SQL aggregates would be four queries
 * over the same window and four chances for two sections of one screen to disagree about a day —
 * which is the bug ADR-094 widened the query to close and ADR-096 §1 kept closed. A streak is not
 * expressible in SQL without window functions nobody in this project could test either.
 *
 * So the decision stands, and this file is what makes it a *measured* decision rather than a
 * confident one. `QueryPlanTest` covers the other half of the step — that the one query SQLite does
 * run is an indexed search and not a table scan.
 */
class GetHabitStatsCostTest {
    private val today = LocalDate.of(2026, 8, 22)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val habit = testHabit(startDate = today.minusYears(2))

    private fun useCase(repository: FakeHabitRepository) =
        GetHabitStats(
            habits = repository,
            calculateStreak = CalculateStreak(clock),
            completionRate = GetCompletionRate(clock),
            clock = clock,
        )

    /** [habits] habits, each kept every day for [days] — the shape a real database has. */
    private fun history(
        habits: List<Habit>,
        days: Int,
    ): List<Completion> =
        habits.flatMap { each ->
            (0 until days).map { back -> Completion(each.id, today.minusDays(back.toLong())) }
        }

    @Test
    fun `one range query answers every figure on the screen`() =
        runTest {
            // ⭐⭐ The invariant, and nothing else in the project holds it. Four figures come out of
            // this use case and a future session adding a per-habit `COUNT(*)` for the rate — which
            // is exactly what M9.7's wording invites — would be a second window that can drift from
            // the first by a day.
            val repository = FakeHabitRepository(listOf(habit), history(listOf(habit), days = 30))

            useCase(repository)(habit).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, repository.completionQueries.size)
        }

    @Test
    fun `a check-off re-emits the figures without asking the database again`() =
        runTest {
            // The Flow is the subscription; a new value arrives through it. A use case that
            // re-queried per emission would still be *correct*, which is why this is worth pinning:
            // the cost would be invisible until the table was large.
            val repository = FakeHabitRepository(listOf(habit), history(listOf(habit), days = 30))

            useCase(repository)(habit).test {
                awaitItem()
                repository.setCompleted(habit.id, today.minusDays(100), completed = true)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, repository.completionQueries.size)
        }

    @Test
    fun `the window is the habit's own history, not the whole table`() =
        runTest {
            // "Not a full table scan" begins one layer above SQLite: a query asking for everything
            // would be an indexed search over every row the app has ever stored, which is a scan by
            // another name. `QueryPlanTest` proves the index is used; this proves the window is
            // finite and says what bounds it.
            //
            // ⚠ **My first version of this asserted the window never reaches back more than a year,
            // and it failed — correctly.** `GetHabitStats` widens to `min(startDate, 365 days ago)`
            // deliberately: `CalculateStreak` measures the *best* streak over everything, because
            // bounding it to a year while the current streak runs past it produces `current > best`,
            // which `Streak` rejects by invariant. The bound is the habit's history, not a year.
            val old = testHabit(id = "old", startDate = today.minusYears(2))
            val repository = FakeHabitRepository(listOf(old), emptyList())

            useCase(repository)(old).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val window = repository.completionQueries.single()
            assertEquals(today, window.endInclusive)
            assertEquals(old.startDate, window.start)
        }

    @Test
    fun `a habit younger than a year still gets the whole year window`() =
        runTest {
            // The other side of that `minOf`, and it is not symmetry for its own sake: `startDate`
            // is *inferred* from the earliest day synced, so a completion older than it is possible.
            // A window starting at the start date would miss one and draw it `BEFORE_START` here
            // while the Year tab drew the same day `COMPLETED` (ADR-094 §2).
            val young = testHabit(id = "young", startDate = today.minusDays(10))
            val repository = FakeHabitRepository(listOf(young), emptyList())

            useCase(repository)(young).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val window = repository.completionQueries.single()
            assertEquals(today.minusDays(DAYS_PER_YEAR - 1L), window.start)
        }

    @Test
    fun `folding a year of five habits stays far inside a frame budget`() =
        runTest {
            // ⚠⚠ **A bound, not a benchmark, and the difference matters.** The figure this asserts
            // is roughly twenty times the real one, so it cannot flake on a slow or loaded machine.
            // What it catches is an *algorithmic* regression, and there is a specific one to catch:
            // `buildPeriod` takes `completedDates` as a **Set**, and handing it a List instead turns
            // 371 cell lookups into 371 linear searches. That change compiles, passes every other
            // test in the suite, and is invisible until the table is large.
            //
            // Measured on this project at five habits x 365 days: **20 folds in 49 ms, so about
            // 2.5 ms each** — a sixth of a 16 ms frame, and it happens once per emission on a
            // background dispatcher rather than once per frame. ADR-098 records the number, and the
            // test prints it so the next session can compare rather than re-derive.
            val habits = List(HABIT_COUNT) { index -> testHabit(id = "habit$index", startDate = today.minusYears(2)) }
            val repository = FakeHabitRepository(habits, history(habits, days = DAYS_PER_YEAR))
            val stats = useCase(repository)

            // Warm the JIT and the class loader, so the figure below is the fold and not the first
            // touch of `java.time`.
            stats(habits.first()).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val elapsed =
                measureTimeMillis {
                    repeat(FOLDS) { index ->
                        stats(habits[index % habits.size]).test {
                            awaitItem()
                            cancelAndIgnoreRemainingEvents()
                        }
                    }
                }

            // Printed rather than only asserted, so the figure lands in the test report and the next
            // session can compare against it instead of re-deriving it.
            println("M9.7: $FOLDS folds of $HABIT_COUNT habits x $DAYS_PER_YEAR days took ${elapsed}ms")
            assertTrue("$FOLDS folds took ${elapsed}ms, ${elapsed / FOLDS}ms each", elapsed < BUDGET_MILLIS)
        }

    private companion object {
        const val HABIT_COUNT = 5
        const val DAYS_PER_YEAR = 365
        const val FOLDS = 20

        /** 40 ms a fold against a measured 2.5 ms. Loose on purpose — see the test's own comment. */
        const val BUDGET_MILLIS = 800L
    }
}

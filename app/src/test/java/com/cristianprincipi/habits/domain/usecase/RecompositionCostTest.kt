package com.cristianprincipi.habits.domain.usecase

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * M13.5 — **what checking one habit off costs the other four.**
 *
 * ### Why this is the measurement, and not a recomposition counter
 *
 * The Compose compiler report says all 202 UI-emitting composables in this app are **already
 * skippable** — strong skipping has been on by default since Kotlin 2.0.20 and the design system's
 * APIs take mostly primitives. So "can Compose skip this?" is settled, and the question that is
 * left is the one *above* it: **when one day is toggled, does the state layer hand the untouched
 * cards values Compose is able to recognise as unchanged?**
 *
 * ⚠⚠ **Strong skipping compares an *unstable* parameter by INSTANCE, not by `equals`.** So a value
 * that is rebuilt-but-equal defeats it exactly as a changed value would. That is the whole cost
 * model here, and the two assertions below are its two halves:
 *
 * - the untouched habits arrive **equal**, so a stable type would skip them;
 * - and they arrive as **different instances**, so an unstable type will not.
 *
 * `GetHabitsForWeek` maps every habit on every emission, so the second is structural rather than
 * incidental — there is no arrangement of Room flows that would return the same object.
 *
 * ### ⚠ What this test does NOT do
 *
 * It does not assert that Compose skipped anything; a JVM test cannot see that. It pins the
 * *precondition* — equal values, fresh instances — which is what makes marking these types stable
 * worth doing, and what would make removing that marking a regression rather than a preference.
 */
class RecompositionCostTest {
    private val today = LocalDate.of(2026, 8, 22)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private val habits =
        listOf(
            habit("read", "Read"),
            habit("journal", "Journal"),
            habit("quote", "Quote"),
            habit("diet", "Diet"),
            habit("focus", "Focus"),
        )

    private fun habit(
        id: String,
        name: String,
    ) = Habit(
        id = HabitId(id),
        name = name,
        emoji = "📖",
        color = HabitColor.BLUE,
        startDate = today.minusDays(400),
    )

    /**
     * ⭐⭐ **Toggling one habit rebuilds all five, and four of them come back equal.**
     *
     * The four are what a stable type would let Compose skip and an unstable one will not. Measured
     * on the Week, because it is the one screen `CLAUDE.md` §1 calls the core loop and the only
     * place a habit is checked off.
     */
    @Test
    fun `checking one habit off leaves the other four equal but freshly built`() =
        runTest {
            val repository = FakeHabitRepository(habits = habits)
            val periods = GetHabitsForWeek(repository, clock)

            periods(today).test {
                val before = awaitItem().associateBy { it.habit.id }

                repository.setCompleted(HabitId("read"), today, completed = true)

                val after = awaitItem().associateBy { it.habit.id }
                assertEquals(habits.size, after.size)

                val untouched = habits.drop(1).map { it.id }
                untouched.forEach { id ->
                    val a = requireNotNull(before[id])
                    val b = requireNotNull(after[id])

                    // Half one: a stable type would skip these — nothing about them changed.
                    assertEquals("$id changed when a different habit was toggled", a, b)

                    // ⚠ Half two, and the reason the first half is not enough. Strong skipping
                    // compares an unstable parameter by identity, so an equal-but-new instance
                    // recomposes exactly as a changed one would. If this ever starts failing, the
                    // use case has begun reusing instances and the stability marking has stopped
                    // being what earns the skip.
                    assertNotSame("$id came back as the same instance — the cost model has changed", a, b)
                }

                // And the one that was toggled genuinely differs, so the fixture is not inert.
                val toggled = requireNotNull(after[HabitId("read")])
                assertTrue(
                    "the toggled habit is unchanged — this test is measuring nothing",
                    toggled != requireNotNull(before[HabitId("read")]),
                )
            }
        }

    /**
     * ⚠ **The cells are rebuilt too, which is why the marking has to reach [HabitPeriod.cells].**
     *
     * A `List` is an interface, so Compose treats one as unstable however it was produced —
     * `HabitPeriod` is unstable *because of* this field, not because of `habit` beside it. Marking
     * the period stable is a promise about this list never being mutated, and this is the assertion
     * that the promise is true of what the use case actually returns.
     */
    @Test
    fun `a period's cells are a fresh equal list rather than a shared mutable one`() =
        runTest {
            val repository = FakeHabitRepository(habits = habits.take(1))
            val periods = GetHabitsForWeek(repository, clock)

            periods(today).test {
                val before: HabitPeriod = awaitItem().single()
                repository.setCompleted(HabitId("journal"), today, completed = true)
                val after: HabitPeriod = awaitItem().single()

                assertEquals(before.cells, after.cells)
                assertNotSame(before.cells, after.cells)
            }
        }

    /**
     * ⚠ A completion for a habit that is not on screen still re-emits the whole list.
     *
     * Not a defect — `observeCompletions` is one range query for the whole week (ADR-094 §1), and
     * splitting it per habit to avoid this would trade one query for five. It is recorded because it
     * is the reason the equality above matters at all: the flow is *chatty*, and stability is what
     * keeps a chatty flow cheap.
     */
    @Test
    fun `an unrelated completion still re-emits every habit`() =
        runTest {
            val repository = FakeHabitRepository(habits = habits)
            val periods = GetHabitsForWeek(repository, clock)

            periods(today).test {
                awaitItem()
                repository.setCompleted(HabitId("not-on-this-screen"), today, completed = true)
                assertEquals(habits.size, awaitItem().size)
            }
        }
}

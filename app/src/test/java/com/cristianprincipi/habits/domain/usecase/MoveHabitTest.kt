package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Moving a habit one place — M15.5.
 *
 * The arithmetic is tested directly rather than through the repository, which is why [moved] is
 * `internal` and not private: "swap with the neighbour" is the only decision this file makes, and a
 * test that had to stand up a store to check it would be testing the store.
 *
 * ⭐ **The user's actual target is [theTargetOrderIsReachableFromWhatTheirPhoneShows]** — the one
 * case that proves the feature does the job it was asked for, using the real order their phone had.
 */
class MoveHabitTest {
    private val read = testHabit(id = "read", name = "Read")
    private val journal = testHabit(id = "journal", name = "Journal")
    private val quote = testHabit(id = "quote", name = "Quote")
    private val diet = testHabit(id = "diet", name = "Diet")
    private val focus = testHabit(id = "focus", name = "Focus")

    private fun List<Habit>.names() = map(Habit::name)

    private fun id(habit: Habit) = habit.id

    // --- the arithmetic -----------------------------------------------------------------------

    @Test
    fun movingUpSwapsWithThePrecedingHabit() {
        val moved = listOf(read, journal, quote).moved(id(journal), MoveDirection.Up)

        assertEquals(listOf("Journal", "Read", "Quote"), moved?.names())
    }

    @Test
    fun movingDownSwapsWithTheFollowingHabit() {
        val moved = listOf(read, journal, quote).moved(id(journal), MoveDirection.Down)

        assertEquals(listOf("Read", "Quote", "Journal"), moved?.names())
    }

    @Test
    fun movingTheFirstHabitUpDoesNothing() {
        // ⚠ Null rather than the same list, and rather than an exception. The screen disables the
        // control at the ends, so this is a stale tap — the state the screen rendered from and the
        // tap that arrives are two different moments — and turning that into a crash on the
        // settings screen would be the worst available answer.
        assertNull(listOf(read, journal).moved(id(read), MoveDirection.Up))
    }

    @Test
    fun movingTheLastHabitDownDoesNothing() {
        assertNull(listOf(read, journal).moved(id(journal), MoveDirection.Down))
    }

    @Test
    fun movingAHabitThatIsNotInTheListDoesNothing() {
        // Reachable in one real way: a sync hid the habit (ADR-081) between the render and the tap.
        assertNull(listOf(read, journal).moved(HabitId("gone"), MoveDirection.Down))
    }

    @Test
    fun movingTheOnlyHabitDoesNothingInEitherDirection() {
        assertNull(listOf(read).moved(id(read), MoveDirection.Up))
        assertNull(listOf(read).moved(id(read), MoveDirection.Down))
    }

    @Test
    fun nothingElseMoves() {
        // A move is a move, not a sort: everything the user did not touch stays exactly where it
        // was, which is what makes repeated taps predictable.
        val moved = listOf(read, journal, quote, diet, focus).moved(id(diet), MoveDirection.Up)

        assertEquals(listOf("Read", "Journal", "Diet", "Quote", "Focus"), moved?.names())
    }

    @Test
    fun aMoveUpThenDownReturnsTheOriginalOrder() {
        val start = listOf(read, journal, quote)

        val there = start.moved(id(quote), MoveDirection.Up)
        val back = there?.moved(id(quote), MoveDirection.Down)

        assertEquals(start.names(), back?.names())
    }

    // --- the write ----------------------------------------------------------------------------

    @Test
    fun theWholeListIsWrittenBackInItsNewOrder() =
        runTest {
            // ⚠ Every id, not the two that swapped. Writing only what moved would leave two habits
            // claiming one position, and `ORDER BY sortOrder` resolves that arbitrarily — so the
            // list would settle into an order nobody chose.
            val repository = FakeHabitRepository(listOf(read, journal, quote))

            MoveHabit(repository)(
                current = listOf(read, journal, quote),
                habitId = id(quote),
                direction = MoveDirection.Up,
            )

            assertEquals(
                listOf("read", "quote", "journal"),
                repository.observeHabits().first().map { it.id.value },
            )
        }

    @Test
    fun aMoveThatDoesNothingWritesNothing() =
        runTest {
            // Not merely harmless: a no-op write would still emit through the flow and animate the
            // list, so a disabled arrow that had been tapped anyway would twitch the screen.
            val repository = FakeHabitRepository(listOf(read, journal))

            MoveHabit(repository)(
                current = listOf(read, journal),
                habitId = id(read),
                direction = MoveDirection.Up,
            )

            assertEquals(emptyList<List<HabitId>>(), repository.orderWrites)
        }

    @Test
    fun theTargetOrderIsReachableFromWhatTheirPhoneShows() {
        // ⭐⭐ **The case this whole step exists for.** `loadProperties` sorts by property name, and
        // every one of those names begins with an emoji — so their phone showed the habits ordered
        // by emoji codepoint: Journal, Focus, Read, Quote, Diet. The target is Read, Journal,
        // Quote, Diet, Focus. This walks one to the other using only the control the screen offers,
        // which is also a check that no target is unreachable by single steps.
        var order = listOf(journal, focus, read, quote, diet)

        // Read to the front: two moves up.
        order = order.moved(id(read), MoveDirection.Up)!!
        order = order.moved(id(read), MoveDirection.Up)!!
        // Focus to the back: two moves down.
        order = order.moved(id(focus), MoveDirection.Down)!!
        order = order.moved(id(focus), MoveDirection.Down)!!

        assertEquals(listOf("Read", "Journal", "Quote", "Diet", "Focus"), order.names())
    }
}

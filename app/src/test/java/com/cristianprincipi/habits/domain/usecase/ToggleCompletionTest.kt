package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** The two writes v1 has: a check-off, and a colour. */
class ToggleCompletionTest {
    private val today = LocalDate.of(2026, 8, 22)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val read = HabitId("read")

    @Test
    fun checkingOffTodayWritesIt() =
        runTest {
            val repository = FakeHabitRepository()

            val applied = ToggleCompletion(repository, clock)(read, today, completed = true)

            assertTrue(applied)
            assertEquals(listOf(FakeHabitRepository.Write(read, today, true)), repository.writes)
        }

    @Test
    fun undoingWritesTheUndo() =
        runTest {
            val repository = FakeHabitRepository()

            ToggleCompletion(repository, clock)(read, today, completed = false)

            assertEquals(listOf(FakeHabitRepository.Write(read, today, false)), repository.writes)
        }

    @Test
    fun aPastDayCanStillBeCheckedOff() =
        runTest {
            // Filling in a day you forgot is the point of showing the whole week.
            val repository = FakeHabitRepository()

            val applied = ToggleCompletion(repository, clock)(read, today.minusDays(4), completed = true)

            assertTrue(applied)
        }

    @Test
    fun aFutureDayIsRefusedAndNothingIsWritten() =
        runTest {
            // The Week pills report a tap on all seven days, including the future ones a mid-week
            // Saturday still shows, and the design gives them no disabled appearance. The rule
            // lives here rather than in the composable.
            val repository = FakeHabitRepository()

            val applied = ToggleCompletion(repository, clock)(read, today.plusDays(1), completed = true)

            assertFalse(applied)
            assertTrue(repository.writes.isEmpty())
        }

    @Test
    fun theBoundaryIsTodayItselfNotYesterday() =
        runTest {
            val repository = FakeHabitRepository()
            val toggle = ToggleCompletion(repository, clock)

            assertTrue(toggle(read, today, completed = true))
            assertFalse(toggle(read, today.plusDays(1), completed = true))
        }

    @Test
    fun checkingTheSameDayTwiceStatesRatherThanFlips() =
        runTest {
            // The repository contract is stating, not toggling: a sync killed mid-run replays its
            // writes, and a replayed toggle is an undone toggle.
            val repository = FakeHabitRepository()
            val toggle = ToggleCompletion(repository, clock)

            toggle(read, today, completed = true)
            toggle(read, today, completed = true)

            assertTrue(repository.writes.all { it.completed })
        }

    @Test
    fun settingAColourReachesTheRepository() =
        runTest {
            val repository = FakeHabitRepository()

            SetHabitColor(repository)(read, HabitColor.PURPLE)

            assertEquals(listOf(read to HabitColor.PURPLE), repository.colorWrites)
        }
}

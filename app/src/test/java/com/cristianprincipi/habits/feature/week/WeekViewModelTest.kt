package com.cristianprincipi.habits.feature.week

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.PeriodCell
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the Week screen is told, and what its four gestures do.
 *
 * The clock is fixed to **Wednesday 26 August 2026**, mid-week on purpose: a Monday or a Sunday
 * would let an off-by-one in the week maths pass by landing on a boundary anyway, and mid-week is
 * also the only place where one visible week contains both past and future days.
 *
 * The repository is a fake because *how* a cell becomes `COMPLETED` is `GetHabitsForPeriodTest`'s
 * subject. This file is about which week is showing and what the four events do to it.
 *
 * ⚠ **Every test here runs on a fixed clock, so none of them can see the day-rollover bug**
 * (`Plan.md` M6.9). `isCurrentWeek` and `todayIndex` are emission-time facts; a test whose clock
 * never moves cannot notice them going stale. That gap is the reason M6.9 is a step.
 */
class WeekViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Wednesday. The week it belongs to runs Mon 24 Aug – Sun 30 Aug 2026. */
    private val today = LocalDate.of(2026, 8, 26)
    private val thisMonday = LocalDate.of(2026, 8, 24)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private val read = testHabit(id = "read", name = "Read", startDate = LocalDate.of(2026, 1, 1))
    private val run = testHabit(id = "run", name = "Run", startDate = LocalDate.of(2026, 1, 1))

    private fun viewModel(
        habits: List<Habit> = listOf(read, run),
        completions: List<Completion> = emptyList(),
        repository: FakeHabitRepository = FakeHabitRepository(habits, completions),
    ) = repository to
        WeekViewModel(
            getHabitsForWeek = GetHabitsForWeek(repository, clock),
            toggleCompletion = ToggleCompletion(repository, clock),
            clock = clock,
        )

    private suspend fun content(viewModel: WeekViewModel): WeekUiState.Content {
        var state: WeekUiState = WeekUiState.Loading
        viewModel.uiState.test { state = awaitItem() }
        return state as? WeekUiState.Content
            ?: throw AssertionError("expected Content, got $state")
    }

    // --- which week is showing ---------------------------------------------------------------

    @Test
    fun itOpensOnTheWeekContainingToday() =
        runTest {
            val (_, viewModel) = viewModel()

            val state = content(viewModel)

            // Monday-based (`docs/design-spec.md` §3), so a Wednesday resolves back to the 24th.
            assertEquals(thisMonday, state.weekStart)
            assertEquals(LocalDate.of(2026, 8, 30), state.weekEndInclusive)
            assertTrue(state.isCurrentWeek)
        }

    @Test
    fun theInitialValueIsLoadingRatherThanAnEmptyWeek() =
        runTest {
            // ⭐ Starting at Empty would flash "you have no habits" on every cold start, which is a
            // sentence about the user's data being absent rather than about it not having arrived yet.
            val (_, viewModel) = viewModel()

            assertEquals(WeekUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun everyHabitGetsSevenCells() =
        runTest {
            val (_, viewModel) = viewModel()

            val state = content(viewModel)

            assertEquals(2, state.habits.size)
            state.habits.forEach { period -> assertEquals(7, period.cells.size) }
        }

    @Test
    fun todaySitsWhereTheCalendarSaysItDoes() =
        runTest {
            val (_, viewModel) = viewModel()

            val state = content(viewModel)

            // Wednesday is the third day of a Monday-based week, so index 2.
            state.habits.forEach { period -> assertEquals(2, period.todayIndex) }
        }

    // --- paging -------------------------------------------------------------------------------

    @Test
    fun previousWeekStepsBackSevenDaysAndIsNoLongerTheCurrentWeek() =
        runTest {
            val (_, viewModel) = viewModel()

            viewModel.onEvent(WeekEvent.PreviousWeekPressed)
            val state = content(viewModel)

            assertEquals(LocalDate.of(2026, 8, 17), state.weekStart)
            assertFalse(state.isCurrentWeek)
        }

    @Test
    fun nextWeekStepsForwardSevenDays() =
        runTest {
            val (_, viewModel) = viewModel()

            viewModel.onEvent(WeekEvent.NextWeekPressed)
            val state = content(viewModel)

            assertEquals(LocalDate.of(2026, 8, 31), state.weekStart)
            assertFalse(state.isCurrentWeek)
        }

    @Test
    fun pagingAwayAndBackLandsExactlyWhereItStarted() =
        runTest {
            // The property that keeps `‹ ›` honest: stepping is reversible, so a user cannot end up on
            // a Tuesday-based "week" after enough taps. `startOfWeek` is only applied at the two entry
            // points, so drift — if there were any — would only show up after repeated stepping.
            val (_, viewModel) = viewModel()

            repeat(5) { viewModel.onEvent(WeekEvent.PreviousWeekPressed) }
            repeat(5) { viewModel.onEvent(WeekEvent.NextWeekPressed) }

            assertEquals(thisMonday, content(viewModel).weekStart)
        }

    @Test
    fun pagingAcrossAYearBoundaryDoesNotDrift() =
        runTest {
            val (_, viewModel) = viewModel()

            // 24 Aug 2026 back 40 weeks is 18 Nov 2025 — through a year end, and still a Monday.
            repeat(40) { viewModel.onEvent(WeekEvent.PreviousWeekPressed) }
            val state = content(viewModel)

            assertEquals(LocalDate.of(2025, 11, 17), state.weekStart)
            assertEquals(DayOfWeek.MONDAY, state.weekStart.dayOfWeek)
        }

    @Test
    fun thisWeekJumpsBackFromWhereverThePagingLeftYou() =
        runTest {
            // ⭐ The affordance the prototype does not have (`WeekEvent.ThisWeekPressed`). Without it a
            // user who paged away has to count their way back.
            val (_, viewModel) = viewModel()
            repeat(9) { viewModel.onEvent(WeekEvent.PreviousWeekPressed) }

            viewModel.onEvent(WeekEvent.ThisWeekPressed)
            val state = content(viewModel)

            assertEquals(thisMonday, state.weekStart)
            assertTrue(state.isCurrentWeek)
        }

    @Test
    fun aPastWeekIsAllPastDaysRatherThanFutureOnes() =
        runTest {
            val (_, viewModel) = viewModel()

            viewModel.onEvent(WeekEvent.PreviousWeekPressed)
            val state = content(viewModel)

            val states =
                state.habits
                    .first()
                    .cells
                    .filterIsInstance<PeriodCell.Day>()
                    .map { it.state }
            assertTrue("expected no FUTURE days in a past week, got $states", states.none { it == DayState.FUTURE })
        }

    // --- empty --------------------------------------------------------------------------------

    @Test
    fun noActiveHabitsIsEmpty() =
        runTest {
            val (_, viewModel) = viewModel(habits = emptyList())

            viewModel.uiState.test { assertEquals(WeekUiState.Empty, awaitItem()) }
        }

    @Test
    fun emptyDoesNotDependOnWhichWeekIsShowing() =
        runTest {
            // ⭐ The claim `WeekUiState.Empty` is built on: habits are global, not per-week, so paging
            // can never turn Empty into Content. If this ever fails, Empty needs to carry a week and
            // M6.8's copy has to change with it.
            val (_, viewModel) = viewModel(habits = emptyList())

            repeat(3) { viewModel.onEvent(WeekEvent.PreviousWeekPressed) }

            viewModel.uiState.test { assertEquals(WeekUiState.Empty, awaitItem()) }
        }

    @Test
    fun aWeekEntirelyBeforeAHabitExistedIsStillContent() =
        runTest {
            // Not Empty — the habit exists, it just had not started. The cells say BEFORE_START, which
            // `DayState` is explicit is "we have no record", not "the user failed".
            val (_, viewModel) = viewModel(habits = listOf(testHabit(startDate = today)))

            repeat(2) { viewModel.onEvent(WeekEvent.PreviousWeekPressed) }
            val state = content(viewModel)

            val states =
                state.habits
                    .first()
                    .cells
                    .filterIsInstance<PeriodCell.Day>()
                    .map { it.state }
            assertTrue("expected every cell BEFORE_START, got $states", states.all { it == DayState.BEFORE_START })
        }

    // --- check-off ----------------------------------------------------------------------------

    @Test
    fun togglingADayWritesTheWantedValueRatherThanAFlip() =
        runTest {
            val (repository, viewModel) = viewModel()
            content(viewModel)

            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = HabitId("read"), date = today, completed = true),
            )

            assertEquals(
                listOf(FakeHabitRepository.Write(HabitId("read"), today, true)),
                repository.writes,
            )
        }

    @Test
    fun unTickingWritesFalseRatherThanDeleting() =
        runTest {
            val (repository, viewModel) = viewModel(completions = listOf(Completion(HabitId("read"), today)))
            content(viewModel)

            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = HabitId("read"), date = today, completed = false),
            )

            assertEquals(
                listOf(FakeHabitRepository.Write(HabitId("read"), today, false)),
                repository.writes,
            )
        }

    @Test
    fun aCheckOffShowsUpInTheStateWithoutAnyNetwork() =
        runTest {
            // ⭐ `CLAUDE.md` §1 in one assertion: the write lands in Room and the screen re-renders from
            // Room. Nothing here is online, and nothing awaited a sync.
            val (_, viewModel) = viewModel()

            viewModel.uiState.test {
                // ⚠ The first item is already Content, not Loading. `MainDispatcherRule` uses an
                // `UnconfinedTestDispatcher`, so the upstream has run by the time Turbine subscribes
                // and `StateFlow` conflates the initial value away. That Loading comes first at all
                // is asserted in `theInitialValueIsLoadingRatherThanAnEmptyWeek`, which reads
                // `.value` before anything subscribes — the only place the claim is observable.
                val before = awaitItem() as WeekUiState.Content
                assertEquals(DayState.INCOMPLETE, (before.habits.first().cells[2] as PeriodCell.Day).state)

                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = today, completed = true),
                )

                val after = awaitItem() as WeekUiState.Content
                assertEquals(DayState.COMPLETED, (after.habits.first().cells[2] as PeriodCell.Day).state)
            }
        }

    @Test
    fun aFutureDayIsRefusedByTheDomainAndNothingIsWritten() =
        runTest {
            // The guard lives in `ToggleCompletion`, not here, and this pins that the ViewModel does not
            // route around it. ⚠ The refusal currently goes nowhere on screen — M6.7 owns making the
            // pill untappable, M6.6 owns the message.
            val (repository, viewModel) = viewModel()
            content(viewModel)

            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = HabitId("read"), date = today.plusDays(1), completed = true),
            )

            assertEquals(emptyList<FakeHabitRepository.Write>(), repository.writes)
        }
}

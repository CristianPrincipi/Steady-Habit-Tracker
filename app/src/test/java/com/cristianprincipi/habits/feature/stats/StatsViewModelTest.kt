package com.cristianprincipi.habits.feature.stats

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.FakeSyncStatusRepository
import com.cristianprincipi.habits.core.testing.FakeTodayFlow
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.core.testing.MutableClock
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.PeriodCell
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.usecase.CalculateStreak
import com.cristianprincipi.habits.domain.usecase.GetCompletionRate
import com.cristianprincipi.habits.domain.usecase.GetHabitStats
import com.cristianprincipi.habits.domain.usecase.GetHabits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the Stats screen is told.
 *
 * ⭐⭐ **This file is where M6's un-ticked Definition-of-Done clause is half discharged.** M6 asked
 * that "the streak updates" and nothing in M6 renders a streak — the Week card's second line is a
 * weekly count — so the clause was carried here (ADR-075, `Plan.md` M6 DoD). It has two halves: a
 * streak that *changes in the state* when a day is checked off, which
 * [theStreakGrowsTheMomentTheDayIsCheckedOff] proves against a real `CalculateStreak` and a real
 * repository write, and somebody *seeing* it change, which needs M9.4's tiles and is not this
 * file's to claim.
 *
 * The use cases are the real ones, as in `YearViewModelTest`: a fake `GetHabitStats` would let this
 * file pass while the arithmetic it is asserting on was wrong.
 *
 * The clock is fixed to **Wednesday 26 August 2026**.
 */
class StatsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 8, 26)

    private val clock = MutableClock(today.atStartOfDay(ZoneOffset.UTC).toInstant())
    private val todayFlow = FakeTodayFlow(today)

    /** Both, always — the ticker in production reads the clock it reports on, so they cannot differ. */
    private fun rollOverTo(date: LocalDate) {
        clock.setTo(date)
        todayFlow.setTo(date)
    }

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(lastSyncedAt = null))
    private val syncStatus = FakeSyncStatusRepository(status)

    private val read = testHabit(id = "read", name = "Read", startDate = LocalDate.of(2024, 1, 1))
    private val diet = testHabit(id = "diet", name = "Diet", startDate = LocalDate.of(2024, 1, 1))

    private fun viewModel(repository: FakeHabitRepository): StatsViewModel =
        StatsViewModel(
            getHabits = GetHabits(repository),
            getHabitStats =
                GetHabitStats(
                    habits = repository,
                    calculateStreak = CalculateStreak(clock),
                    completionRate = GetCompletionRate(clock),
                    clock = clock,
                ),
            syncStatus = syncStatus,
            today = todayFlow,
        )

    private fun repository(
        habits: List<Habit> = listOf(read, diet),
        completions: List<Completion> = emptyList(),
    ) = FakeHabitRepository(habits, completions)

    /** [days] consecutive completions ending on [endingOn]. */
    private fun daysEnding(
        habit: Habit,
        days: Int,
        endingOn: LocalDate,
    ): List<Completion> = (0 until days).map { Completion(habit.id, endingOn.minusDays(it.toLong())) }

    @Test
    fun theInitialValueIsLoadingRatherThanAnEmptyScreen() =
        runTest {
            // Starting at Empty would flash "you have no habits" on every cold start, which is a
            // sentence about the user's data being absent rather than about it not having arrived.
            assertEquals(StatsUiState.Loading, viewModel(repository()).uiState.value)
        }

    @Test
    fun itOpensOnTheFirstHabit() =
        runTest {
            // The design's own default — `docs/design-spec.md` §5.1, "defaults to habits[0]".
            viewModel(repository()).uiState.test {
                val state = awaitItem() as StatsUiState.Content
                assertEquals(read.id, state.selectedId)
                assertEquals(listOf(read, diet), state.habits)
            }
        }

    @Test
    fun selectingAHabitSwitchesTheFiguresUnderIt() =
        runTest {
            // Different lengths, so a ViewModel that changed the pill but kept the old figures —
            // the mutant where the selection is read for the label and not for the query — fails.
            val completions =
                daysEnding(read, days = 3, endingOn = today) + daysEnding(diet, days = 7, endingOn = today)
            val viewModel = viewModel(repository(completions = completions))

            viewModel.uiState.test {
                assertEquals(3, (awaitItem() as StatsUiState.Content).selected.streak.current)

                viewModel.onEvent(StatsEvent.HabitSelected(diet.id))

                val switched = awaitItem() as StatsUiState.Content
                assertEquals(diet.id, switched.selectedId)
                assertEquals(7, switched.selected.streak.current)
            }
        }

    @Test
    fun theStreakGrowsTheMomentTheDayIsCheckedOff() =
        runTest {
            // ⭐⭐ M6's carried clause, in its buildable half.
            //
            // Five days ending yesterday, so the streak is alive but today is still open — which is
            // what every user sees when they open the app in the morning, and the case
            // `CalculateStreak` counts back from yesterday for. Checking today off must take it to
            // six, in the state, with nothing re-subscribing or re-querying by hand.
            val repository = repository(completions = daysEnding(read, days = 5, endingOn = today.minusDays(1)))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                val before = (awaitItem() as StatsUiState.Content).selected
                assertEquals(5, before.streak.current)
                assertEquals(5, before.rate.completedDays)

                repository.setCompleted(read.id, today, completed = true)

                val after = (awaitItem() as StatsUiState.Content).selected
                assertEquals(6, after.streak.current)
                assertEquals(6, after.rate.completedDays)
            }
        }

    @Test
    fun undoingTodayTakesTheStreakBackDown() =
        runTest {
            // The other direction, because a streak that only ever grows is a streak nobody has
            // tested. It drops to five rather than to zero: the days behind today still stand.
            val repository = repository(completions = daysEnding(read, days = 6, endingOn = today))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                assertEquals(6, (awaitItem() as StatsUiState.Content).selected.streak.current)

                repository.setCompleted(read.id, today, completed = false)

                assertEquals(5, (awaitItem() as StatsUiState.Content).selected.streak.current)
            }
        }

    @Test
    fun aHabitThatLeavesTheSetFallsBackToTheFirstAndComesBackToItsOwnFigures() =
        runTest {
            // ⭐ ADR-081: hiding is silent and global, so the chosen pill can simply stop existing
            // between two emissions. The ViewModel holds the id the user tapped and re-resolves it
            // every time — never the habit — so the fallback happens on its own and reverses on its
            // own. A `MutableStateFlow<Habit>` would go on drawing a habit the rest of the app has
            // hidden, with figures nothing is querying for any more.
            val repository = repository(completions = daysEnding(diet, days = 4, endingOn = today))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                viewModel.onEvent(StatsEvent.HabitSelected(diet.id))
                assertEquals(diet.id, (awaitItem() as StatsUiState.Content).selectedId)

                repository.setHabits(listOf(read))
                assertEquals(read.id, (awaitItem() as StatsUiState.Content).selectedId)

                repository.setHabits(listOf(read, diet))
                val restored = awaitItem() as StatsUiState.Content
                assertEquals(diet.id, restored.selectedId)
                assertEquals(4, restored.selected.streak.current)
            }
        }

    @Test
    fun theFiguresFollowTodayAcrossMidnight() =
        runTest {
            // ⭐⭐ The mutant this exists for: dropping `TodayFlow` from the ViewModel. Nothing would
            // look wrong — `GetHabitStats` reads the clock once, when it is invoked — but every
            // figure on this screen is measured against that reading. A streak of three, left on
            // screen overnight with the day missed, is a lie the user would act on.
            val repository = repository(completions = daysEnding(read, days = 3, endingOn = today.minusDays(1)))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                assertEquals(3, (awaitItem() as StatsUiState.Content).selected.streak.current)

                rollOverTo(today.plusDays(1))

                // Yesterday is now the 26th, which was never done, so the run is broken and the
                // best streak is all that survives of it.
                val overnight = (awaitItem() as StatsUiState.Content).selected
                assertEquals(0, overnight.streak.current)
                assertEquals(3, overnight.streak.best)
            }
        }

    @Test
    fun theYearCardGetsATrailing365EndingToday() =
        runTest {
            // The window the design's year overview draws (`docs/design-spec.md` §5.2), and the same
            // one the Year tab uses — one grid rendered at two sizes must not be two windows.
            viewModel(repository()).uiState.test {
                val cells = (awaitItem() as StatsUiState.Content).selected.period.cells
                val realDays = cells.filterIsInstance<PeriodCell.Day>()

                assertEquals(365, realDays.size)
                assertEquals(today, realDays.last().date)
            }
        }

    @Test
    fun daysBeforeTheHabitStartedAreDrawnAsSuch() =
        runTest {
            // The Year grid is the one place the design separates "before this habit existed" from
            // an ordinary miss — a year is long enough for a start date to be a real feature of the
            // picture (`YearDotGrid.yearDayColor`).
            val young = testHabit(id = "young", startDate = today.minusDays(9))
            viewModel(repository(habits = listOf(young))).uiState.test {
                val cells = (awaitItem() as StatsUiState.Content).selected.period.cells
                val states = cells.filterIsInstance<PeriodCell.Day>().map { it.state }

                assertEquals(365 - 10, states.count { it == DayState.BEFORE_START })
                assertEquals(10, states.count { it == DayState.INCOMPLETE })
            }
        }

    @Test
    fun aCompletionOlderThanTheInferredStartDateIsStillDrawnAsKept() =
        runTest {
            // ⭐⭐ The mutant: query `startDate..today` instead of reaching back to the window start.
            // `startDate` is *inferred* from the earliest day synced, not read from Notion, so a
            // completion older than it is possible — and one the query missed would be drawn
            // BEFORE_START here while the Year tab, which queries the whole window, drew the same
            // day COMPLETED. One grid, two screens, two answers.
            val young = testHabit(id = "young", startDate = today.minusDays(9))
            val older = Completion(young.id, today.minusDays(100))
            viewModel(repository(habits = listOf(young), completions = listOf(older))).uiState.test {
                val cells = (awaitItem() as StatsUiState.Content).selected.period.cells
                val day = cells.filterIsInstance<PeriodCell.Day>().single { it.date == older.date }

                assertEquals(DayState.COMPLETED, day.state)
            }
        }

    @Test
    fun noHabitsAtAllIsEmptyWithTheReasonSyncStatusGives() =
        runTest {
            val viewModel = viewModel(repository(habits = emptyList()))

            viewModel.uiState.test {
                assertEquals(StatsUiState.Empty(HabitsEmptyReason.SyncPending), awaitItem())

                status.value = SyncStatus.Failed(SyncFailure.Offline, lastSyncedAt = null)

                assertEquals(StatsUiState.Empty(HabitsEmptyReason.SyncFailed), awaitItem())
            }
        }

    @Test
    fun aHabitWithNoHistoryIsContentRatherThanEmpty() =
        runTest {
            // The distinction StatsUiState.Empty exists to make: zero completions is a streak of
            // zero and a rate of 0%, which are true things to draw. Empty is about the habit set.
            viewModel(repository(habits = listOf(read))).uiState.test {
                val state = awaitItem() as StatsUiState.Content
                assertEquals(0, state.selected.streak.best)
                assertEquals(0, state.selected.rate.completedDays)
            }
        }
}

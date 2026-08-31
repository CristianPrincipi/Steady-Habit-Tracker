package com.cristianprincipi.habits.feature.history

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
import com.cristianprincipi.habits.domain.usecase.GetHabitsForMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Which month the Month screen shows, and what its two arrows do to it.
 *
 * The clock is fixed to **Wednesday 26 August 2026** — the same day `WeekViewModelTest` uses, and
 * mid-month for the same kind of reason: a 1st or a 31st would let an off-by-one in the window maths
 * pass by landing on a boundary anyway, and mid-month is the only place where one visible month
 * holds both past and future days.
 *
 * August 2026 is also the month `MonthDotGrid`'s preview is drawn from: 31 days, starting on a
 * Saturday, so the window opens with **five** `Padding` cells.
 *
 * How a cell becomes `COMPLETED` is `GetHabitsForPeriodTest`'s subject and is not re-asserted here.
 */
class MonthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 8, 26)
    private val thisMonth = YearMonth.of(2026, 8)

    private val clock = MutableClock(today.atStartOfDay(ZoneOffset.UTC).toInstant())
    private val todayFlow = FakeTodayFlow(today)

    /** Both, always — the ticker in production reads the clock it reports on, so they cannot differ. */
    private fun rollOverTo(date: LocalDate) {
        clock.setTo(date)
        todayFlow.setTo(date)
    }

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(lastSyncedAt = null))
    private val syncStatus = FakeSyncStatusRepository(status)

    private val read = testHabit(id = "read", name = "Read", startDate = LocalDate.of(2026, 1, 1))
    private val run = testHabit(id = "run", name = "Run", startDate = LocalDate.of(2026, 1, 1))

    private fun viewModel(
        habits: List<Habit> = listOf(read, run),
        completions: List<Completion> = emptyList(),
    ) = MonthViewModel(
        getHabitsForMonth = GetHabitsForMonth(FakeHabitRepository(habits, completions), clock),
        syncStatus = syncStatus,
        today = todayFlow,
        clock = clock,
    )

    private suspend fun content(viewModel: MonthViewModel): MonthUiState.Content {
        var state: MonthUiState = viewModel.uiState.value
        viewModel.uiState.test { state = awaitItem() }
        return state as? MonthUiState.Content ?: throw AssertionError("expected Content, got $state")
    }

    // --- which month is showing ---------------------------------------------------------------

    @Test
    fun itOpensOnTheMonthContainingToday() =
        runTest {
            val state = content(viewModel())

            assertEquals(thisMonth, state.month)
        }

    @Test
    fun evenLoadingKnowsWhichMonthItIs() =
        runTest {
            // ⭐ The reason `month` sits on the interface rather than inside Content: this screen's
            // header title IS the month name, so a state without one is a screen with no title.
            // Week can start at a titleless Loading because its title is the word "Week".
            assertEquals(MonthUiState.Loading(thisMonth), viewModel().uiState.value)
        }

    @Test
    fun everyHabitGetsOneCellPerDayPlusTheLeadingBlanks() =
        runTest {
            val state = content(viewModel())

            assertEquals(2, state.habits.size)
            state.habits.forEach { period ->
                // August 2026 starts on a Saturday: five blanks push the 1st into the sixth column.
                assertEquals(BLANKS_IN_AUGUST + DAYS_IN_AUGUST, period.cells.size)
                assertEquals(DAYS_IN_AUGUST, period.cells.count { it is PeriodCell.Day })
                assertEquals(
                    BLANKS_IN_AUGUST,
                    period.cells.take(BLANKS_IN_AUGUST).count { it is PeriodCell.Padding },
                )
            }
        }

    @Test
    fun daysAfterTodayAreFutureAndDaysBeforeItAreNot() =
        runTest {
            val cells =
                content(viewModel())
                    .habits
                    .first()
                    .cells
                    .filterIsInstance<PeriodCell.Day>()

            assertEquals(DayState.INCOMPLETE, cells.first { it.date == LocalDate.of(2026, 8, 25) }.state)
            assertEquals(DayState.INCOMPLETE, cells.first { it.date == today }.state)
            assertEquals(DayState.FUTURE, cells.first { it.date == LocalDate.of(2026, 8, 27) }.state)
        }

    // --- paging -------------------------------------------------------------------------------

    @Test
    fun theArrowsStepOneCalendarMonth() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(MonthEvent.PreviousMonthPressed)
            assertEquals(YearMonth.of(2026, 7), content(viewModel).month)

            viewModel.onEvent(MonthEvent.NextMonthPressed)
            viewModel.onEvent(MonthEvent.NextMonthPressed)
            assertEquals(YearMonth.of(2026, 9), content(viewModel).month)
        }

    @Test
    fun aShortMonthGetsItsOwnLengthRatherThanTheOneBeforeIt() =
        runTest {
            // February 2027 is 28 days and starts on a Monday, so it is also the one month in reach
            // with NO leading blanks — a window built by padding a fixed 31 would show both faults.
            val viewModel = viewModel()

            repeat(MONTHS_TO_FEBRUARY) { viewModel.onEvent(MonthEvent.NextMonthPressed) }

            val period = content(viewModel).habits.first()
            assertEquals(YearMonth.of(2027, 2), content(viewModel).month)
            assertEquals(DAYS_IN_FEBRUARY_2027, period.cells.size)
            assertTrue(
                "expected no leading blanks in February 2027",
                period.cells.none { it is PeriodCell.Padding },
            )
        }

    @Test
    fun steppingBackFromJanuaryRollsTheYear() =
        runTest {
            // `docs/design-spec.md` §2's "rolling the year at the boundaries", in both directions.
            val viewModel = viewModel()

            repeat(MONTHS_TO_JANUARY) { viewModel.onEvent(MonthEvent.PreviousMonthPressed) }
            assertEquals(YearMonth.of(2026, 1), content(viewModel).month)

            viewModel.onEvent(MonthEvent.PreviousMonthPressed)
            assertEquals(YearMonth.of(2025, 12), content(viewModel).month)

            repeat(MONTHS_BACK_UP_TO_JANUARY) { viewModel.onEvent(MonthEvent.NextMonthPressed) }
            assertEquals(YearMonth.of(2027, 1), content(viewModel).month)
        }

    // --- the anchor, and midnight -------------------------------------------------------------

    @Test
    fun aScreenLeftOpenAcrossAMonthBoundaryMovesToTheNewMonth() =
        runTest {
            val viewModel = viewModel()
            assertEquals(thisMonth, content(viewModel).month)

            rollOverTo(LocalDate.of(2026, 9, 1))

            assertEquals(YearMonth.of(2026, 9), content(viewModel).month)
        }

    @Test
    fun pagingAwayAndBackLeavesTheScreenFollowingTodayAgain() =
        runTest {
            // ⭐⭐ The mutant this catches: `moveMonths` storing the current month instead of null.
            // Nothing on screen would differ — until the next month boundary, where a screen that
            // had been paged away and back would stay stuck on the old month while an untouched one
            // moved on. Two states that render identically must behave identically.
            val viewModel = viewModel()

            viewModel.onEvent(MonthEvent.PreviousMonthPressed)
            viewModel.onEvent(MonthEvent.NextMonthPressed)
            assertEquals(thisMonth, content(viewModel).month)

            rollOverTo(LocalDate.of(2026, 9, 1))

            assertEquals(YearMonth.of(2026, 9), content(viewModel).month)
        }

    @Test
    fun aMonthTheUserPagedToIsNotMovedByMidnight() =
        runTest {
            // The other half of the same rule: a deliberate position is held, so the user is not
            // moved off the month they are reading because the date changed while they read it.
            val viewModel = viewModel()

            viewModel.onEvent(MonthEvent.PreviousMonthPressed)
            assertEquals(YearMonth.of(2026, 7), content(viewModel).month)

            rollOverTo(LocalDate.of(2026, 9, 1))

            assertEquals(YearMonth.of(2026, 7), content(viewModel).month)
        }

    // --- empty --------------------------------------------------------------------------------

    @Test
    fun noHabitsIsEmptyAndKeepsTheMonthForTheTitle() =
        runTest {
            val viewModel = viewModel(habits = emptyList())

            viewModel.uiState.test {
                assertEquals(MonthUiState.Empty(thisMonth, HabitsEmptyReason.SyncPending), awaitItem())
            }
        }

    @Test
    fun emptyDoesNotDependOnWhichMonthIsShowing() =
        runTest {
            // Habits are global, not per-month (ADR-010), so paging can never turn Empty into
            // Content. It is also why the header hides the sub-nav here: the arrows would move a
            // label above a screen that cannot change.
            val viewModel = viewModel(habits = emptyList())

            repeat(THREE_MONTHS_BACK) { viewModel.onEvent(MonthEvent.PreviousMonthPressed) }

            viewModel.uiState.test {
                assertEquals(
                    MonthUiState.Empty(YearMonth.of(2026, 5), HabitsEmptyReason.SyncPending),
                    awaitItem(),
                )
            }
        }

    @Test
    fun aFailedFirstImportSaysSoHereTooRatherThanWaitingForEver() =
        runTest {
            val viewModel = viewModel(habits = emptyList())
            status.value = SyncStatus.Failed(SyncFailure.DatabaseNotShared, lastSyncedAt = null)

            viewModel.uiState.test {
                assertEquals(MonthUiState.Empty(thisMonth, HabitsEmptyReason.SyncFailed), awaitItem())
            }
        }

    @Test
    fun aFailedSyncChangesNothingOnScreenWhileThereAreHabitsToDraw() =
        runTest {
            // `CLAUDE.md` §3.5: sync is invisible until it fails, and a failure with habits in Room
            // has nothing to say on a history screen — `SyncStatusScreen` owns that sentence.
            val viewModel = viewModel()

            viewModel.uiState.test {
                val before = awaitItem()
                assertTrue("expected Content, got $before", before is MonthUiState.Content)

                status.value = SyncStatus.Failed(SyncFailure.Offline, lastSyncedAt = null)

                expectNoEvents()
            }
        }

    private companion object {
        const val DAYS_IN_AUGUST = 31
        const val BLANKS_IN_AUGUST = 5
        const val DAYS_IN_FEBRUARY_2027 = 28
        const val MONTHS_TO_FEBRUARY = 6
        const val MONTHS_TO_JANUARY = 7
        const val MONTHS_BACK_UP_TO_JANUARY = 13
        const val THREE_MONTHS_BACK = 3
    }
}

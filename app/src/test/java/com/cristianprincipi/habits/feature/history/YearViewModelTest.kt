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
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.PeriodCell
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.usecase.GetCompletionRate
import com.cristianprincipi.habits.domain.usecase.GetHabitsForYear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the Year screen is told.
 *
 * Much thinner than [MonthViewModelTest], and the reason is the window: `GetHabitsForYear` takes no
 * date, because the design gives this view no paging. There is no anchor to normalise, no snapshot
 * to pair with a query and no event to fire — so what is left to get wrong is **the day**.
 *
 * The clock is fixed to **Wednesday 26 August 2026**.
 */
class YearViewModelTest {
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

    private val read = testHabit(id = "read", name = "Read", startDate = LocalDate.of(2020, 1, 1))
    private val young = testHabit(id = "young", name = "Young", startDate = today.minusDays(9))

    private fun viewModel(
        habits: List<Habit> = listOf(read),
        completions: List<Completion> = emptyList(),
    ): YearViewModel {
        val repository = FakeHabitRepository(habits, completions)
        return YearViewModel(
            getHabitsForYear = GetHabitsForYear(repository, GetCompletionRate(clock), clock),
            syncStatus = syncStatus,
            today = todayFlow,
        )
    }

    private suspend fun content(viewModel: YearViewModel): YearUiState.Content {
        var state: YearUiState = viewModel.uiState.value
        viewModel.uiState.test { state = awaitItem() }
        return state as? YearUiState.Content ?: throw AssertionError("expected Content, got $state")
    }

    /** The last real day in the window — the one the design puts today on. */
    private fun lastDay(state: YearUiState.Content): LocalDate =
        state.habits
            .first()
            .period
            .cells
            .filterIsInstance<PeriodCell.Day>()
            .last()
            .date

    @Test
    fun theInitialValueIsLoadingRatherThanAnEmptyYear() =
        runTest {
            // Starting at Empty would flash "you have no habits" on every cold start, which is a
            // sentence about the user's data being absent rather than about it not having arrived.
            assertEquals(YearUiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun everyHabitGetsATrailingYearEndingToday() =
        runTest {
            val state = content(viewModel())

            assertEquals(
                DAYS_IN_YEAR,
                state.habits
                    .first()
                    .period.cells
                    .count { it is PeriodCell.Day },
            )
            assertEquals(today, lastDay(state))
        }

    @Test
    fun theRateTravelsWithTheGridRatherThanBeingMeasuredSeparately() =
        runTest {
            // ⭐ A habit ten days old, kept on six of them. The grid is 365 cells and the rate is
            // over **ten** — which is the whole reason the card prints the two numbers behind the
            // percentage rather than a count out of 365 (ADR-085's slot, one card along).
            val kept = (0 until 6).map { Completion(young.id, today.minusDays(it.toLong())) }
            val state = content(viewModel(habits = listOf(young), completions = kept))

            val year = state.habits.single()
            assertEquals(DAYS_IN_YEAR, year.period.cells.count { it is PeriodCell.Day })
            assertEquals(6, year.rate.completedDays)
            assertEquals(DAYS_YOUNG, year.rate.eligibleDays)
            assertEquals(SIXTY_PERCENT, year.rate.percent)
        }

    @Test
    fun theWindowFollowsTodayAcrossMidnight() =
        runTest {
            // ⭐⭐ The mutant this exists for: dropping `TodayFlow` from the ViewModel. Nothing would
            // look wrong — `GetHabitsForYear` reads the clock once, when it is invoked — but the
            // window would then end on the day the screen was *opened*, and this is the tab most
            // likely to be left in the background, because it is the one with nothing to do on it.
            // A grid whose last column quietly stops being today is not something anyone notices in
            // a picture made of 4dp squares.
            val viewModel = viewModel()
            assertEquals(today, lastDay(content(viewModel)))

            rollOverTo(today.plusDays(1))

            assertEquals(today.plusDays(1), lastDay(content(viewModel)))
        }

    @Test
    fun noHabitsIsEmpty() =
        runTest {
            val viewModel = viewModel(habits = emptyList())

            viewModel.uiState.test {
                assertEquals(YearUiState.Empty(HabitsEmptyReason.SyncPending), awaitItem())
            }
        }

    @Test
    fun aFailedFirstImportSaysSoHereTooRatherThanWaitingForEver() =
        runTest {
            val viewModel = viewModel(habits = emptyList())
            status.value = SyncStatus.Failed(SyncFailure.DatabaseNotShared, lastSyncedAt = null)

            viewModel.uiState.test {
                assertEquals(YearUiState.Empty(HabitsEmptyReason.SyncFailed), awaitItem())
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
                assertTrue("expected Content, got $before", before is YearUiState.Content)

                status.value = SyncStatus.Failed(SyncFailure.Offline, lastSyncedAt = null)

                expectNoEvents()
            }
        }

    private companion object {
        const val DAYS_IN_YEAR = 365

        /** Started nine days before today, and the window is inclusive at both ends. */
        const val DAYS_YOUNG = 10
        const val SIXTY_PERCENT = 60
    }
}

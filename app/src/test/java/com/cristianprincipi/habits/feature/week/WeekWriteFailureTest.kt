package com.cristianprincipi.habits.feature.week

import android.database.sqlite.SQLiteFullException
import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.FakeMeasureRepository
import com.cristianprincipi.habits.core.testing.FakeSyncStatusRepository
import com.cristianprincipi.habits.core.testing.FakeTodayFlow
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.core.testing.MutableClock
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.GetMeasuresForWeek
import com.cristianprincipi.habits.domain.usecase.SetMeasureReading
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * ⚠⚠ **What the Week screen says when the device will not write — M13.12.**
 *
 * Split from `WeekViewModelTest`, which is about which week is showing and what the four gestures
 * do. This is about the one thing none of those tests could reach: a write that **fails**.
 *
 * ### Why this file exists at all
 *
 * Every write here was a bare `viewModelScope.launch { … }`, and `viewModelScope`'s `SupervisorJob`
 * stops a failed child cancelling its siblings without stopping the *exception* — so a Room failure
 * reached the thread's uncaught handler and killed the app, on the interaction `CLAUDE.md` §1
 * promises "succeeds immediately and locally… never lost".
 *
 * ⚠ **And the obvious fix would have been worse than the crash.** The pills are drawn from Room,
 * observed, with no optimistic overlay: catch the failure and say nothing, and the pill stays
 * exactly where it was — which is what a finger that missed looks like. So every test below asserts
 * that something was **said**, not merely that nothing blew up.
 *
 * Two of the screen's rules are deliberately inverted for failure, and each has a test here: a
 * failed write speaks **on today**, where a successful one is silent, and a failed **undo** speaks,
 * where a successful one is silent.
 */
class WeekWriteFailureTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Wednesday, as `WeekViewModelTest` fixes it — the week runs Mon 24 – Sun 30 Aug 2026. */
    private val today = LocalDate.of(2026, 8, 26)
    private val thisMonday = LocalDate.of(2026, 8, 24)

    private val clock = MutableClock(today.atStartOfDay(ZoneOffset.UTC).toInstant())
    private val todayFlow = FakeTodayFlow(today)
    private val syncStatus = FakeSyncStatusRepository(MutableStateFlow(SyncStatus.Idle(lastSyncedAt = null)))

    private val read = testHabit(id = "read", name = "Read", startDate = LocalDate.of(2026, 1, 1))
    private val run = testHabit(id = "run", name = "Run", startDate = LocalDate.of(2026, 1, 1))

    private fun viewModel(): Pair<FakeHabitRepository, WeekViewModel> {
        val repository = FakeHabitRepository(listOf(read, run), emptyList())
        return repository to
            WeekViewModel(
                getHabitsForWeek = GetHabitsForWeek(repository, clock),
                getMeasuresForWeek = GetMeasuresForWeek(FakeMeasureRepository(), clock),
                setMeasureReading = SetMeasureReading(FakeMeasureRepository(), clock),
                toggleCompletion = ToggleCompletion(repository, clock),
                syncStatus = syncStatus,
                today = todayFlow,
                clock = clock,
            )
    }

    /** Drains the first emission so the ViewModel is holding habits before a tap is sent. */
    private suspend fun content(viewModel: WeekViewModel) {
        viewModel.uiState.test { awaitItem() }
    }

    @Test
    fun aCheckOffThatCannotBeWrittenSaysSoRatherThanCrashing() =
        runTest {
            // ⭐⭐ **`CLAUDE.md` §1's promise, on the day the device breaks it.** Before M13.12 this
            // threw out of a bare `viewModelScope.launch` and took the app down. The half that the
            // crash was hiding is what makes a bare try/catch the wrong fix: the pill is drawn from
            // Room with no optimistic overlay, so a caught-and-ignored failure leaves it exactly
            // where it was — indistinguishable from a finger that missed.
            val (repository, viewModel) = viewModel()
            content(viewModel)
            repository.failWritesWith = SQLiteFullException("disk full")

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = thisMonday, completed = true),
                )

                val message = awaitItem() as WeekMessage.SaveFailed
                assertEquals("Read", message.habitName)
                assertEquals(thisMonday, message.date)
                assertTrue(message.completed)
            }
        }

    @Test
    fun aFailedCheckOffSpeaksEvenOnToday() =
        runTest {
            // ⚠⚠ **The inversion of M6.11, and the reason it is not an oversight.** A *successful*
            // tap on today is silent because the ring already points at that column and the pill
            // has just filled in. Neither is true of a write that did not happen: nothing moved, on
            // today's column exactly as on any other. The most important failure to report is the
            // one on the day the user is actually checking off.
            val (repository, viewModel) = viewModel()
            content(viewModel)
            repository.failWritesWith = SQLiteFullException("disk full")

            viewModel.messages.test {
                viewModel.onEvent(WeekEvent.DayToggled(habitId = HabitId("read"), date = today, completed = true))

                assertEquals("Read", (awaitItem() as WeekMessage.SaveFailed).habitName)
            }
        }

    @Test
    fun retryingAFailedCheckOffWritesTheSameThingAgain() =
        runTest {
            // The offer has to be worth pressing: the retry carries the write that failed, not a
            // flip of whatever the pill holds now.
            val (repository, viewModel) = viewModel()
            content(viewModel)
            repository.failWritesWith = SQLiteFullException("disk full")

            var failure: WeekMessage.SaveFailed? = null
            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = thisMonday, completed = true),
                )
                failure = awaitItem() as WeekMessage.SaveFailed
            }

            repository.failWritesWith = null
            viewModel.onEvent(failure!!.retry)

            assertEquals(
                listOf(
                    FakeHabitRepository.Write(HabitId("read"), thisMonday, true),
                    FakeHabitRepository.Write(HabitId("read"), thisMonday, true),
                ),
                repository.writes,
            )
        }

    @Test
    fun aFailedUndoRetriesAsAnUndoAndNotAsAFreshTap() =
        runTest {
            // ⚠ The loop `WeekEvent.UndoPressed` exists to prevent, reached the other way round. If
            // the retry were rebuilt as a `DayToggled`, pressing Retry on a failed undo would earn a
            // message offering to undo the undo — so the retry is the event that failed, carried.
            val (repository, viewModel) = viewModel()
            content(viewModel)
            repository.failWritesWith = SQLiteFullException("disk full")

            var failure: WeekMessage.SaveFailed? = null
            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.UndoPressed(habitId = HabitId("read"), date = thisMonday, completed = false),
                )
                failure = awaitItem() as WeekMessage.SaveFailed
            }

            assertTrue(failure!!.retry is WeekEvent.UndoPressed)

            repository.failWritesWith = null
            viewModel.messages.test {
                viewModel.onEvent(failure!!.retry)
                // A successful undo still says nothing, so the retry cannot start the loop.
                expectNoEvents()
            }
        }
}

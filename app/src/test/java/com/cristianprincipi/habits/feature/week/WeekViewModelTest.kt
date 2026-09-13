package com.cristianprincipi.habits.feature.week

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
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.PeriodCell
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
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
 * The clock is a [MutableClock] rather than a `Clock.fixed`, and every test that does not call
 * [rollOverTo] reads it as though it were fixed at [today]. The ones that do call it are M6.9's:
 * `isCurrentWeek` and `todayIndex` are emission-time facts, and what makes them correct is not that
 * they are recomputed but that **something re-emits when the day changes**.
 */
class WeekViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Wednesday. The week it belongs to runs Mon 24 Aug – Sun 30 Aug 2026. */
    private val today = LocalDate.of(2026, 8, 26)
    private val thisMonday = LocalDate.of(2026, 8, 24)

    /**
     * Movable rather than fixed, for M6.9's sake — see [rollOverTo]. Every test that does not call
     * that reads it exactly as a `Clock.fixed` at [today].
     */
    private val clock = MutableClock(today.atStartOfDay(ZoneOffset.UTC).toInstant())
    private val todayFlow = FakeTodayFlow(today)

    /**
     * Midnight, as the app actually experiences it.
     *
     * **Both, always.** In production the ticker reads the clock it reports on, so they cannot
     * disagree; a test that moved only the flow would be asserting against a state the app has no
     * way to reach, and one that moved only the clock would prove nothing, because nothing re-emits.
     */
    private fun rollOverTo(date: LocalDate) {
        clock.setTo(date)
        todayFlow.setTo(date)
    }

    /** Drives [WeekUiState.Empty]'s reason, and nothing else on this screen (M6.8). */
    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(lastSyncedAt = null))
    private val syncStatus = FakeSyncStatusRepository(status)

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
            syncStatus = syncStatus,
            today = todayFlow,
            clock = clock,
        )

    /** The first habit's cell for [date], as the screen would draw it. */
    private fun stateOn(
        state: WeekUiState,
        date: LocalDate,
    ): DayState =
        (state as WeekUiState.Content)
            .habits
            .first()
            .cells
            .filterIsInstance<PeriodCell.Day>()
            .first { cell -> cell.date == date }
            .state

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

            viewModel.uiState.test {
                assertEquals(WeekUiState.Empty(HabitsEmptyReason.SyncPending), awaitItem())
            }
        }

    @Test
    fun emptyDoesNotDependOnWhichWeekIsShowing() =
        runTest {
            // ⭐ The claim `WeekUiState.Empty` is built on: habits are global, not per-week, so paging
            // can never turn Empty into Content. If this ever fails, Empty needs to carry a week and
            // M6.8's copy has to change with it.
            val (_, viewModel) = viewModel(habits = emptyList())

            repeat(3) { viewModel.onEvent(WeekEvent.PreviousWeekPressed) }

            viewModel.uiState.test {
                assertEquals(WeekUiState.Empty(HabitsEmptyReason.SyncPending), awaitItem())
            }
        }

    // --- why the screen is empty (M6.8) -------------------------------------------------------

    @Test
    fun aRunningFirstImportReadsAsOnItsWayRatherThanAsAProblem() =
        runTest {
            val (_, viewModel) = viewModel(habits = emptyList())
            status.value = SyncStatus.Syncing(lastSyncedAt = null)

            viewModel.uiState.test {
                assertEquals(WeekUiState.Empty(HabitsEmptyReason.SyncPending), awaitItem())
            }
        }

    @Test
    fun aSyncHeldUntilTheNetworkReturnsIsNotAFailure() =
        runTest {
            // ⭐ `CLAUDE.md` §1 makes offline the normal case, and WorkManager releases a held run
            // by itself — there is nothing for the user to do, so there is nothing to tell them to
            // do. The same call M4.16 made for the sync-status card, made again here.
            val (_, viewModel) = viewModel(habits = emptyList())
            status.value = SyncStatus.WaitingForNetwork(lastSyncedAt = null)

            viewModel.uiState.test {
                assertEquals(WeekUiState.Empty(HabitsEmptyReason.SyncPending), awaitItem())
            }
        }

    @Test
    fun aFailedFirstImportSaysSoRatherThanWaitingForEver() =
        runTest {
            val (_, viewModel) = viewModel(habits = emptyList())
            status.value = SyncStatus.Failed(SyncFailure.DatabaseNotShared, lastSyncedAt = null)

            viewModel.uiState.test {
                assertEquals(WeekUiState.Empty(HabitsEmptyReason.SyncFailed), awaitItem())
            }
        }

    @Test
    fun aFailureThatArrivesWhileTheEmptyScreenIsOpenChangesWhatItSays() =
        runTest {
            // ⭐⭐ **The case ADR-072 exists for**, in order. `submitMapping` saves the mapping,
            // which flips `HabitsShell`'s gate, and only *then* awaits the first sync — so the user
            // is already looking at this screen when that sync fails. The wizard holding the error
            // is gone by then, which makes this the only surface left to say anything.
            val (_, viewModel) = viewModel(habits = emptyList())

            viewModel.uiState.test {
                assertEquals(WeekUiState.Empty(HabitsEmptyReason.SyncPending), awaitItem())

                status.value = SyncStatus.Failed(SyncFailure.Offline, lastSyncedAt = null)

                assertEquals(WeekUiState.Empty(HabitsEmptyReason.SyncFailed), awaitItem())
            }
        }

    @Test
    fun aFailedSyncChangesNothingOnScreenWhileThereAreHabitsToDraw() =
        runTest {
            // ⚠ `CLAUDE.md` §3.5: sync is invisible until it fails, and §1 keeps it away from the
            // check-off loop entirely. Reading the status for the empty branch must not leak into
            // the branch that matters — not as a message, and not as a re-emission that rebuilds
            // every card mid-tap.
            val (_, viewModel) = viewModel()

            viewModel.uiState.test {
                assertTrue(awaitItem() is WeekUiState.Content)

                status.value = SyncStatus.Failed(SyncFailure.TokenRejected, lastSyncedAt = null)

                expectNoEvents()
            }
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

    // --- midnight (M6.9) ------------------------------------------------------------------------

    @Test
    fun theRingMovesWhenTheDayChangesUnderAnOpenApp() =
        runTest {
            val (_, viewModel) = viewModel()

            viewModel.uiState.test {
                // Wednesday is index 2 of a Monday-based week.
                assertEquals(2, (awaitItem() as WeekUiState.Content).habits.first().todayIndex)

                rollOverTo(today.plusDays(1))

                assertEquals(3, (awaitItem() as WeekUiState.Content).habits.first().todayIndex)
            }
        }

    @Test
    fun theNewDaysPillStopsBeingAFutureDayAndBecomesWritable() =
        runTest {
            // ⭐⭐ The symptom that makes M6.9 more than cosmetic. M6.7 disables a pill whose cell is
            // FUTURE, so before this step the first check-off of a new day did nothing at all,
            // silently — the one interaction the whole app exists for.
            val (_, viewModel) = viewModel()
            val tomorrow = today.plusDays(1)

            viewModel.uiState.test {
                assertEquals(DayState.FUTURE, stateOn(awaitItem(), tomorrow))

                rollOverTo(tomorrow)

                assertEquals(DayState.INCOMPLETE, stateOn(awaitItem(), tomorrow))
            }
        }

    @Test
    fun crossingIntoANewWeekCarriesTheViewWithIt() =
        runTest {
            // ADR-009 makes "open onto the current week with today ringed" the landing tab's whole
            // premise. A view that stayed on last week would put a "This week" tap in front of the
            // new day's first check-off.
            val (_, viewModel) = viewModel()

            viewModel.uiState.test {
                assertEquals(thisMonday, (awaitItem() as WeekUiState.Content).weekStart)

                // Sunday 30 Aug → Monday 31 Aug.
                rollOverTo(LocalDate.of(2026, 8, 31))

                val state = awaitItem() as WeekUiState.Content
                assertEquals(LocalDate.of(2026, 8, 31), state.weekStart)
                assertTrue(state.isCurrentWeek)
            }
        }

    @Test
    fun aWeekYouPagedToStaysWhereYouLeftItWhenMidnightPasses() =
        runTest {
            // The other half of the rule. Following is right for someone who never left the current
            // week; moving the grid under someone who deliberately went looking at a past one is
            // not, and they are told where they are by "This week" lighting up.
            val (_, viewModel) = viewModel()
            viewModel.onEvent(WeekEvent.PreviousWeekPressed)

            viewModel.uiState.test {
                assertEquals(thisMonday.minusDays(7), (awaitItem() as WeekUiState.Content).weekStart)

                rollOverTo(LocalDate.of(2026, 8, 31))

                // ⭐ **No emission at all**, which is the stronger claim. A pinned week is made of
                // seven days whose meaning does not depend on which later day it happens to be, so
                // the state is not merely equivalent — it is equal, and `StateFlow` conflates it.
                expectNoEvents()

                val state = viewModel.uiState.value as WeekUiState.Content
                assertEquals(thisMonday.minusDays(7), state.weekStart)
                assertFalse(state.isCurrentWeek)
            }
        }

    @Test
    fun pagingAwayAndBackResumesFollowingRatherThanPinningTodaysWeek() =
        runTest {
            // ⚠ The invariant `moveWeeks` normalises for. Landing back on the current week by paging
            // looks identical to never having paged, so it must behave identically at midnight —
            // otherwise two states that render the same diverge a few hours later.
            val (_, viewModel) = viewModel()
            viewModel.onEvent(WeekEvent.PreviousWeekPressed)
            viewModel.onEvent(WeekEvent.NextWeekPressed)

            viewModel.uiState.test {
                assertEquals(thisMonday, (awaitItem() as WeekUiState.Content).weekStart)

                rollOverTo(LocalDate.of(2026, 8, 31))

                assertEquals(LocalDate.of(2026, 8, 31), (awaitItem() as WeekUiState.Content).weekStart)
            }
        }

    @Test
    fun thisWeekGoesBackToFollowingRatherThanToTodaysWeekAsAFixedDate() =
        runTest {
            val (_, viewModel) = viewModel()
            repeat(3) { viewModel.onEvent(WeekEvent.PreviousWeekPressed) }
            viewModel.onEvent(WeekEvent.ThisWeekPressed)

            viewModel.uiState.test {
                assertEquals(thisMonday, (awaitItem() as WeekUiState.Content).weekStart)

                rollOverTo(LocalDate.of(2026, 8, 31))

                assertEquals(LocalDate.of(2026, 8, 31), (awaitItem() as WeekUiState.Content).weekStart)
            }
        }

    @Test
    fun aDayChangeInsideTheSameWeekStillReachesTheScreen() =
        runTest {
            // ⚠ Wednesday → Thursday does not move the week at all, so the value the week is derived
            // from is unchanged. `Snapshot` carries the day as well for exactly this reason: without
            // it there would be nothing for anything downstream to tell apart.
            val (_, viewModel) = viewModel()

            viewModel.uiState.test {
                awaitItem()

                rollOverTo(today.plusDays(1))

                val state = awaitItem() as WeekUiState.Content
                assertEquals(thisMonday, state.weekStart)
                assertEquals(3, state.habits.first().todayIndex)
            }
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
            // route around it. ⚠ The refusal still goes nowhere on screen — M6.7 owns making the pill
            // untappable, and M6.6 deliberately does not invent a sentence for it.
            val (repository, viewModel) = viewModel()
            content(viewModel)

            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = HabitId("read"), date = today.plusDays(1), completed = true),
            )

            assertEquals(emptyList<FakeHabitRepository.Write>(), repository.writes)
        }

    // --- what a check-off says (M6.6) ---------------------------------------------------------

    @Test
    fun aCheckOffOnAnotherDayNamesTheHabitAndTheDayItChanged() =
        runTest {
            // ⭐ The whole point of the message. A filled pill says *something* happened; only this
            // can say it was Read, on this day — the two things a mis-tap gets wrong.
            //
            // ⚠ **On a day that is not today**, since M6.11 — see [aTapOnTodaySaysNothingAtAll].
            val (_, viewModel) = viewModel()
            content(viewModel)

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = thisMonday, completed = true),
                )

                assertEquals(
                    WeekMessage.Toggled(
                        habitId = HabitId("read"),
                        habitName = "Read",
                        date = thisMonday,
                        completed = true,
                    ),
                    awaitItem(),
                )
            }
        }

    @Test
    fun aTapOnTodaySaysNothingAtAll() =
        runTest {
            // ⭐⭐ **The user's M6.11 call, and the one that could have deleted this feature.**
            // ADR-070 fired on every write, which put a sentence on the commonest action in the app
            // and ran against the user's stated taste for persistent state over toasts. What
            // survives is the identification, spent only where a mis-aim is plausible: today is the
            // column the ring points at and the tap the landing tab exists for, so it is silent.
            val (_, viewModel) = viewModel()
            content(viewModel)

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = today, completed = true),
                )

                expectNoEvents()
            }
        }

    @Test
    fun aSilentTapOnTodayStillWritesTheTick() =
        runTest {
            // ⚠ **Silence is not refusal**, and this is what stops the two being confused. The
            // local write is unconditional (`CLAUDE.md` §1); M6.11 removed a sentence, not a
            // check-off, and today is the tap this whole screen exists to make cheap.
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
    fun theDayThatGoesQuietMovesAtMidnight() =
        runTest {
            // ⭐ The rule is "today", not a date decided once. This date was silent while it *was*
            // today; after the rollover the same tap is back-fill and gets named. That is why the
            // clock is read when the finger lands rather than remembered from construction.
            val (_, viewModel) = viewModel()
            content(viewModel)
            rollOverTo(today.plusDays(1))

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = today, completed = true),
                )

                assertEquals(today, awaitItem().date)
            }
        }

    @Test
    fun unTickingIsAnnouncedAsWhatItDid() =
        runTest {
            val (_, viewModel) = viewModel(completions = listOf(Completion(HabitId("read"), thisMonday)))
            content(viewModel)

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = thisMonday, completed = false),
                )

                // `completed` is what the tap DID, so the screen picks the "cleared" sentence.
                assertFalse((awaitItem() as WeekMessage.Toggled).completed)
            }
        }

    @Test
    fun theOfferRestoresExactlyWhatTheTapChanged() =
        runTest {
            val (_, viewModel) = viewModel()
            content(viewModel)

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = thisMonday, completed = true),
                )

                // Derived from the message rather than stored beside it, so the two cannot disagree.
                assertEquals(
                    WeekEvent.UndoPressed(habitId = HabitId("read"), date = thisMonday, completed = false),
                    (awaitItem() as WeekMessage.Toggled).undo,
                )
            }
        }

    @Test
    fun aRefusedFutureWriteSaysNothingEither() =
        runTest {
            // Nothing was written, so there is nothing to announce and nothing to offer back. M6.7
            // owns telling the user why the tap did nothing.
            val (_, viewModel) = viewModel()
            content(viewModel)

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.DayToggled(habitId = HabitId("read"), date = today.plusDays(1), completed = true),
                )

                expectNoEvents()
            }
        }

    @Test
    fun aFastHandKeepsOnlyTheNewestOfferRatherThanAQueue() =
        runTest {
            // ⭐ The `CONFLATED` channel, asserted rather than assumed. Checking off habit after
            // habit is the normal way this screen is used; a buffered channel would hold every one
            // and the screen would walk through them long after the taps that earned them.
            val (_, viewModel) = viewModel()
            content(viewModel)

            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = HabitId("read"), date = thisMonday, completed = true),
            )
            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = HabitId("run"), date = thisMonday, completed = true),
            )

            viewModel.messages.test {
                assertEquals("Run", (awaitItem() as WeekMessage.Toggled).habitName)
                expectNoEvents()
            }
        }

    // --- said once, or not at all (M6.10) -------------------------------------------------------

    @Test
    fun aMessageAlreadyShownIsNotSaidAgainWhenTheScreenComesBack() =
        runTest {
            // ⭐⭐ **The claim `WeekMessage` is built on, and nothing pinned it until M6.10.** Its
            // KDoc says a message in `UiState` "is shown again after every rotation, every tab
            // return and every process restore", and that is the whole reason it travels on a
            // `Channel`. A rotation is exactly this: the collector goes away and a new one arrives.
            val (_, viewModel) = viewModel()
            // The message names the habit, and the name is read back from the current state — so
            // the state has to exist first. `aTapIsNeverGatedOnHavingSomethingToSayAboutIt` is the
            // test for the other case, where it does not.
            content(viewModel)

            viewModel.onEvent(WeekEvent.DayToggled(habitId = read.id, date = thisMonday, completed = true))

            viewModel.messages.test { assertEquals(read.name, (awaitItem() as WeekMessage.Toggled).habitName) }

            // The screen is rebuilt. "Read checked off for Mon, Aug 24" must not be announced twice
            // for one tap the user made before the rotation.
            viewModel.messages.test { expectNoEvents() }
        }

    @Test
    fun aBacklogOfTapsIsNeverDumpedAtAScreenThatComesBack() =
        runTest {
            // ⚠ `WeekViewModel._messages`' KDoc: "returning to the tab cannot dump a history at the
            // user". Three taps with nobody listening must leave one offer, not three — and it must
            // be the newest, because it is the only one whose sentence still describes the screen.
            val (_, viewModel) = viewModel()
            content(viewModel)

            viewModel.onEvent(WeekEvent.DayToggled(habitId = read.id, date = thisMonday, completed = true))
            viewModel.onEvent(WeekEvent.DayToggled(habitId = run.id, date = thisMonday, completed = true))
            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = read.id, date = today.minusDays(1), completed = true),
            )

            viewModel.messages.test {
                val only = awaitItem() as WeekMessage.Toggled
                assertEquals(read.name, only.habitName)
                assertEquals(today.minusDays(1), only.date)
                expectNoEvents()
            }
        }

    @Test
    fun theScreenRendersEvenIfSyncStatusNeverAnswers() =
        runTest {
            // ⭐ M6.8 claimed `onStart` means reading sync status "can never delay a render", and
            // `combine` waits for every source — so a status flow that never emits would otherwise
            // hold the app's landing screen at Loading with habits sitting in Room. That is exactly
            // the gating `CLAUDE.md` §1 forbids, and the seed is what prevents it.
            val silent = FakeHabitRepository(listOf(read, run), emptyList())
            val model =
                WeekViewModel(
                    getHabitsForWeek = GetHabitsForWeek(silent, clock),
                    toggleCompletion = ToggleCompletion(silent, clock),
                    syncStatus = SilentSyncStatus,
                    today = todayFlow,
                    clock = clock,
                )

            model.uiState.test {
                assertTrue(awaitItem() is WeekUiState.Content)
            }
        }

    // --- undo (M6.6) --------------------------------------------------------------------------

    @Test
    fun undoWritesTheValueItCarries() =
        runTest {
            val (repository, viewModel) = viewModel(completions = listOf(Completion(HabitId("read"), today)))
            content(viewModel)

            viewModel.onEvent(
                WeekEvent.UndoPressed(habitId = HabitId("read"), date = today, completed = false),
            )

            assertEquals(
                listOf(FakeHabitRepository.Write(HabitId("read"), today, false)),
                repository.writes,
            )
        }

    @Test
    fun undoAnnouncesNothing() =
        runTest {
            // ⭐⭐ The one that stops an endless loop. If undo earned a message, that message would
            // offer to undo the undo, and the next press would offer to undo *that*, for as long as
            // the user kept tapping. It is also right on its own terms: the pill flips back in front
            // of them, so there is nothing left to say.
            val (_, viewModel) = viewModel()
            content(viewModel)

            viewModel.messages.test {
                viewModel.onEvent(
                    WeekEvent.UndoPressed(habitId = HabitId("read"), date = today, completed = true),
                )

                expectNoEvents()
            }
        }

    @Test
    fun aTapIsNeverGatedOnHavingSomethingToSayAboutIt() =
        runTest {
            // ⚠ The message needs a habit **name**, which is read back from the current state. This
            // pins that the write happens anyway if that lookup ever comes back empty: `CLAUDE.md` §1
            // makes the local write unconditional, and losing a sentence must never lose a tick.
            // Nothing has subscribed here, so `uiState` is still Loading and the lookup finds nothing.
            //
            // ⚠ **Not today's date**, or this would pass on M6.11's silence rather than on the
            // empty lookup it claims to test — and would keep passing if the lookup ever broke.
            val (repository, viewModel) = viewModel()

            viewModel.onEvent(
                WeekEvent.DayToggled(habitId = HabitId("read"), date = thisMonday, completed = true),
            )

            assertEquals(
                listOf(FakeHabitRepository.Write(HabitId("read"), thisMonday, true)),
                repository.writes,
            )
            viewModel.messages.test { expectNoEvents() }
        }
}

/**
 * A [SyncStatusRepository] whose status never arrives.
 *
 * Not a plausible production state so much as the shape of one: any `Flow` assembled from DataStore
 * and WorkManager can be slow on a cold start, and `combine` cannot tell "slow" from "never".
 */
private object SilentSyncStatus : SyncStatusRepository {
    override fun observeStatus(): Flow<SyncStatus> = emptyFlow()

    override fun requestSync() = Unit

    override fun requestAppOpenSync() = Unit

    override fun observeRequestedSyncRunning(): Flow<Boolean> = emptyFlow()

    override fun observeDiscardedEdits(): Flow<List<DiscardedEditNotice>> = emptyFlow()

    override suspend fun dismissDiscardedEdits() = Unit
}

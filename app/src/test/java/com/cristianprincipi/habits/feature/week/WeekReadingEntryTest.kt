package com.cristianprincipi.habits.feature.week

import app.cash.turbine.test
import com.cristianprincipi.habits.core.designsystem.component.ReadingEntry
import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.FakeMeasureRepository
import com.cristianprincipi.habits.core.testing.FakeSyncStatusRepository
import com.cristianprincipi.habits.core.testing.FakeTodayFlow
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.core.testing.MutableClock
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.GetMeasuresForWeek
import com.cristianprincipi.habits.domain.usecase.SetMeasureReading
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The number dialog, driven through `WeekViewModel` — M16.2b, ADR-156 §2.
 *
 * Separate from `WeekViewModelTest` because the subject is separate: a dialog with a text field and
 * three buttons, and what each of them writes. The habit loop above it is untouched by any of this,
 * and the last test says so.
 */
class WeekReadingEntryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Wednesday — the week runs Mon 24 – Sun 30 Aug 2026, as `WeekViewModelTest` fixes it. */
    private val today = LocalDate.of(2026, 8, 26)
    private val thisMonday = LocalDate.of(2026, 8, 24)
    private val tuesday = thisMonday.plusDays(1)
    private val tomorrow = today.plusDays(1)

    private val clock = MutableClock(today.atStartOfDay(ZoneOffset.UTC).toInstant())
    private val weight = Measure(MeasureId("w"), name = "Weight", emoji = "", unit = "kg", color = HabitColor.GREEN)
    private val habits =
        FakeHabitRepository(listOf(testHabit(id = "read", name = "Read", startDate = LocalDate.of(2026, 1, 1))))
    private val measures = FakeMeasureRepository(listOf(weight), listOf(MeasureReading(weight.id, thisMonday, 82.9)))

    // Lazy, so it is built inside the test — after `MainDispatcherRule` has installed the dispatcher
    // its `viewModelScope` captures. A field initialiser runs before the rule does.
    private val viewModel by lazy {
        WeekViewModel(
            getHabitsForWeek = GetHabitsForWeek(habits, clock),
            getMeasuresForWeek = GetMeasuresForWeek(measures, clock),
            setMeasureReading = SetMeasureReading(measures, clock),
            toggleCompletion = ToggleCompletion(habits, clock),
            syncStatus = FakeSyncStatusRepository(MutableStateFlow(SyncStatus.Idle(lastSyncedAt = null))),
            today = FakeTodayFlow(today),
            clock = clock,
        )
    }

    /** The dialog as the screen would draw it now — the newest state, whatever came before it. */
    private suspend fun entry(): ReadingEntry? {
        var state: WeekUiState = WeekUiState.Loading
        viewModel.uiState.test { state = expectMostRecentItem() }
        return (state as WeekUiState.Content).readingEntry
    }

    private suspend fun open(
        date: LocalDate,
        prefill: String = "",
    ) {
        entry()
        viewModel.onEvent(WeekEvent.ReadingCellTapped(weight.id, date, prefill))
    }

    @Test
    fun aTappedCellOpensTheDialogNamedForItsNumberAndDay() =
        runTest {
            open(thisMonday, prefill = "82.9")

            assertEquals(
                ReadingEntry(weight.id, "Weight", "kg", thisMonday, text = "82.9", hadValue = true),
                entry(),
            )
        }

    @Test
    fun aDayWithoutAValueOpensEmptyAndOffersNoClear() =
        runTest {
            open(tuesday)

            val entry = entry()!!
            assertFalse(entry.hadValue)
            assertFalse("empty is not savable", entry.canSave)
            assertFalse("empty is not an error either", entry.isInvalid)
        }

    @Test
    fun aDayThatHasNotHappenedDoesNotOpen() =
        runTest {
            // The cell is inert; this is the rule underneath it, as `ToggleCompletion` is for a pill.
            open(tomorrow)

            assertNull(entry())
        }

    @Test
    fun saveWritesWhatWasTypedInEitherDecimalAndCloses() =
        runTest {
            open(tuesday)
            viewModel.onEvent(WeekEvent.ReadingTextChanged("82,6"))

            viewModel.onEvent(WeekEvent.ReadingSaved)

            assertEquals(listOf(Triple(weight.id, tuesday, 82.6)), measures.writtenReadings)
            assertNull(entry())
        }

    @Test
    fun somethingThatIsNotANumberCannotBeSavedAndSaysSo() =
        runTest {
            // Decimal hours only (the user's ruling): `7:30` is refused, not guessed at.
            open(tuesday)
            viewModel.onEvent(WeekEvent.ReadingTextChanged("7:30"))

            val entry = entry()!!
            assertFalse(entry.canSave)
            assertTrue(entry.isInvalid)

            viewModel.onEvent(WeekEvent.ReadingSaved)

            assertTrue("a stale Save must write nothing", measures.writtenReadings.isEmpty())
            assertEquals("and the dialog stays for a correction", "7:30", entry()?.text)
        }

    @Test
    fun clearWritesNoValueAndCloses() =
        runTest {
            open(thisMonday, prefill = "82.9")

            viewModel.onEvent(WeekEvent.ReadingCleared)

            assertEquals(listOf(Triple(weight.id, thisMonday, null)), measures.writtenReadings)
            assertNull(entry())
        }

    @Test
    fun cancelWritesNothing() =
        runTest {
            open(thisMonday, prefill = "82.9")
            viewModel.onEvent(WeekEvent.ReadingTextChanged("80"))

            viewModel.onEvent(WeekEvent.ReadingDismissed)

            assertTrue(measures.writtenReadings.isEmpty())
            assertNull(entry())
        }

    @Test
    fun aSaveThatDoesNotLandSaysSoAndOffersTheSameWrite() =
        runTest {
            // M13.12's rule for a number: the cell did not change, so silence would look like a
            // finger that missed.
            measures.failWritesWith = IOException("disk full")
            open(tuesday)
            viewModel.onEvent(WeekEvent.ReadingTextChanged("82.6"))

            viewModel.messages.test {
                viewModel.onEvent(WeekEvent.ReadingSaved)

                assertEquals(
                    WeekMessage.ReadingSaveFailed(
                        measureName = "Weight",
                        date = tuesday,
                        retry = WeekEvent.ReadingRetried(weight.id, tuesday, 82.6),
                    ),
                    awaitItem(),
                )
            }
        }

    @Test
    fun retryWritesTheValueItCarries() =
        runTest {
            entry()

            viewModel.onEvent(WeekEvent.ReadingRetried(weight.id, tuesday, 82.6))

            assertEquals(listOf(Triple(weight.id, tuesday, 82.6)), measures.writtenReadings)
        }

    @Test
    fun aNumberNeverTouchesAHabit() =
        runTest {
            open(tuesday)
            viewModel.onEvent(WeekEvent.ReadingTextChanged("82.6"))
            viewModel.onEvent(WeekEvent.ReadingSaved)

            assertTrue(habits.writes.isEmpty())
        }
}

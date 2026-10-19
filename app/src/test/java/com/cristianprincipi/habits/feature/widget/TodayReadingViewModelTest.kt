package com.cristianprincipi.habits.feature.widget

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.cristianprincipi.habits.core.designsystem.component.ReadingEntry
import com.cristianprincipi.habits.core.testing.FakeMeasureRepository
import com.cristianprincipi.habits.core.testing.FakeSyncStatusRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.core.testing.MutableClock
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import com.cristianprincipi.habits.domain.usecase.SetMeasureReading
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
import java.util.Locale

/**
 * The number widget's dialog — M16.3. Today's value, written the way Week writes one, and synced
 * the way the habit square syncs: at the tap.
 */
class TodayReadingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 19)
    private val clock = MutableClock(today.atStartOfDay(ZoneOffset.UTC).toInstant())
    private val weight = Measure(MeasureId("w"), name = "Weight", emoji = "", unit = "kg", color = HabitColor.GREEN)
    private val sleep = Measure(MeasureId("s"), name = "Sleep", emoji = "", unit = "h", color = HabitColor.PURPLE)
    private val measures =
        FakeMeasureRepository(
            measures = listOf(weight, sleep),
            readings =
                listOf(
                    MeasureReading(weight.id, today.minusDays(1), 83.1),
                    MeasureReading(sleep.id, today, 7.5),
                ),
        )
    private val syncStatus = FakeSyncStatusRepository()

    private fun viewModel(measureId: String? = weight.id.value): TodayReadingViewModel =
        TodayReadingViewModel(
            savedStateHandle =
                SavedStateHandle(
                    measureId?.let { mapOf(TodayReadingActivity.EXTRA_MEASURE_ID to it) } ?: emptyMap(),
                ),
            measures = measures,
            setMeasureReading = SetMeasureReading(measures, clock),
            syncStatus = syncStatus,
            clock = clock,
        )

    /** The dialog as the screen would draw it now, or null while there is none. */
    private suspend fun TodayReadingViewModel.entry(): ReadingEntry? {
        var state: TodayReadingUiState = TodayReadingUiState.Loading
        uiState.test { state = expectMostRecentItem() }
        return (state as? TodayReadingUiState.Open)?.entry
    }

    private suspend fun TodayReadingViewModel.assertClosed() = closed.test { awaitItem() }

    private suspend fun TodayReadingViewModel.assertStillOpen() =
        closed.test {
            expectNoEvents()
            cancel()
        }

    @Test
    fun itOpensOnTodayForTheWidgetsNumberWithNothingInTheFieldWhenTodayIsEmpty() =
        runTest {
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }

            // Yesterday's 83.1 is not today's: the field starts empty and Clear is not offered.
            assertEquals(
                ReadingEntry(weight.id, "Weight", "kg", today, text = "", hadValue = false),
                viewModel.entry(),
            )
        }

    @Test
    fun todaysValueIsPrefilledInTheReadersSeparator() =
        runTest {
            val viewModel = viewModel(sleep.id.value).apply { onEvent(TodayReadingEvent.Started(Locale.ITALY)) }

            val entry = viewModel.entry()
            assertEquals("7,5", entry?.text)
            assertTrue("Clear is offered for a day with a value", entry?.hadValue == true)
        }

    @Test
    fun saveWritesTodaySyncsAtOnceAndCloses() =
        runTest {
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()

            viewModel.onEvent(TodayReadingEvent.TextChanged("82,4"))
            viewModel.onEvent(TodayReadingEvent.Saved)

            assertEquals(listOf(Triple(weight.id, today, 82.4)), measures.writtenReadings)
            assertEquals("the widget's sync, as a check-off's", 1, syncStatus.widgetCheckOffSyncRequests)
            viewModel.assertClosed()
        }

    @Test
    fun clearWritesAnEmptyValueNeverAZero() =
        runTest {
            val viewModel = viewModel(sleep.id.value).apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()

            viewModel.onEvent(TodayReadingEvent.Cleared)

            assertEquals(listOf(Triple(sleep.id, today, null)), measures.writtenReadings)
            assertEquals(1, syncStatus.widgetCheckOffSyncRequests)
            viewModel.assertClosed()
        }

    @Test
    fun cancelWritesNothingAndAsksForNoSync() =
        runTest {
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()

            viewModel.onEvent(TodayReadingEvent.TextChanged("82.4"))
            viewModel.onEvent(TodayReadingEvent.Dismissed)

            assertTrue(measures.writtenReadings.isEmpty())
            assertEquals(0, syncStatus.widgetCheckOffSyncRequests)
            viewModel.assertClosed()
        }

    @Test
    fun aFieldThatIsNotANumberSavesNothingAndStaysOpen() =
        runTest {
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()

            viewModel.onEvent(TodayReadingEvent.TextChanged("7:30"))
            viewModel.onEvent(TodayReadingEvent.Saved)

            assertTrue(measures.writtenReadings.isEmpty())
            assertTrue(viewModel.entry()?.isInvalid == true)
            viewModel.assertStillOpen()
        }

    @Test
    fun aWriteThatFailsKeepsTheDialogOpenAndSaysSoUntilTheNextAttempt() =
        runTest {
            // ADR-120's rule: a local write that fails says so. Here there is no screen behind the
            // dialog for a snackbar, so the dialog stays and its field carries the sentence.
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()
            measures.failWritesWith = IOException("disk full")

            viewModel.onEvent(TodayReadingEvent.TextChanged("82.4"))
            viewModel.onEvent(TodayReadingEvent.Saved)

            assertTrue(viewModel.entry()?.saveFailed == true)
            assertEquals("nothing landed, so nothing is owed", 0, syncStatus.widgetCheckOffSyncRequests)
            viewModel.assertStillOpen()

            viewModel.onEvent(TodayReadingEvent.TextChanged("82.5"))
            assertFalse("typing again clears the old failure", viewModel.entry()!!.saveFailed)

            measures.failWritesWith = null
            viewModel.onEvent(TodayReadingEvent.Saved)
            assertEquals(listOf(Triple(weight.id, today, 82.5)), measures.writtenReadings)
            viewModel.assertClosed()
        }

    @Test
    fun aSyncRequestThatFailsStillClosesBecauseTheNumberIsSaved() =
        runTest {
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()
            syncStatus.failWidgetCheckOffSyncWith = IllegalStateException("WorkManager could not store it")

            viewModel.onEvent(TodayReadingEvent.TextChanged("82.4"))
            viewModel.onEvent(TodayReadingEvent.Saved)

            assertEquals(1, measures.writtenReadings.size)
            assertFalse("saying it failed would be untrue", viewModel.entry()?.saveFailed == true)
            viewModel.assertClosed()
        }

    @Test
    fun aNumberThatIsGoneClosesWithoutADialog() =
        runTest {
            // A widget outlives its number (ADR-081): no dialog for a question with no answer.
            val viewModel = viewModel("removed").apply { onEvent(TodayReadingEvent.Started(Locale.US)) }

            assertNull(viewModel.entry())
            viewModel.assertClosed()
        }

    @Test
    fun anIntentWithNoNumberClosesWithoutADialog() =
        runTest {
            val viewModel = viewModel(measureId = null).apply { onEvent(TodayReadingEvent.Started(Locale.US)) }

            assertNull(viewModel.entry())
            viewModel.assertClosed()
        }

    @Test
    fun aRotationDoesNotThrowAwayWhatWasTyped() =
        runTest {
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()
            viewModel.onEvent(TodayReadingEvent.TextChanged("82.4"))

            viewModel.onEvent(TodayReadingEvent.Started(Locale.US))

            assertEquals("82.4", viewModel.entry()?.text)
        }

    @Test
    fun aDialogLeftOpenPastMidnightWritesTheDayItNames() =
        runTest {
            val viewModel = viewModel().apply { onEvent(TodayReadingEvent.Started(Locale.US)) }
            viewModel.entry()
            clock.setTo(today.plusDays(1))

            viewModel.onEvent(TodayReadingEvent.TextChanged("82.4"))
            viewModel.onEvent(TodayReadingEvent.Saved)

            assertEquals(listOf(Triple(weight.id, today, 82.4)), measures.writtenReadings)
        }
}

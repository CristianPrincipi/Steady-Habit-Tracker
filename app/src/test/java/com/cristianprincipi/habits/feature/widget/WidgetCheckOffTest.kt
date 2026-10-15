package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.FakeSyncStatusRepository
import com.cristianprincipi.habits.core.testing.MutableClock
import com.cristianprincipi.habits.core.testing.RecordingWidgetRefresher
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.GetHabits
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The widget's corner square — M15.11. What a tap writes, and the taps it refuses to write.
 *
 * ⚠ **The refusals are the point.** A widget is a picture that can be hours old, so the dangerous
 * tap is not the one that fails — it is the one that succeeds against a picture that is no longer
 * true. Each refusal below is a write that would have changed the user's history behind their back.
 */
class WidgetCheckOffTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val read = testHabit(id = "read", name = "Read")
    private val habits = FakeHabitRepository(habits = listOf(read))
    private val clock = MutableClock(today.atTime(9, 0).toInstant(ZoneOffset.UTC))
    private val syncStatus = FakeSyncStatusRepository()

    private fun checkOff(widgets: RecordingWidgetRefresher = RecordingWidgetRefresher()) =
        WidgetCheckOff(
            habits = GetHabits(habits),
            toggleCompletion = ToggleCompletion(habits, clock),
            syncStatus = syncStatus,
            widgets = widgets,
            clock = clock,
        )

    @Test
    fun aTapOnTodaysEmptySquareChecksItOffAndAsksForTheWidgetSync() =
        runTest {
            checkOff()(read.id, today, completed = true)

            assertEquals(listOf(FakeHabitRepository.Write(read.id, today, completed = true)), habits.writes)
            assertEquals(
                "A widget is there so the app is not opened — Notion has to be asked",
                1,
                syncStatus.widgetCheckOffSyncRequests,
            )
            assertEquals(
                "A widget check-off must not count as the user's pull-to-refresh",
                0,
                syncStatus.syncRequests,
            )
        }

    @Test
    fun aTapOnAFilledSquareTakesItBack() =
        runTest {
            checkOff()(read.id, today, completed = false)

            assertEquals(listOf(FakeHabitRepository.Write(read.id, today, completed = false)), habits.writes)
            assertEquals(1, syncStatus.widgetCheckOffSyncRequests)
        }

    @Test
    fun aSquareDrawnBeforeMidnightWritesNothingAndRepaints() =
        runTest {
            // Drawn at 23:50, tapped at 00:05 — the midnight repaint was late. Writing today would
            // apply a decision made about yesterday's square; writing yesterday is back-fill.
            val widgets = RecordingWidgetRefresher()

            checkOff(widgets)(read.id, today.minusDays(1), completed = true)

            assertTrue("Nothing may be written from a stale square", habits.writes.isEmpty())
            assertEquals("The repaint is the answer: it shows today's square", 1, widgets.refreshes)
            assertEquals(0, syncStatus.widgetCheckOffSyncRequests)
        }

    @Test
    fun aHabitThatLeftTheMappingWritesNothing() =
        runTest {
            val widgets = RecordingWidgetRefresher()

            checkOff(widgets)(HabitId("journal"), today, completed = true)

            assertTrue(habits.writes.isEmpty())
            assertEquals(1, widgets.refreshes)
        }

    @Test
    fun thePickersSampleSquareCanNeverWrite() =
        runTest {
            // The preview shows the user's REAL first habit, so its square must not carry that id —
            // and if a tap ever reached it anyway, the sample id must find nothing to write to.
            checkOff()(SAMPLE_HABIT_ID, today, completed = true)

            assertTrue(habits.writes.isEmpty())
        }

    @Test
    fun aFailedWriteIsSwallowedRepaintedAndNotSynced() =
        runTest {
            // An exception escaping a Glance action callback takes the process with it. The repaint
            // shows the square as it really is — unchanged — so the tap visibly did nothing.
            habits.failWritesWith = IOException("disk full")
            val widgets = RecordingWidgetRefresher()

            checkOff(widgets)(read.id, today, completed = true)

            assertEquals(1, widgets.refreshes)
            assertEquals(0, syncStatus.widgetCheckOffSyncRequests)
        }

    @Test
    fun theTapSurvivesR8() {
        // Glance builds the callback by reflection, and its consumer rule keeps only PUBLIC
        // ActionCallbacks. A private one would pass every debug test and fail only in release.
        val callback = CheckOffTodayAction::class.java

        assertTrue(Modifier.isPublic(callback.modifiers))
        assertTrue(Modifier.isPublic(callback.getDeclaredConstructor().modifiers))
    }
}

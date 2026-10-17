package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.core.testing.RecordingWidgetRefresher
import com.cristianprincipi.habits.data.local.TestTransactionRunner
import com.cristianprincipi.habits.data.local.dao.FakeDayDao
import com.cristianprincipi.habits.data.local.dao.FakeMeasureDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEditEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.MeasureId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [MeasureRepositoryImpl.setReading] — M16.2a, what one typed number writes (ADR-156 §2).
 *
 * What these prove is the record the sync engine relies on: the reading shows at once, an edit row
 * says it is owed with **what Notion held** as its base, and the day's checkbox state is never
 * touched. `SyncMeasureWriteTest` drives the same class through a whole sync.
 */
class MeasureRepositoryImplTest {
    private val fixedMillis = 1_755_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(fixedMillis), ZoneOffset.UTC)

    private val date = LocalDate.of(2026, 10, 15)
    private val epochDay = date.toEpochDay()
    private val dayWithNothing = LocalDate.of(2026, 10, 14)
    private val weight = MeasureId("measure-weight")

    private val days = FakeDayDao()
    private val measures =
        FakeMeasureDao(
            initial =
                listOf(
                    MeasureEntity(
                        id = weight.value,
                        notionPropertyId = "prop-weight",
                        notionPropertyName = "Weight",
                        unit = "kg",
                        color = HabitColor.GREEN.name,
                        isActive = true,
                        sortOrder = 0,
                    ),
                ),
            dayExists = { epochDay -> epochDay in days.current },
        )
    private val widgets = RecordingWidgetRefresher()

    private val repository =
        MeasureRepositoryImpl(
            measureDao = measures,
            dayDao = days,
            transactionRunner = TestTransactionRunner(),
            clock = clock,
            widgets = widgets,
        )

    /** A day pulled from Notion holding [value], as the sync leaves one. */
    private suspend fun pulled(value: Double?) {
        days.upsert(
            DayEntity(
                epochDay = epochDay,
                notionPageId = "page-1",
                localUpdatedAtEpochMillis = null,
                remoteLastEditedAtEpochMillis = 1_000,
                syncState = SyncState.SYNCED,
            ),
        )
        value?.let { measures.upsertReading(MeasurementEntity(weight.value, epochDay, it)) }
    }

    private fun reading(): Double? = measures.currentReadings.singleOrNull()?.value

    private fun owed(): MeasurementEditEntity? = measures.currentEdits.singleOrNull()

    @Test
    fun aTypedNumberShowsAtOnceAndIsOwedAgainstWhatNotionHeld() =
        runTest {
            pulled(84.2)

            repository.setReading(weight, date, 83.6)

            assertEquals(83.6, reading())
            assertEquals(MeasurementEditEntity(weight.value, epochDay, baseValue = 84.2, fixedMillis), owed())
        }

    @Test
    fun aSecondEditBeforeThePushKeepsTheFirstBase() =
        runTest {
            // Notion still holds 84.2 — nothing has been sent — so that stays the common ancestor.
            pulled(84.2)
            repository.setReading(weight, date, 83.6)

            repository.setReading(weight, date, 83.4)

            assertEquals(83.4, reading())
            assertEquals(84.2, owed()?.baseValue)
        }

    @Test
    fun aNumberOnADayNotionHeldNothingForHasAnEmptyBase() =
        runTest {
            pulled(null)

            repository.setReading(weight, date, 83.6)

            assertNull(owed()?.baseValue)
            assertEquals(1, measures.currentEdits.size)
        }

    @Test
    fun clearingDeletesTheReadingAndKeepsTheEdit() =
        runTest {
            // ⚠⚠ Never a zero: the missing row is the "no value", and the edit row is what makes
            // the push send an empty number rather than nothing at all.
            pulled(84.2)

            repository.setReading(weight, date, null)

            assertNull(reading())
            assertEquals(84.2, owed()?.baseValue)
        }

    @Test
    fun savingTheValueAlreadyHeldWritesNothing() =
        runTest {
            pulled(84.2)

            repository.setReading(weight, date, 84.2)
            repository.setReading(weight, dayWithNothing, null)

            assertTrue(measures.currentEdits.isEmpty())
            assertEquals(0, widgets.refreshes)
        }

    @Test
    fun typingBackTheOriginalValueStillOwesIt() =
        runTest {
            // Once an edit is owed, the repository cannot know Notion still holds the base — only the
            // next pull can, and the merge handles "Notion holds what we hold" by dropping the edit.
            pulled(84.2)
            repository.setReading(weight, date, 83.6)

            repository.setReading(weight, date, 84.2)

            assertEquals(84.2, reading())
            assertEquals(84.2, owed()?.baseValue)
        }

    @Test
    fun theDaysCheckboxStateIsNeverTouched() =
        runTest {
            // ⭐⭐ ADR-156: stamping the day would make this number defend the phone's checkboxes in
            // a conflict nobody had. A day owing a check-off keeps exactly its own record too.
            val owingCheckOff =
                DayEntity(
                    epochDay = epochDay,
                    notionPageId = "page-1",
                    localUpdatedAtEpochMillis = 42,
                    remoteLastEditedAtEpochMillis = 1_000,
                    syncState = SyncState.PENDING_PUSH,
                )
            days.upsert(owingCheckOff)

            repository.setReading(weight, date, 83.6)

            assertEquals(owingCheckOff, days.current.getValue(epochDay))
        }

    @Test
    fun aDayNeverSeenIsCreatedWithItsCheckboxesOwingNothing() =
        runTest {
            repository.setReading(weight, date, 83.6)

            assertEquals(
                DayEntity(
                    epochDay = epochDay,
                    notionPageId = null,
                    localUpdatedAtEpochMillis = null,
                    remoteLastEditedAtEpochMillis = null,
                    syncState = SyncState.SYNCED,
                ),
                days.current.getValue(epochDay),
            )
            assertEquals(83.6, reading())
            assertNull(owed()?.baseValue)
        }

    @Test
    fun theWidgetsAreRepaintedAfterAWrite() =
        runTest {
            pulled(84.2)

            repository.setReading(weight, date, 83.6)

            assertEquals(1, widgets.refreshes)
        }

    @Test
    fun aNumberThatIsNotFiniteIsRefused() =
        runTest {
            // JSON has no NaN or Infinity; one sent to Notion would be a rejected request, and one
            // stored here would poison every average it touched.
            pulled(84.2)

            val refused = runCatching { repository.setReading(weight, date, Double.NaN) }.exceptionOrNull()

            assertTrue("got $refused", refused is IllegalArgumentException)
            assertEquals(84.2, reading())
            assertTrue(measures.currentEdits.isEmpty())
        }
}

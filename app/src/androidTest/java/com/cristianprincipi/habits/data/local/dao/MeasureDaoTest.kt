package com.cristianprincipi.habits.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.testing.createInMemoryDatabase
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.domain.model.HabitColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `MeasureDao` against real SQLite — the schema cache and the readings on it (M15.4).
 *
 * Shaped after `DayAndCompletionDaoTest`, because most of what is worth testing here is the same
 * relationship: a reading cannot exist without its day, and deleting either parent takes its
 * readings with it.
 *
 * ⭐ **The one that is not a copy is [aSecondReadingForADayReplacesTheFirst].** A completion uses
 * `IGNORE` and must, because "done" has no payload to update. A reading has a value the user can
 * correct in Notion, so the two DAOs deliberately differ, and this is where that difference is held
 * still.
 */
@RunWith(AndroidJUnit4::class)
class MeasureDaoTest {
    private lateinit var database: HabitsDatabase
    private lateinit var measures: MeasureDao

    private val weight = "weight"
    private val sleep = "sleep"
    private val day1 = 20_300L
    private val day2 = 20_301L

    @Before
    fun setUp() =
        runTest {
            database = createInMemoryDatabase()
            measures = database.measureDao()

            measures.upsert(listOf(measure(weight, "kg", 0), measure(sleep, "h", 1)))
            database.dayDao().upsert(day(day1))
            database.dayDao().upsert(day(day2))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aReadingIsStoredAndReadBackForItsRange() =
        runTest {
            measures.upsertReading(MeasurementEntity(weight, day1, 84.2))

            val stored = measures.observeBetween(day1, day2).first()

            assertEquals(listOf(MeasurementEntity(weight, day1, 84.2)), stored)
        }

    @Test
    fun bothBoundsOfTheRangeAreInclusive() =
        runTest {
            // `BETWEEN` is inclusive at both ends in SQLite, which is what lets the average and the
            // grid above it agree about which days they cover.
            measures.upsertReading(MeasurementEntity(weight, day1, 84.0))
            measures.upsertReading(MeasurementEntity(weight, day2, 83.5))

            assertEquals(2, measures.observeBetween(day1, day2).first().size)
        }

    @Test
    fun aSecondReadingForADayReplacesTheFirst() =
        runTest {
            // ⭐⭐ **The difference from `CompletionDao`, held still.** A completion re-inserted must
            // change nothing, so it uses IGNORE. A reading corrected in Notion must land, so this
            // upserts — IGNORE here would pin the first number ever synced and every later pull
            // would silently do nothing.
            measures.upsertReading(MeasurementEntity(weight, day1, 84.0))

            measures.upsertReading(MeasurementEntity(weight, day1, 83.1))

            assertEquals(listOf(83.1), measures.observeBetween(day1, day1).first().map { it.value })
        }

    @Test
    fun anEmptiedNumberLeavesNoRowRatherThanAZero() =
        runTest {
            // ⚠⚠ The invisible failure this table is designed against: a day with no reading is not
            // a day with a reading of zero, and a 0.0 would drag an average down with nothing on
            // screen looking unwell.
            measures.upsertReading(MeasurementEntity(weight, day1, 84.0))

            measures.deleteReading(measureId = weight, epochDay = day1)

            assertEquals(emptyList<MeasurementEntity>(), measures.observeBetween(day1, day1).first())
        }

    @Test
    fun deletingAReadingThatIsNotThereIsNotAnError() =
        runTest {
            measures.deleteReading(measureId = weight, epochDay = day1)

            assertTrue(measures.observeBetween(day1, day2).first().isEmpty())
        }

    @Test
    fun onlyActiveMeasuresAreObserved() =
        runTest {
            // ADR-081 for numbers: a column that left the mapped set is hidden, not deleted, and
            // `observeActive` is the only read the UI has.
            measures.deactivateAllExcept(listOf(weight))

            assertEquals(listOf(weight), measures.observeActive().first().map { it.id })
        }

    @Test
    fun hidingAMeasureKeepsItsReadings() =
        runTest {
            // The whole point of deactivating rather than deleting: Notion destroyed the column and
            // every value in it, so these rows may be the only surviving copy.
            measures.upsertReading(MeasurementEntity(sleep, day1, 7.5))

            measures.deactivateAllExcept(listOf(weight))

            assertEquals(1, measures.observeBetween(day1, day1).first().size)
            assertEquals(2, measures.findAll().size)
        }

    @Test
    fun measuresComeBackInTheOrderTheUserChose() =
        runTest {
            // `sortOrder` is assigned from the mapping's order, which is the order the user ticked
            // the columns in and the order they read them in.
            measures.upsert(listOf(measure(weight, "kg", 5), measure(sleep, "h", 2)))

            assertEquals(listOf(sleep, weight), measures.observeActive().first().map { it.id })
        }

    @Test
    fun deletingAMeasureTakesItsReadingsWithIt() =
        runTest {
            measures.upsertReading(MeasurementEntity(weight, day1, 84.0))
            measures.upsertReading(MeasurementEntity(sleep, day1, 7.5))

            measures.deleteAllMeasures()

            assertTrue(measures.observeBetween(day1, day2).first().isEmpty())
        }

    @Test
    fun deletingADayTakesTheReadingsOnItWithIt() =
        runTest {
            // A reading outliving the day it belongs to would be unreachable and uncounted.
            measures.upsertReading(MeasurementEntity(weight, day1, 84.0))
            measures.upsertReading(MeasurementEntity(weight, day2, 83.0))

            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM days WHERE epochDay = ?",
                arrayOf<Any>(day1),
            )

            assertEquals(listOf(day2), measures.observeBetween(day1, day2).first().map { it.epochDay })
        }

    @Test
    fun aReadingForADayThatWasNeverSyncedIsRefused() =
        runTest {
            // The same constraint a completion has, and the same intended outcome: the sync writes
            // the day and its readings in one transaction, so a bare insert against a day the app
            // has never seen is a bug rather than something to work around.
            val thrown =
                runCatching { measures.upsertReading(MeasurementEntity(weight, 19_000L, 84.0)) }
                    .exceptionOrNull()

            assertTrue("Expected a constraint failure but got $thrown", thrown != null)
        }

    @Test
    fun theUnitAndTheColourRoundTrip() =
        runTest {
            // Both are app-local and exist nowhere else — the unit was typed by the user and cannot
            // be derived, the colour has no Notion counterpart at all. `upsert` writes every column,
            // so the importer must carry them forward, and this is what that has to survive.
            val stored = measures.findAll().associateBy(MeasureEntity::id)

            assertEquals("kg", stored[weight]?.unit)
            assertEquals("h", stored[sleep]?.unit)
            assertEquals(HabitColor.GREEN.name, stored[weight]?.color)
            assertNull(stored["nothing"])
        }

    private fun measure(
        id: String,
        unit: String,
        order: Int,
    ) = MeasureEntity(
        id = id,
        notionPropertyId = "prop-$id",
        notionPropertyName = id,
        unit = unit,
        color = HabitColor.GREEN.name,
        isActive = true,
        sortOrder = order,
    )

    private fun day(epochDay: Long) =
        DayEntity(
            epochDay = epochDay,
            notionPageId = "page-$epochDay",
            localUpdatedAtEpochMillis = null,
            remoteLastEditedAtEpochMillis = 1_725_000_000_000L,
            syncState = SyncState.SYNCED,
        )
}

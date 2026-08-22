package com.cristianprincipi.habits.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.testing.createInMemoryDatabase
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.HabitEntity
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
import java.time.LocalDate

/**
 * `DayDao` and `CompletionDao` against real SQLite, plus the two cascades that join them.
 *
 * The two DAOs share a file because most of what is worth testing is the relationship between them:
 * a completion cannot exist without its day, and deleting either parent takes its completions with
 * it. Splitting them would mean building the same fixture twice to test one behaviour.
 */
@RunWith(AndroidJUnit4::class)
class DayAndCompletionDaoTest {
    private lateinit var database: HabitsDatabase
    private lateinit var days: DayDao
    private lateinit var completions: CompletionDao

    private val read = "read"
    private val water = "water"
    private val day1 = LocalDate.of(2026, 8, 20).toEpochDay()
    private val day2 = LocalDate.of(2026, 8, 21).toEpochDay()
    private val day3 = LocalDate.of(2026, 8, 22).toEpochDay()

    @Before
    fun setUp() =
        runTest {
            database = createInMemoryDatabase()
            days = database.dayDao()
            completions = database.completionDao()
            database.habitDao().upsert(listOf(habitRow(read, 0), habitRow(water, 1)))
            listOf(day1, day2, day3).forEach { days.upsert(dayRow(it)) }
        }

    @After
    fun tearDown() = database.close()

    private fun habitRow(
        id: String,
        sortOrder: Int,
    ) = HabitEntity(
        id = id,
        notionPropertyId = "prop-$id",
        notionPropertyName = id,
        color = HabitColor.BLUE.name,
        startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
        isActive = true,
        sortOrder = sortOrder,
    )

    private fun dayRow(
        epochDay: Long,
        pageId: String? = null,
        remoteMillis: Long? = null,
        state: SyncState = SyncState.SYNCED,
    ) = DayEntity(
        epochDay = epochDay,
        notionPageId = pageId,
        localUpdatedAtEpochMillis = null,
        remoteLastEditedAtEpochMillis = remoteMillis,
        syncState = state,
    )

    // ─── DayDao ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun aDayThatWasNeverTouchedIsNotFound() =
        runTest {
            assertNull(days.find(LocalDate.of(1999, 1, 1).toEpochDay()))
        }

    @Test
    fun upsertingADayKeepsOneRowPerDate() =
        runTest {
            days.upsert(dayRow(day1, pageId = "page-1", state = SyncState.PENDING_PUSH))

            val found = days.find(day1)!!
            assertEquals("page-1", found.notionPageId)
            assertEquals(SyncState.PENDING_PUSH, found.syncState)
        }

    @Test
    fun daysOwingNotionAWriteComeBackOldestFirst() =
        runTest {
            // The push path drains this in order, so an out-of-order sync would write a later day's
            // state over an earlier one on a page that covers both.
            days.upsert(dayRow(day3, state = SyncState.PENDING_PUSH))
            days.upsert(dayRow(day1, state = SyncState.PENDING_PUSH))

            assertEquals(listOf(day1, day3), days.withState(SyncState.PENDING_PUSH).map { it.epochDay })
        }

    @Test
    fun withStateMatchesOnlyTheStateAsked() =
        runTest {
            days.upsert(dayRow(day1, state = SyncState.PENDING_PUSH))
            days.upsert(dayRow(day2, state = SyncState.CONFLICT))

            assertEquals(listOf(day2), days.withState(SyncState.CONFLICT).map { it.epochDay })
        }

    @Test
    fun theSyncStateIsStoredByNameSoTheEnumCanBeReordered() =
        runTest {
            // Stored by name rather than ordinal. An ordinal would make reordering the enum a
            // silent data corruption; this is the assertion that the converter is doing its job.
            days.upsert(dayRow(day1, state = SyncState.PENDING_DELETE))

            val stored =
                database.openHelper.readableDatabase
                    .query("SELECT syncState FROM days WHERE epochDay = $day1")
                    .use {
                        it.moveToFirst()
                        it.getString(0)
                    }

            assertEquals("PENDING_DELETE", stored)
        }

    @Test
    fun theLatestRemoteEditIsTheHighestOneSeen() =
        runTest {
            // Drives the incremental pull filter. Taking the lowest, or the last written, would
            // re-fetch the whole database on every sync.
            days.upsert(dayRow(day1, remoteMillis = 100L))
            days.upsert(dayRow(day2, remoteMillis = 900L))
            days.upsert(dayRow(day3, remoteMillis = 500L))

            assertEquals(900L, days.latestRemoteEditMillis())
        }

    @Test
    fun theLatestRemoteEditIgnoresDaysNotYetSynced() =
        runTest {
            days.upsert(dayRow(day1, remoteMillis = null))
            days.upsert(dayRow(day2, remoteMillis = 700L))

            assertEquals(700L, days.latestRemoteEditMillis())
        }

    @Test
    fun theLatestRemoteEditOfNothingIsNull() =
        runTest {
            // First run, before any pull. MAX over no rows is NULL in SQLite, and the return type
            // has to be nullable for that — a non-null Long would come back as 0 and mean "1970",
            // which as a pull filter would fetch everything.
            assertNull(days.latestRemoteEditMillis())
        }

    // ─── CompletionDao ─────────────────────────────────────────────────────────────────────────

    @Test
    fun completionsAreObservedInclusiveOfBothBounds() =
        runTest {
            // The repository contract promises inclusive; BETWEEN delivers it. Off by one here
            // silently drops the first or last day of every screen.
            listOf(day1, day2, day3).forEach { completions.insert(HabitCompletionEntity(read, it)) }

            val inRange = completions.observeBetween(from = day1, to = day3).first()

            assertEquals(listOf(day1, day2, day3), inRange.map { it.epochDay }.sorted())
        }

    @Test
    fun completionsOutsideTheRangeAreLeftOut() =
        runTest {
            listOf(day1, day2, day3).forEach { completions.insert(HabitCompletionEntity(read, it)) }

            val middleOnly = completions.observeBetween(from = day2, to = day2).first()

            assertEquals(listOf(day2), middleOnly.map { it.epochDay })
        }

    @Test
    fun oneRangeQueryReturnsEveryHabitsCompletions() =
        runTest {
            // There is deliberately no per-habit overload — one query serves the whole screen.
            completions.insert(HabitCompletionEntity(read, day1))
            completions.insert(HabitCompletionEntity(water, day1))

            val all = completions.observeBetween(from = day1, to = day1).first()

            assertEquals(setOf(read, water), all.map { it.habitId }.toSet())
        }

    @Test
    fun insertingTheSameCompletionTwiceIsIgnored() =
        runTest {
            // OnConflictStrategy.IGNORE against the real composite primary key. This is what makes
            // a replayed write — by a double tap or a sync killed mid-run — harmless.
            completions.insert(HabitCompletionEntity(read, day1))
            completions.insert(HabitCompletionEntity(read, day1))

            assertEquals(1, completions.forDay(day1).size)
        }

    @Test
    fun deletingACompletionThatIsNotThereIsNotAnError() =
        runTest {
            completions.delete(habitId = read, epochDay = day1)

            assertTrue(completions.forDay(day1).isEmpty())
        }

    @Test
    fun deletingTakesOnlyTheHabitAndDayNamed() =
        runTest {
            completions.insert(HabitCompletionEntity(read, day1))
            completions.insert(HabitCompletionEntity(water, day1))
            completions.insert(HabitCompletionEntity(read, day2))

            completions.delete(habitId = read, epochDay = day1)

            assertEquals(listOf(water), completions.forDay(day1).map { it.habitId })
            assertEquals(1, completions.forDay(day2).size)
        }

    @Test
    fun forDayReturnsEveryHabitCheckedOffThatDay() =
        runTest {
            // What the push path reads to build one page update out of several check-offs.
            completions.insert(HabitCompletionEntity(read, day1))
            completions.insert(HabitCompletionEntity(water, day1))

            assertEquals(2, completions.forDay(day1).size)
        }

    // ─── The cascades ──────────────────────────────────────────────────────────────────────────

    @Test
    fun deletingAHabitTakesItsCompletionsWithIt() =
        runTest {
            // Habits are deleted locally only when the database is wiped on disconnect
            // (CLAUDE.md §3.6). Orphaned completions would survive that wipe.
            completions.insert(HabitCompletionEntity(read, day1))
            completions.insert(HabitCompletionEntity(water, day1))

            database.openHelper.writableDatabase.execSQL("DELETE FROM habits WHERE id = ?", arrayOf<Any>(read))

            assertEquals(listOf(water), completions.forDay(day1).map { it.habitId })
        }

    @Test
    fun deletingADayTakesItsCompletionsWithIt() =
        runTest {
            completions.insert(HabitCompletionEntity(read, day1))
            completions.insert(HabitCompletionEntity(read, day2))

            database.openHelper.writableDatabase.execSQL("DELETE FROM days WHERE epochDay = ?", arrayOf<Any>(day1))

            assertTrue(completions.forDay(day1).isEmpty())
            assertEquals(1, completions.forDay(day2).size)
        }
}

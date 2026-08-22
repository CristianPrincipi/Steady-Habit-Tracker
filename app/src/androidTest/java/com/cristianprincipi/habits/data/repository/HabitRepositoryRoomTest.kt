package com.cristianprincipi.habits.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.testing.createInMemoryDatabase
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.RoomTransactionRunner
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The repository against real SQLite, with the foreign key and the transaction actually enforced.
 *
 * `HabitRepositoryImplTest` covers the same logic on the JVM in a second, and its fakes reproduce
 * the foreign key by hand. This is where that reproduction is checked against the real thing: if
 * Room ever stopped enabling `PRAGMA foreign_keys`, or the constraint were dropped from the schema,
 * the JVM test would keep passing and this one would not.
 */
@RunWith(AndroidJUnit4::class)
class HabitRepositoryRoomTest {
    private val fixedMillis = 1_755_000_000_000L
    private val date = LocalDate.of(2026, 8, 22)
    private val epochDay = date.toEpochDay()
    private val read = HabitId("habit-read")

    private lateinit var database: HabitsDatabase
    private lateinit var repository: HabitRepositoryImpl

    @Before
    fun setUp() =
        runTest {
            database = createInMemoryDatabase()
            repository =
                HabitRepositoryImpl(
                    habitDao = database.habitDao(),
                    dayDao = database.dayDao(),
                    completionDao = database.completionDao(),
                    transactionRunner = RoomTransactionRunner(database),
                    clock = Clock.fixed(Instant.ofEpochMilli(fixedMillis), ZoneOffset.UTC),
                )
            // A completion's other foreign key points at `habits`, so the habit has to exist for
            // any of this to be about the day at all.
            database.habitDao().upsert(
                listOf(
                    HabitEntity(
                        id = read.value,
                        notionPropertyId = "prop-read",
                        notionPropertyName = "Read",
                        color = HabitColor.BLUE.name,
                        startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
                        isActive = true,
                        sortOrder = 0,
                    ),
                ),
            )
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aCheckOffOnADayTheAppHasNeverSeenSatisfiesTheForeignKey() =
        runTest {
            // The whole point of the transaction: the day row does not exist yet, and inserting the
            // completion first would fail this constraint in SQLite rather than in a fake.
            repository.setCompleted(habitId = read, date = date, completed = true)

            val day = database.dayDao().find(epochDay)!!
            assertNull(day.notionPageId)
            assertEquals(fixedMillis, day.localUpdatedAtEpochMillis)
            assertEquals(SyncState.PENDING_PUSH, day.syncState)
            assertEquals(1, database.completionDao().forDay(epochDay).size)
        }

    @Test
    fun theSameCheckOffTwiceLeavesOneRow() =
        runTest {
            repository.setCompleted(habitId = read, date = date, completed = true)
            repository.setCompleted(habitId = read, date = date, completed = true)

            // Proves OnConflictStrategy.IGNORE against the real composite primary key, not a Set.
            assertEquals(1, database.completionDao().forDay(epochDay).size)
        }

    @Test
    fun undoingLeavesTheDayOwedToNotion() =
        runTest {
            repository.setCompleted(habitId = read, date = date, completed = true)

            repository.setCompleted(habitId = read, date = date, completed = false)

            assertTrue(database.completionDao().forDay(epochDay).isEmpty())
            assertEquals(SyncState.PENDING_PUSH, database.dayDao().find(epochDay)!!.syncState)
        }

    @Test
    fun theDayIsListedAsOwingNotionAWrite() =
        runTest {
            // The one signal M4's push path has. If setCompleted stopped stamping the state, the
            // check-off would still look right on screen and would never reach Notion.
            repository.setCompleted(habitId = read, date = date, completed = true)

            val pending = database.dayDao().withState(SyncState.PENDING_PUSH)

            assertEquals(listOf(epochDay), pending.map { it.epochDay })
        }

    @Test
    fun completionsComeBackThroughTheRepositoryAsDomainDates() =
        runTest {
            repository.setCompleted(habitId = read, date = date, completed = true)

            val completions = repository.observeCompletions(from = date, to = date).first()

            assertEquals(listOf(date), completions.map { it.date })
            assertEquals(listOf(read), completions.map { it.habitId })
        }

    @Test
    fun aColourChangeRoundTripsThroughTheDomainModel() =
        runTest {
            // setColor writes HabitColor.name; the mapper reads it back by name. The two halves are
            // written in different files and nothing but this makes them agree.
            repository.setColor(habitId = read, color = HabitColor.PURPLE)

            val habits = repository.observeHabits(includeInactive = false).first()

            assertEquals(HabitColor.PURPLE, habits.single().color)
        }
}

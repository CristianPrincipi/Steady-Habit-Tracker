package com.cristianprincipi.habits.data.repository

import app.cash.turbine.test
import com.cristianprincipi.habits.data.local.TestTransactionRunner
import com.cristianprincipi.habits.data.local.dao.FakeCompletionDao
import com.cristianprincipi.habits.data.local.dao.FakeDayDao
import com.cristianprincipi.habits.data.local.dao.FakeHabitDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
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
 * What the repository decides, tested without a device.
 *
 * The queries themselves are M3.11's job — these fakes run no SQL. What is proved here is the
 * repository's own logic: that entities are mapped away before they leave, that a check-off writes
 * the day before the completion, and that every local edit marks the day as owed to Notion.
 */
class HabitRepositoryImplTest {
    private val fixedMillis = 1_755_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(fixedMillis), ZoneOffset.UTC)

    private val date = LocalDate.of(2026, 8, 22)
    private val epochDay = date.toEpochDay()
    private val read = HabitId("habit-read")

    private fun habit(
        id: String,
        name: String,
        sortOrder: Int,
        isActive: Boolean = true,
    ) = HabitEntity(
        id = id,
        notionPropertyId = "prop-$id",
        notionPropertyName = name,
        color = HabitColor.BLUE.name,
        startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
        isActive = isActive,
        sortOrder = sortOrder,
    )

    private fun repository(
        habitDao: FakeHabitDao = FakeHabitDao(),
        dayDao: FakeDayDao = FakeDayDao(),
        completionDao: FakeCompletionDao = FakeCompletionDao { dayDao.current.containsKey(it) },
    ) = HabitRepositoryImpl(
        habitDao = habitDao,
        dayDao = dayDao,
        completionDao = completionDao,
        transactionRunner = TestTransactionRunner(),
        clock = clock,
    )

    @Test
    fun observeHabitsHidesOneWhoseColumnHasGoneAndOrdersTheRestForDisplay() =
        runTest {
            // ⭐ **The user's rule, at the layer that enforces it** (ADR-081): a habit whose property
            // has left the mapped set is invisible, and there is no second read that would show it.
            // The ordering is the other half — `sortOrder` is the order the user chose in Notion,
            // and the gap the hidden row leaves must not reshuffle what is left.
            val habitDao =
                FakeHabitDao(
                    listOf(
                        habit(id = "b", name = "Water", sortOrder = 2),
                        habit(id = "gone", name = "Journal", sortOrder = 0, isActive = false),
                        habit(id = "a", name = "Read", sortOrder = 1),
                    ),
                )

            repository(habitDao = habitDao).observeHabits().test {
                assertEquals(listOf("Read", "Water"), awaitItem().map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aHiddenHabitReappearsInPlaceWhenItsPropertyComesBack() =
        runTest {
            // The other half of the same rule: invisible *while* it is gone. The row was never
            // deleted, so it comes back carrying its own id — the id every completion in the
            // database points at — rather than arriving as a habit that started today.
            val habitDao =
                FakeHabitDao(
                    listOf(
                        habit(id = "gone", name = "Journal", sortOrder = 0, isActive = false),
                        habit(id = "a", name = "Read", sortOrder = 1),
                    ),
                )

            repository(habitDao = habitDao).observeHabits().test {
                assertEquals(listOf("Read"), awaitItem().map { it.name })

                habitDao.upsert(listOf(habit(id = "gone", name = "Journal", sortOrder = 0)))

                assertEquals(listOf(HabitId("gone"), HabitId("a")), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeHabitsReturnsDomainModelsWithTheEmojiSplitOff() =
        runTest {
            val bookEmoji = "📖"
            val habitDao = FakeHabitDao(listOf(habit(id = "a", name = "$bookEmoji Read", sortOrder = 0)))

            repository(habitDao = habitDao).observeHabits().test {
                val habit = awaitItem().single()

                assertEquals("Read", habit.name)
                assertEquals(bookEmoji, habit.emoji)
                assertEquals(HabitId("a"), habit.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeCompletionsIncludesBothEndsOfTheRangeAndReturnsDates() =
        runTest {
            val completionDao = FakeCompletionDao()
            listOf(9L, 10L, 12L, 13L).forEach {
                completionDao.insert(HabitCompletionEntity(habitId = read.value, epochDay = it))
            }
            val from = LocalDate.ofEpochDay(10)
            val to = LocalDate.ofEpochDay(12)

            repository(completionDao = completionDao).observeCompletions(from = from, to = to).test {
                assertEquals(listOf(from, to), awaitItem().map { it.date })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun checkingOffADayTheAppHasNeverSeenCreatesItsDayRowFirst() =
        runTest {
            // The fake enforces the foreign key, so writing the completion first would throw here
            // exactly as SQLite would on a device.
            val dayDao = FakeDayDao()
            val completionDao = FakeCompletionDao { dayDao.current.containsKey(it) }

            repository(dayDao = dayDao, completionDao = completionDao)
                .setCompleted(habitId = read, date = date, completed = true)

            val day = dayDao.current.getValue(epochDay)
            assertNull(day.notionPageId)
            assertNull(day.remoteLastEditedAtEpochMillis)
            assertEquals(fixedMillis, day.localUpdatedAtEpochMillis)
            assertEquals(SyncState.PENDING_PUSH, day.syncState)
            assertEquals(setOf(HabitCompletionEntity(read.value, epochDay)), completionDao.current)
        }

    @Test
    fun checkingTheSameHabitOffTwiceLeavesOneCompletion() =
        runTest {
            val dayDao = FakeDayDao()
            val completionDao = FakeCompletionDao { dayDao.current.containsKey(it) }
            val repository = repository(dayDao = dayDao, completionDao = completionDao)

            repository.setCompleted(habitId = read, date = date, completed = true)
            repository.setCompleted(habitId = read, date = date, completed = true)

            assertEquals(1, completionDao.current.size)
        }

    @Test
    fun undoingACheckOffRemovesTheCompletionAndStillOwesNotionTheDay() =
        runTest {
            // The page still has to be written: a cleared checkbox is a change Notion needs telling
            // about, and there is no completion row left to carry it.
            val dayDao = FakeDayDao()
            val completionDao = FakeCompletionDao { dayDao.current.containsKey(it) }
            val repository = repository(dayDao = dayDao, completionDao = completionDao)
            repository.setCompleted(habitId = read, date = date, completed = true)

            repository.setCompleted(habitId = read, date = date, completed = false)

            assertTrue(completionDao.current.isEmpty())
            assertEquals(SyncState.PENDING_PUSH, dayDao.current.getValue(epochDay).syncState)
        }

    @Test
    fun editingASyncedDayKeepsWhatNotionKnowsAboutItsPage() =
        runTest {
            // Losing the page id would make the next push create a second page for the same date.
            val synced =
                DayEntity(
                    epochDay = epochDay,
                    notionPageId = "page-1",
                    localUpdatedAtEpochMillis = 1L,
                    remoteLastEditedAtEpochMillis = 42L,
                    syncState = SyncState.SYNCED,
                )
            val dayDao = FakeDayDao(listOf(synced))

            repository(dayDao = dayDao).setCompleted(habitId = read, date = date, completed = true)

            val day = dayDao.current.getValue(epochDay)
            assertEquals("page-1", day.notionPageId)
            assertEquals(42L, day.remoteLastEditedAtEpochMillis)
            assertEquals(SyncState.PENDING_PUSH, day.syncState)
            assertEquals(fixedMillis, day.localUpdatedAtEpochMillis)
        }

    @Test
    fun theLocalEditTimeComesFromTheInjectedClock() =
        runTest {
            // CLAUDE.md section 6: nothing in data code calls now() itself. If it did, this would
            // record wall-clock time rather than the fixed instant.
            val dayDao = FakeDayDao()

            repository(dayDao = dayDao).setCompleted(habitId = read, date = date, completed = true)

            assertEquals(fixedMillis, dayDao.current.getValue(epochDay).localUpdatedAtEpochMillis)
        }

    @Test
    fun setColorStoresTheEnumNameTheMapperReadsBack() =
        runTest {
            val habitDao = FakeHabitDao(listOf(habit(id = "a", name = "Read", sortOrder = 0)))

            repository(habitDao = habitDao).setColor(habitId = HabitId("a"), color = HabitColor.PURPLE)

            assertEquals(HabitColor.PURPLE.name, habitDao.current.single().color)
        }

    @Test
    fun aColourChangeReachesAScreenThatIsAlreadyWatching() =
        runTest {
            // ⭐⭐ **The property an app-local presentation store exists for**, and the one thing
            // about colour that nothing asserted. Every other colour test writes and *then* reads,
            // which proves the value was stored — not that anything looking at it finds out.
            //
            // What this pins is the case M7.5 will produce: the manage-habits screen sets a colour
            // while the Week cards behind it are still subscribed, and they have to repaint without
            // the user navigating away and back. A `distinctUntilChanged` keyed on anything that
            // ignores colour — an id list, say, which is the obvious thing to "optimise" this flow
            // with — would swallow the change and leave the app looking broken in a way no
            // read-back test could see.
            val habitDao = FakeHabitDao(listOf(habit(id = "a", name = "Read", sortOrder = 0)))
            val repository = repository(habitDao = habitDao)

            repository.observeHabits().test {
                assertEquals(HabitColor.BLUE, awaitItem().single().color)

                repository.setColor(habitId = HabitId("a"), color = HabitColor.PURPLE)

                assertEquals(HabitColor.PURPLE, awaitItem().single().color)
            }
        }

    @Test
    fun setColorTouchesNoDayAndOwesNotionNothing() =
        runTest {
            // Colour is app-local: the user's database has no colour property, so this edit can
            // never be something Notion is behind on.
            val habitDao = FakeHabitDao(listOf(habit(id = "a", name = "Read", sortOrder = 0)))
            val dayDao = FakeDayDao()

            repository(habitDao = habitDao, dayDao = dayDao).setColor(HabitId("a"), HabitColor.GREEN)

            assertTrue(dayDao.current.isEmpty())
        }
}

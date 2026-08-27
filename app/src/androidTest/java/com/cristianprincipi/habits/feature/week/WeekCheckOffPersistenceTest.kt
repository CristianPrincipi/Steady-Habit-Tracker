package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.RoomTransactionRunner
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.repository.HabitRepositoryImpl
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * A check-off made on the Week screen, and what is left of it afterwards — **M6's Definition of
 * Done, and the one clause nothing had ever checked**.
 *
 * Every layer here is the real one: SQLite on a real file, `HabitRepositoryImpl`, `ToggleCompletion`,
 * `GetHabitsForWeek`, `WeekViewModel`, `WeekContent`. The only thing faked is the clock, so that
 * "today" is a fixed Saturday rather than whenever the suite happens to run.
 *
 * ### What "restart" means here, exactly
 *
 * A test runs *inside* the process it is testing, so it cannot restart it. What it can do — and what
 * [aCheckOffMadeOnTheWeekScreenSurvivesTheStackBeingRebuiltFromTheFile] does — is take the screen off
 * the tree, open a **second Room instance on the same file**, and build a new repository, new use
 * cases and a new ViewModel over it. Nothing but bytes on disk connects the two stacks, so a tick
 * that survives that was committed to the file rather than held in memory.
 *
 * The remaining gap is the process itself, and it is closed by hand instead: `Plan.md` M6.5 records
 * the emulator walk — check a day off, `am force-stop`, relaunch, still ticked.
 *
 * ### ⚠ It also closes a caveat M4 left standing
 *
 * M4.15 proved the push path against days whose `PENDING_PUSH` had been **written into Room by
 * hand**, because no check-off UI existed yet. [aCheckOffFromTheUiLeavesTheDayPendingForTheSyncEngine]
 * is that claim made from the other end: a finger on a pill leaves exactly the row the sync engine
 * was tested against.
 */
@RunWith(AndroidJUnit4::class)
class WeekCheckOffPersistenceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Saturday 22 August 2026, at noon UTC — so the week is Mon 17 – Sun 23. */
    private val clock = Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC)
    private val monday = startOfWeek(LocalDate.of(2026, 8, 22))

    /** A day already past, so `ToggleCompletion` has no reason to refuse it. */
    private val wednesday = monday.plusDays(2)

    private val habitId = HabitId("habit-read")

    /** What `HabitMapper` splits the stored property name into, and therefore what a pill announces. */
    private val habitName = "Read"

    /** Every instance opened during a test, so `tearDown` closes all of them. */
    private val opened = mutableListOf<HabitsDatabase>()

    private lateinit var database: HabitsDatabase

    /** Which ViewModel the composition is rendering, or none at all. */
    private val live = mutableStateOf<WeekViewModel?>(null)

    @Before
    fun setUp() {
        // A file left behind by an earlier run would bring that run's check-offs with it, and what
        // is in the file is this test's entire subject.
        context.deleteDatabase(DATABASE)
        database = openDatabase()
        runBlocking {
            database.habitDao().upsert(
                listOf(
                    HabitEntity(
                        id = habitId.value,
                        notionPropertyId = "prop-read",
                        notionPropertyName = "📖 Read",
                        color = HabitColor.BLUE.name,
                        startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
                        isActive = true,
                        sortOrder = 0,
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        opened.forEach(HabitsDatabase::close)
        context.deleteDatabase(DATABASE)
    }

    /**
     * A real database on a real file — **not** `createInMemoryDatabase`, which is what the DAO tests
     * use and what would leave this test proving nothing at all.
     */
    private fun openDatabase(): HabitsDatabase =
        Room
            .databaseBuilder(context, HabitsDatabase::class.java, DATABASE)
            .build()
            .also { instance -> opened += instance }

    /** The production stack, top to bottom, over one database instance. */
    private fun weekViewModelOver(instance: HabitsDatabase): WeekViewModel {
        val repository =
            HabitRepositoryImpl(
                habitDao = instance.habitDao(),
                dayDao = instance.dayDao(),
                completionDao = instance.completionDao(),
                transactionRunner = RoomTransactionRunner(instance),
                clock = clock,
            )
        return WeekViewModel(
            getHabitsForWeek = GetHabitsForWeek(repository, clock),
            toggleCompletion = ToggleCompletion(repository, clock),
            clock = clock,
        )
    }

    private fun showWeek() {
        live.value = weekViewModelOver(database)
        composeTestRule.setContent {
            HabitsTheme {
                live.value?.let { model ->
                    val state by model.uiState.collectAsStateWithLifecycle()
                    WeekContent(state = state, onEvent = model::onEvent)
                }
            }
        }
    }

    private fun description(date: LocalDate): String =
        context.getString(R.string.week_pill_description).format(habitName, accessibleDate(date, Locale.getDefault()))

    private fun pill(date: LocalDate): SemanticsNodeInteraction =
        composeTestRule.onNodeWithContentDescription(description(date), useUnmergedTree = true)

    /**
     * Waits for the pill to exist at all.
     *
     * A real ViewModel emits `Loading` first, and that draws nothing — so every test here starts on
     * an empty screen. Room is fast; it is not synchronous.
     */
    private fun awaitPill(date: LocalDate): SemanticsNodeInteraction {
        composeTestRule.waitUntil(TIMEOUT_MILLIS) { pillCount(date) == 1 }
        return pill(date)
    }

    /** Waits for the pill to report [ticked] — the write coming back through Room's own `Flow`. */
    private fun awaitPill(
        date: LocalDate,
        ticked: Boolean,
    ) {
        val state = if (ticked) isOn() else isOff()
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasContentDescription(description(date)) and state, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size == 1
        }
    }

    private fun pillCount(date: LocalDate): Int =
        composeTestRule
            .onAllNodesWithContentDescription(description(date), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size

    private fun dayRow() = runBlocking { database.dayDao().find(wednesday.toEpochDay()) }

    private fun completionsOnWednesday(): List<String> =
        runBlocking { database.completionDao().forDay(wednesday.toEpochDay()) }.map { row -> row.habitId }

    @Test
    fun aCheckOffMadeOnTheWeekScreenSurvivesTheStackBeingRebuiltFromTheFile() {
        showWeek()
        awaitPill(wednesday).assertIsOff()

        pill(wednesday).performClick()
        // The optimistic local write, watched arriving back through Room rather than assumed.
        awaitPill(wednesday, ticked = true)

        // The restart. Taking the screen down first is not tidiness: without it, the assertion at the
        // end could be satisfied by the tree that is already on screen.
        composeTestRule.runOnIdle { live.value = null }
        composeTestRule.waitUntil(TIMEOUT_MILLIS) { pillCount(wednesday) == 0 }
        composeTestRule.runOnIdle { live.value = weekViewModelOver(openDatabase()) }

        awaitPill(wednesday, ticked = true)
        pill(wednesday).assertIsOn()
    }

    @Test
    fun aCheckOffFromTheUiLeavesTheDayPendingForTheSyncEngine() {
        showWeek()

        awaitPill(wednesday).performClick()
        awaitPill(wednesday, ticked = true)

        assertEquals(listOf(habitId.value), completionsOnWednesday())
        assertEquals(SyncState.PENDING_PUSH, dayRow()?.syncState)
    }

    @Test
    fun unTickingThroughTheUiTakesTheCompletionBackOutAndStillOwesNotionAPush() {
        // A day the user takes back is not a day the app forgets: the checkbox has to go false
        // upstream too, so the day stays PENDING_PUSH with nothing on it. There is no delete here
        // and no tombstone to write (ADR-036).
        showWeek()
        awaitPill(wednesday).performClick()
        awaitPill(wednesday, ticked = true)

        pill(wednesday).performClick()
        awaitPill(wednesday, ticked = false)

        assertEquals(emptyList<String>(), completionsOnWednesday())
        assertEquals(SyncState.PENDING_PUSH, dayRow()?.syncState)
    }

    private companion object {
        /** Its own file, so nothing here can touch the `habits.db` a real connection fills. */
        const val DATABASE = "week-check-off-test.db"

        /** Generous: this waits on Room's executor and the main looper, not on a test scheduler. */
        const val TIMEOUT_MILLIS = 5_000L
    }
}

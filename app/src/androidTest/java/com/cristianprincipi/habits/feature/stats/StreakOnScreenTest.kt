package com.cristianprincipi.habits.feature.stats

import android.content.Context
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.testing.FreshInstallRule
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.gallery.ShellHarnessActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.time.LocalDate
import java.util.Locale

/**
 * ⭐⭐ **The clause M6 asked for and nothing could discharge: a streak seen changing.**
 *
 * `Plan.md` M6's Definition of Done asks that "the streak updates". ADR-092 §2 split that in two and
 * could only close half: `StatsViewModelTest` proves the number changes **in the state**, against a
 * real `CalculateStreak` and a real repository write, and it has done since M9.1. What no test could
 * reach was the other half — a **rendered** streak moving because somebody checked a day off.
 *
 * ### Why nothing could, until now
 *
 * `ShellHarnessActivity` renders the real shell over the **real Room database**, and Room stays
 * empty until a Notion connection imports habits — which costs the user a token paste and can never
 * live in a fixture (`CLAUDE.md` §3.6). So `HabitsShellNavigationTest` drives the shell against
 * empty screens, and the check-off loop has never met the Stats screen in a test at all.
 *
 * The opening is that `FreshInstallRule` clears `filesDir/datastore` and **not** Room. A rule
 * ordered ahead of the activity can therefore put habits on disk, and the app reads them on its
 * first query like any other data.
 *
 * ### What this drives, end to end and through nothing fake
 *
 * Real DAOs, real repository, real `ToggleCompletion`, real `CalculateStreak`, real ViewModels, real
 * navigation. The only thing arranged is the starting data. A tap on a Week pill writes to SQLite;
 * the Stats tab reads it back and re-folds it; the tile redraws.
 *
 * ### ⚠ It cleans up after itself, and that is not politeness
 *
 * Room survives between tests in a run — `FreshInstallRule` does not touch it — so rows left behind
 * would reach `HabitsShellNavigationTest`, which asserts against empty screens. The rule below
 * deletes what it wrote whether the test passes or fails.
 */
@RunWith(AndroidJUnit4::class)
class StreakOnScreenTest {
    private val composeTestRule = createAndroidComposeRule<ShellHarnessActivity>()

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(FreshInstallRule())
            .around(SeededHabitRule())
            .around(composeTestRule)

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun tab(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(isSelectable())

    private fun heading(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(hasNoClickAction())

    /**
     * The **current streak** tile, addressed by its own label rather than by its number.
     *
     * ⚠ Four tiles print bare integers through one string resource, so matching on the number alone
     * finds whichever tiles happen to share it. The label is what says *which* number this is —
     * still without adding a test tag to production code for a test's convenience.
     *
     * ⚠⚠ **This was `hasAnySibling` until M13.2, and the change is the point of that step.** A tile
     * laid its label and value out as two sibling `Text`s, which is why a sibling lookup worked —
     * and it is exactly why a screen reader stopped twice and announced a bare "9". `StatTile` now
     * merges, so label and value are **one** node rather than two, and the finder asks that node for
     * both. The assertion got stronger on the way: it is now the same node carrying both facts,
     * rather than two nodes that happen to be adjacent.
     */
    private fun currentStreakTile(value: Int) = tile(R.string.stats_tile_current_streak, value)

    /** Today's pill for the seeded habit, named the way a screen reader would hear it. */
    private fun todaysPill() =
        composeTestRule.onNodeWithContentDescription(
            string(R.string.week_pill_description).format(HABIT_NAME, accessibleDate(today, Locale.getDefault())),
            useUnmergedTree = true,
        )

    @Test
    fun checkingOffTodayOnWeekMovesTheCurrentStreakTileOnStats() {
        // ⭐⭐ The whole of M6's carried clause, in five lines. Everything before this proved the
        // number was right somewhere; this is the number changing where a person can see it.
        heading(R.string.screen_title_week).assertExists()

        tab(R.string.tab_stats).performClick()
        currentStreakTile(SEEDED_STREAK).assertExists()

        tab(R.string.tab_week).performClick()
        todaysPill().performClick()

        tab(R.string.tab_stats).performClick()
        currentStreakTile(SEEDED_STREAK + 1).assertExists()
    }

    @Test
    fun theTileThatMovedIsTheCurrentStreakAndNotTheBest() {
        // ⚠ The mutant this rules out is a tile wired to the wrong half of `HabitStats` — best where
        // current belongs. The seeded history makes the two differ on purpose: a long run a month
        // ago that a check-off today cannot touch, and a short live one that it can.
        tab(R.string.tab_stats).performClick()
        currentStreakTile(SEEDED_STREAK).assertExists()
        bestStreakTile(SEEDED_BEST).assertExists()

        tab(R.string.tab_week).performClick()
        todaysPill().performClick()
        tab(R.string.tab_stats).performClick()

        currentStreakTile(SEEDED_STREAK + 1).assertExists()
        bestStreakTile(SEEDED_BEST).assertExists()
    }

    private fun bestStreakTile(value: Int) = tile(R.string.stats_tile_best_streak, value)

    /** One merged stat tile: the node that carries this label and this value together (M13.2). */
    private fun tile(
        labelRes: Int,
        value: Int,
    ) = composeTestRule.onNode(
        hasText(string(labelRes)) and hasText(string(R.string.stats_count_value).format(value)),
    )

    private companion object {
        val today: LocalDate = LocalDate.now()

        const val HABIT_ID = "seeded-read"
        const val HABIT_NAME = "Read"

        /**
         * A live run ending **yesterday**, so today is still open for the test to check off.
         *
         * `CalculateStreak` counts back from yesterday while today is undone — that is deliberate
         * (M3.9), and it is what makes the current streak a number that goes *up* when today is
         * ticked rather than one that was already counting today.
         */
        const val SEEDED_STREAK = 3

        /** An older, longer run, far enough back that today's tap cannot join it. */
        const val SEEDED_BEST = 12

        const val BEST_RUN_ENDS_DAYS_AGO = 40L
    }

    /**
     * Puts one habit and its history into the app's own database, ahead of the activity launching.
     *
     * A rule rather than an `@Before`, for `FreshInstallRule`'s reason: `createAndroidComposeRule`
     * launches the activity from its own `before()`, and JUnit runs every rule outside the `@Before`
     * methods — so by the time an `@Before` ran, Week would already have composed against an empty
     * table.
     *
     * It opens a second Room instance on the same file rather than reaching into the app's Hilt
     * graph, which `ShellHarnessActivity`'s KDoc records as a dead end. Two instances do not share
     * an invalidation tracker, so this only works because the writes land **before** the app's first
     * read — which is exactly the ordering a rule gives.
     */
    private class SeededHabitRule : TestRule {
        override fun apply(
            base: Statement,
            description: Description,
        ): Statement =
            object : Statement() {
                override fun evaluate() {
                    write { database ->
                        database.habitDao().upsert(listOf(habitRow()))
                        seededDays().forEach { day ->
                            database.dayDao().upsert(dayRow(day))
                            database.completionDao().insert(HabitCompletionEntity(HABIT_ID, day))
                        }
                    }
                    try {
                        base.evaluate()
                    } finally {
                        // Whether the test passed or not: the next test in the run expects an empty
                        // app, and Room is the one store `FreshInstallRule` leaves alone.
                        write { database ->
                            database.completionDao().deleteAll()
                            database.dayDao().deleteAll()
                            database.habitDao().deleteAll()
                        }
                    }
                }
            }

        private fun write(block: suspend (HabitsDatabase) -> Unit) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database =
                Room
                    .databaseBuilder(context, HabitsDatabase::class.java, HabitsDatabase.NAME)
                    .build()
            try {
                runBlocking { block(database) }
            } finally {
                database.close()
            }
        }

        /** The live run ending yesterday, plus an older and longer one. */
        private fun seededDays(): List<Long> {
            val live = (1..SEEDED_STREAK).map { back -> today.minusDays(back.toLong()).toEpochDay() }
            val best =
                (0 until SEEDED_BEST).map { back ->
                    today.minusDays(BEST_RUN_ENDS_DAYS_AGO + back).toEpochDay()
                }
            return live + best
        }

        private fun habitRow() =
            HabitEntity(
                id = HABIT_ID,
                notionPropertyId = "prop-$HABIT_ID",
                // The mapper splits a leading emoji off the property name, so the habit renders as
                // "Read" with a book beside it — and the Week pill announces the name alone.
                notionPropertyName = "📖 $HABIT_NAME",
                color = HabitColor.BLUE.name,
                startDateEpochDay = today.minusDays(YEAR_AND_A_BIT).toEpochDay(),
                isActive = true,
                sortOrder = 0,
            )

        private fun dayRow(epochDay: Long) =
            DayEntity(
                epochDay = epochDay,
                notionPageId = null,
                localUpdatedAtEpochMillis = null,
                remoteLastEditedAtEpochMillis = null,
                // SYNCED, not PENDING_PUSH: this is history the app is pretending to have imported,
                // and marking it owed would be arranging for a push that no test is about.
                syncState = SyncState.SYNCED,
            )

        private companion object {
            const val YEAR_AND_A_BIT = 400L
        }
    }
}

package com.cristianprincipi.habits.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.datetime.weekdayLabels
import com.cristianprincipi.habits.core.designsystem.component.ConnectionStatusCard
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.feature.history.YearContent
import com.cristianprincipi.habits.feature.history.YearUiState
import com.cristianprincipi.habits.feature.stats.StatsContent
import com.cristianprincipi.habits.feature.stats.StatsUiState
import com.cristianprincipi.habits.feature.week.WeekContent
import com.cristianprincipi.habits.feature.week.WeekUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * M13.2 — **what a screen reader stops on, and in what order.**
 *
 * ### Why this is a different test from [AccessibilitySweepTest]
 *
 * The sweep asks whether every actionable node has a *name*. This asks the question one level up:
 * given that everything is named, **is it grouped into the right number of things to hear?** They
 * fail independently. Every assertion below was green on the naming sweep and wrong in the ear.
 *
 * The audit that produced these was a transcript, not a reading: a walk of the *merged* semantics
 * tree — the tree the accessibility framework serves — printing every stop, on thirteen screens.
 * Three findings came out of it and they are one finding wearing three hats. `HabitsCard` is a plain
 * `Surface` + `Column`, so **nothing in this app merged**: a Week card was two stops before its
 * pills, a stat tile was two, the connection card was two, and the second stop was in every case a
 * bare figure — "6 of 7", "9", "Last synced 3 minutes ago" — that says nothing about what it counts.
 * Touch exploration lands on those directly; it does not arrive from the label above.
 *
 * ⚠⚠ **The fourth was the opposite problem: noise.** `WeekPills` draws a `Mon…Sun` heading row, and
 * it was read out per card — seven stops × five habits — immediately before pills that each already
 * announce "Read, Monday, August 17, 2026" in full.
 *
 * ### What it does not assert, and why
 *
 * **Not the transcript itself.** A golden-file assertion over the whole tree would fail on every
 * copy change and pass on none of the things this step is about. Each test below names one grouping
 * decision and the reason it was made, so a failure says which decision was reversed.
 *
 * ⚠ **Nor the traversal order the framework finally serves.** Compose sorts nodes for accessibility,
 * and this reads tree order. It is a good approximation and it is not TalkBack. **The judgement
 * belongs to the user with TalkBack running**, which is what M13.2's own wording asks for; this file
 * exists so that what they approve cannot then drift back.
 */
@RunWith(AndroidJUnit4::class)
class SemanticsGroupingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int) = context.getString(id)

    /** One entry per stop a screen reader makes, in tree order: what it would announce. */
    private fun stops(): List<String> {
        fun walk(node: SemanticsNode): List<String> {
            val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(", ")
            val self = listOfNotNull(spoken ?: text)
            return self + node.children.flatMap(::walk)
        }
        return walk(composeTestRule.onRoot().fetchSemanticsNode())
    }

    private fun show(content: @Composable () -> Unit) {
        composeTestRule.setContent { HabitsTheme { content() } }
    }

    private fun showWeek() =
        show {
            WeekContent(
                state =
                    WeekUiState.Content(
                        weekStart = startOfWeek(SampleHabits.referenceDate),
                        habits = SampleHabits.weekPeriods(SampleHabits.referenceDate),
                        isCurrentWeek = false,
                        currentYear = SampleHabits.referenceDate.year,
                    ),
                onEvent = {},
                onOpenSyncStatus = {},
            )
        }

    /**
     * ⚠⚠ **35 stops that said nothing.**
     *
     * The `Mon…Sun` row is a column heading: it labels the pill *below* it, which is a relationship
     * only an eye follows. A screen reader travels along the row, and the pill it reaches next
     * already carries the weekday in full. Seven per card, five cards, immediately before the seven
     * that say it properly.
     *
     * ⭐ Asserted against `weekdayLabels(Locale)` — the app's own list — rather than a hardcoded
     * `"Mon"`, so a locale change cannot make this pass by looking for a word nobody draws.
     */
    @Test
    fun theWeekdayHeadingRowIsSilentBecauseEveryPillBelowItSaysTheDayInFull() {
        showWeek()

        val spoken = stops()
        val labels = weekdayLabels(Locale.getDefault())
        val heard = labels.filter { label -> spoken.any { it == label } }
        assertTrue("the weekday heading row is being read out: $heard", heard.isEmpty())

        // ⭐ The control on the clearing, and the clearing is only right while it holds: silence up
        // there is affordable **because** the seven pills below speak. One habit's name should be
        // heard eight times — once in its merged header, once per pill. Without this, deleting the
        // pills' descriptions would leave the assertion above perfectly green.
        val name = SampleHabits.habits().first().name
        assertEquals(
            "the pills are not announcing the habit and day — the heading row's silence is now a real loss",
            DAYS_PER_WEEK + 1,
            spoken.count { it.contains(name) },
        )
    }

    /**
     * A habit card opens with one stop, not two.
     *
     * "Read", then "6 of 7" — the second is a bare ratio, and a user exploring by touch lands on it
     * without having heard the first. Merged, the card announces the habit and its week together.
     */
    @Test
    fun aHabitCardOpensWithOneStopCarryingBothTheNameAndItsFigure() {
        showWeek()

        val name = SampleHabits.habits().first().name
        val together = stops().filter { it.contains(name) && it.contains(" of ") }

        assertEquals(
            "the card header is not merged — the name and its figure are separate stops: ${stops().take(12)}",
            1,
            together.size,
        )
    }

    /**
     * ⚠⚠ **The one place in the app that announced a value before saying what it measured.**
     *
     * The `COMPLETION` eyebrow is drawn *beneath* the figure, and reading order is drawing order, so
     * the ear got "87%" and only then the word for it. The design does not move; the description
     * puts the two in the order an ear needs.
     */
    @Test
    fun theYearCardSaysWhatItsPercentageMeasuresInTheSameBreath() {
        show {
            YearContent(
                state = YearUiState.Content(SampleHabits.years(SampleHabits.referenceDate)),
                onOpenSyncStatus = {},
            )
        }

        val spoken = stops()
        val eyebrow = string(R.string.year_completion_eyebrow)

        // The bare eyebrow must never be a stop of its own, and the figure must never be one either.
        assertTrue(
            "\"$eyebrow\" is still announced on its own, after the number it labels",
            spoken.none { it == eyebrow },
        )
        val phrased = spoken.filter { it.endsWith("% completion") }
        assertTrue(
            "no card announces its percentage together with what it measures: ${spoken.take(10)}",
            phrased.isNotEmpty(),
        )
    }

    /** A stat tile is one stop. A bare "9" is a number about nothing. */
    @Test
    fun aStatTileSaysItsLabelAndItsValueInOneStop() {
        show {
            StatsContent(
                state =
                    StatsUiState.Content(
                        habits = SampleHabits.habits(),
                        selected =
                            SampleHabits.stats(
                                id =
                                    SampleHabits
                                        .habits()
                                        .first()
                                        .id.value,
                            ),
                    ),
                onEvent = {},
                onOpenSyncStatus = {},
            )
        }

        val spoken = stops()
        val label = string(R.string.stats_tile_current_streak)

        assertTrue(
            "\"$label\" is still a stop on its own, with its number in the next one",
            spoken.none { it == label },
        )
        assertTrue(
            "no stop carries \"$label\" together with a value: ${spoken.take(12)}",
            spoken.any { it.startsWith(label) && it.length > label.length },
        )
    }

    /** The connection card is one status in two lines, and the second alone does not say what of. */
    @Test
    fun theConnectionCardIsOneStatusRatherThanTwoLines() {
        show { ConnectionStatusCard(ConnectionSummary(isConnected = true, lastSynced = Elapsed.Minutes(3L))) }

        val spoken = stops()
        val headline = string(R.string.sync_connected)

        assertTrue("\"$headline\" is a stop on its own — the card is not merged", spoken.none { it == headline })
        assertEquals("the card should be exactly one stop: $spoken", 1, spoken.size)
        assertTrue("the merged stop lost the headline: $spoken", spoken.single().contains(headline))
    }

    /**
     * ⚠⚠ **The control, and ADR-108 §2 is why it is here.**
     *
     * Four of the five tests above assert an *absence* — that some string is no longer a stop of its
     * own — and an absence passes for free the moment [stops] stops finding anything. A walk that
     * silently returned an empty list would turn this whole file green. This is the shape that
     * caught a vacuous sweep in the same hour it was written, rather than a milestone later.
     *
     * Two unmerged `Text`s in a plain `Column` must be **two** stops. If they are ever one, the walk
     * is not measuring what this file claims it measures.
     */
    @Test
    fun andAnUnmergedColumnStillReadsAsTwoStops() {
        show {
            Column {
                Text(text = CONTROL_FIRST)
                Text(text = CONTROL_SECOND)
            }
        }

        val spoken = stops()

        assertTrue("the walk is not finding plain text at all: $spoken", spoken.contains(CONTROL_FIRST))
        assertEquals("two sibling texts should be two stops, not one: $spoken", 2, spoken.size)
    }

    private companion object {
        /** Seven pills, and the header above them makes eight. */
        const val DAYS_PER_WEEK = 7

        const val CONTROL_FIRST = "Control label"
        const val CONTROL_SECOND = "Control value"
    }
}

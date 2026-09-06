package com.cristianprincipi.habits.feature.stats

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.shortMonthLabel
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.CompletionRate
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.MonthlyCompletions
import com.cristianprincipi.habits.domain.model.Streak
import com.cristianprincipi.habits.domain.usecase.HabitStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.Locale

/**
 * The Stats screen on a device, driven through its stateless half.
 *
 * ⚠ **Most of what this file protects is invisible.** `HabitChip` was built in M2 and looks right on
 * its own; what M9.2 adds is the row around it, and the row's whole contribution — `selectableGroup`
 * — is **semantics-only**. Delete it and nothing on screen moves, which is precisely the shape of
 * claim M8.5 found nothing was keeping true (ADR-089). So it is asserted here rather than described
 * in a KDoc.
 *
 * The habits are `SampleHabits`' five, whose names and emoji are the user's real ones.
 */
@RunWith(AndroidJUnit4::class)
class StatsRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val habits = SampleHabits.habits()
    private val selected = habits.first()

    private val chosen = mutableListOf<HabitId>()
    private var syncStatusOpened = 0

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** The pill's own label, built the way `HabitChip` builds it: `{emoji} {name}`. */
    private fun label(habit: Habit) = "${habit.emoji} ${habit.name}"

    private fun showStats(selectedHabit: Habit = selected) {
        composeTestRule.setContent {
            HabitsTheme {
                StatsContent(
                    state =
                        StatsUiState.Content(
                            habits = habits,
                            selected = SampleHabits.stats(id = selectedHabit.id.value),
                        ),
                    onEvent = { event ->
                        if (event is StatsEvent.HabitSelected) chosen += event.habitId
                    },
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }
    }

    /** The chart's spoken form, assembled the way `StatsContent` assembles it. */
    private fun spokenChart(stats: HabitStats): String {
        val locale = Locale.getDefault()
        val months =
            stats.months.joinToString(string(R.string.stats_chart_month_separator)) { month ->
                string(
                    R.string.stats_chart_month,
                ).format(locale, shortMonthLabel(month.month, locale), month.completions)
            }
        return string(R.string.stats_chart_description).format(locale, months)
    }

    private fun showEmpty(reason: HabitsEmptyReason) {
        composeTestRule.setContent {
            HabitsTheme {
                StatsContent(
                    state = StatsUiState.Empty(reason),
                    onEvent = { },
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }
    }

    @Test
    fun theTitleIsStatisticsEvenThoughTheTabSaysStats() {
        // The design's own asymmetry (`docs/design-spec.md` §1), not a mistake to tidy up.
        showStats()

        composeTestRule.onNodeWithText(string(R.string.screen_title_stats)).assertIsDisplayed()
    }

    @Test
    fun everyHabitGetsAPillReadingItsEmojiAndName() {
        showStats()

        habits.forEach { habit ->
            composeTestRule.onNodeWithText(label(habit)).assertExists()
        }
    }

    @Test
    fun exactlyOnePillIsAnnouncedSelectedAndItIsTheChosenOne() {
        // ⭐ `selected` is what a screen reader reads out, and it is not the same thing as the fill
        // colour: a row that coloured the right pill while reporting every one of them unselected
        // would look perfect and announce nothing. The colour is `HabitChip`'s and M2's; this is
        // the row's claim about which habit the rest of the screen is about.
        showStats(selectedHabit = habits[2])

        composeTestRule.onNodeWithText(label(habits[2])).assertIsSelected()
        habits.filterIndexed { index, _ -> index != 2 }.forEach { habit ->
            composeTestRule.onNodeWithText(label(habit)).assertIsNotSelected()
        }
    }

    @Test
    fun theRowIsASelectableGroup() {
        // ⚠⚠ The mutant: drop `Modifier.selectableGroup()` from the row. Nothing on screen moves —
        // it adds no size, no colour and no padding — and every other test in this file still
        // passes. What is lost is a screen reader saying "2 of 5" instead of five unrelated
        // controls, which is the whole reason `HabitChip` uses `selectable` rather than `clickable`
        // and says so in its KDoc.
        showStats()

        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertExists()
    }

    @Test
    fun tappingAPillAsksForThatHabitAndNoOther() {
        showStats()

        composeTestRule.onNodeWithText(label(habits[3])).performClick()

        assertEquals(listOf(habits[3].id), chosen)
    }

    @Test
    fun tappingTheAlreadySelectedPillStillReportsIt() {
        // Not a no-op on purpose: the ViewModel's selection is an id it re-resolves, so re-sending
        // the current one is harmless, and swallowing it here would be this row deciding what the
        // ViewModel already decides. `selectable` fires on the selected item too.
        showStats()

        composeTestRule.onNodeWithText(label(selected)).performClick()

        assertEquals(listOf(selected.id), chosen)
    }

    @Test
    fun aPillIsBigEnoughToAimAtEvenThoughItIsDrawnSmaller() {
        // ⚠⚠ **This asserts TOUCH bounds, and the difference cost this step a red run.**
        // `assertHeightIsAtLeast` reads the *layout* bounds of the semantics node, and here they
        // are **27.8dp** — because `minimumInteractiveComponentSize()` measures its child with the
        // original constraints and then centres it in a 48dp box, so the `selectable` modifier
        // inside that box still reports the drawn size. `WeekPills` passes the same assertion only
        // because it uses `heightIn(min = ...)`, which pushes the minimum *down* into the child.
        //
        // What the accessibility rule is actually about is the touchable area, and Compose expands
        // that to `ViewConfiguration`'s 48dp at the pointer layer whatever the layout says. So the
        // honest assertion is this one — and 48dp is Android's number, a literal rather than a
        // `Sizing` token, the same call `WeekTouchTargetTest` makes one screen along.
        showStats()

        composeTestRule.onNodeWithText(label(selected)).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun theYearOverviewCardIsThereWithItsEyebrow() {
        // The grid under it cannot be asserted from here: `YearDotGrid` clears its own semantics
        // (ADR-089), which is deliberate — a screen reader cannot usefully be read 365 coloured
        // squares. Its pixels are `YearDotGridTest`'s, its window is `StatsViewModelTest`'s, and
        // what is left for this file is that the section exists at all.
        showStats()

        composeTestRule.onNodeWithText(string(R.string.stats_year_overview_eyebrow)).assertIsDisplayed()
    }

    @Test
    fun theEmptyScreenHasNoYearOverviewEither() {
        // No habits means no habit to draw a year for. The eyebrow without a grid under it would
        // be a section heading for nothing.
        showEmpty(HabitsEmptyReason.SyncPending)

        composeTestRule.onNodeWithText(string(R.string.stats_year_overview_eyebrow)).assertDoesNotExist()
    }

    @Test
    fun theFourTilesShowTheSelectedHabitsFigures() {
        // ⭐ Every number checked against the use case's own answer rather than a literal, so the
        // test cannot drift from the arithmetic: a tile wired to the wrong half of `HabitStats` —
        // best streak where current belongs, say — fails here while still looking plausible.
        val stats = SampleHabits.stats(id = selected.id.value)
        showStats()

        composeTestRule.onNodeWithText(string(R.string.stats_tile_completed_days)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.stats_tile_completion_rate)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.stats_tile_current_streak)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.stats_tile_best_streak)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.percent_value).format(stats.rate.percent)).assertExists()
        composeTestRule
            .onAllNodesWithText(string(R.string.stats_count_value).format(stats.rate.completedDays))
            .assertCountEquals(1)
    }

    @Test
    fun theTilesFollowTheSelection() {
        // The mutant: draw `habits.first()` rather than `state.selected`. Two habits with different
        // rates are all it takes, and `SampleHabits` gives every habit its own keep-rate.
        val other = habits[3]
        val theirs = SampleHabits.stats(id = other.id.value)
        val firsts = SampleHabits.stats(id = selected.id.value)
        assertNotEquals(firsts.rate.percent, theirs.rate.percent)

        showStats(selectedHabit = other)

        composeTestRule.onNodeWithText(string(R.string.percent_value).format(theirs.rate.percent)).assertExists()
        composeTestRule
            .onAllNodesWithText(string(R.string.percent_value).format(firsts.rate.percent))
            .assertCountEquals(0)
    }

    @Test
    fun theEmptyScreenHasNoTilesEither() {
        showEmpty(HabitsEmptyReason.SyncPending)

        composeTestRule.onNodeWithText(string(R.string.stats_tile_current_streak)).assertDoesNotExist()
    }

    @Test
    fun theMonthlyChartIsThereWithItsEyebrow() {
        showStats()

        composeTestRule.onNodeWithText(string(R.string.stats_monthly_eyebrow)).assertIsDisplayed()
    }

    @Test
    fun theChartSaysEveryMonthAndItsCountOutLoud() {
        // ⭐⭐ This is the only assertion that can reach the chart at all, and it is not a
        // consolation prize. The curve is one `Canvas` with no text nodes in it, so what a test can
        // check is what a screen reader gets — and `MonthlyChart` speaks precisely *because*
        // nothing else on this screen prints a monthly figure (ADR-089 is why the year grid beside
        // it stays silent, and neither half of that reasoning survives here).
        //
        // The months and counts come from the use case's own answer, so a chart handed the wrong
        // habit's buckets, or buckets in the wrong order, fails here.
        val stats = SampleHabits.stats(id = selected.id.value)
        showStats()

        composeTestRule.onNodeWithContentDescription(spokenChart(stats)).assertExists()
    }

    @Test
    fun theChartFollowsTheSelection() {
        // The same mutant `theTilesFollowTheSelection` rules out, one card lower: draw
        // `habits.first()` instead of `state.selected` and every habit charts the same year.
        val other = habits[3]
        val theirs = SampleHabits.stats(id = other.id.value)
        val firsts = SampleHabits.stats(id = selected.id.value)
        assertNotEquals(spokenChart(firsts), spokenChart(theirs))

        showStats(selectedHabit = other)

        composeTestRule.onNodeWithContentDescription(spokenChart(theirs)).assertExists()
        composeTestRule.onNodeWithContentDescription(spokenChart(firsts)).assertDoesNotExist()
    }

    @Test
    fun oneMonthOfHistoryKeepsTheCardAndSaysWhenTheChartStarts() {
        // ⚠⚠ **M9.5 hid this card and M9.6 reversed it, from a device rather than from an opinion.**
        // A lone month was drawn as a 4dp dot, which in the gallery beside an explanatory label
        // looked reasonable and on the real screen reads as a chart that failed to render. A section
        // that vanishes and reappears a month later is also a screen nobody can learn.
        //
        // The threshold is structural, not chosen: a line needs two points.
        showChart(months = 1)

        composeTestRule.onNodeWithText(string(R.string.stats_monthly_eyebrow)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.stats_chart_not_yet)).assertIsDisplayed()
    }

    @Test
    fun aHabitWithNoMonthAtAllGetsTheSameSentence() {
        // The other side of the same threshold, and a case only a sync anomaly reaches: an inferred
        // start date in the future yields no bucket at all. One sentence covers both — it says when
        // the chart arrives rather than what is missing, so it stays true either way.
        showChart(months = 0)

        composeTestRule.onNodeWithText(string(R.string.stats_monthly_eyebrow)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.stats_chart_not_yet)).assertIsDisplayed()
        // The tiles above it stay, because zero completions is a true thing to print.
        composeTestRule.onNodeWithText(string(R.string.stats_tile_current_streak)).assertIsDisplayed()
    }

    @Test
    fun twoMonthsIsAChartAndNotASentence() {
        // The mutant this catches is an off-by-one in the threshold: `<= MINIMUM_CHART_POINTS`
        // instead of `<`, which would put the sentence in front of a habit that has a real curve.
        showChart(months = 2)

        composeTestRule.onNodeWithText(string(R.string.stats_chart_not_yet)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.stats_monthly_eyebrow)).assertIsDisplayed()
    }

    @Test
    fun theRealSampleHabitNeverShowsTheNotYetSentence() {
        // Five habits with months of history behind them. If this fires, the threshold is reading
        // the wrong thing entirely.
        showStats()

        composeTestRule.onNodeWithText(string(R.string.stats_chart_not_yet)).assertDoesNotExist()
    }

    @Test
    fun theEmptyScreenHasNoChartEither() {
        showEmpty(HabitsEmptyReason.SyncPending)

        composeTestRule.onNodeWithText(string(R.string.stats_monthly_eyebrow)).assertDoesNotExist()
    }

    @Test
    fun theEmptyScreenHasNoPillsToChooseFrom() {
        // Empty means no habits at all (`StatsUiState.Empty`), so there is nothing to select
        // between — and the selector must not draw an empty row above the message.
        showEmpty(HabitsEmptyReason.SyncPending)

        habits.forEach { habit ->
            composeTestRule.onNodeWithText(label(habit)).assertDoesNotExist()
        }
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertDoesNotExist()
    }

    /** The screen for a habit with exactly [months] buckets and nothing else worth drawing. */
    private fun showChart(months: Int) {
        composeTestRule.setContent {
            HabitsTheme {
                StatsContent(
                    state = StatsUiState.Content(habits = habits, selected = statsWithMonths(selected, months)),
                    onEvent = { },
                    onOpenSyncStatus = { },
                )
            }
        }
    }

    /**
     * Figures for a habit young enough to have [months] buckets — hand-built rather than aged out of
     * `SampleHabits`, whose youngest habit is already three months old.
     */
    private fun statsWithMonths(
        habit: Habit,
        months: Int,
    ) = HabitStats(
        period = HabitPeriod(habit = habit, cells = emptyList(), todayIndex = HabitPeriod.TODAY_ABSENT),
        streak = Streak.None,
        rate = CompletionRate(completedDays = 0, eligibleDays = 0),
        months =
            List(months) { index ->
                MonthlyCompletions(month = YearMonth.of(2026, index + 1), completions = index)
            },
        // These assertions are about how many buckets draw a chart at all, not about the tail, so
        // the finished case keeps them reading as one thing.
        lastMonthIsUnfinished = false,
    )
}

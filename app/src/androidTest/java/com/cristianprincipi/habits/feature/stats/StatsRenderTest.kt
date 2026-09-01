package com.cristianprincipi.habits.feature.stats

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}

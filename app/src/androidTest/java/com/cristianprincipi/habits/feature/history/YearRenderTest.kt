package com.cristianprincipi.habits.feature.history

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Year screen on a device, driven through its stateless half.
 *
 * The thing that can go wrong quietly here is **which denominator the card prints**. The percentage
 * is measured over eligible days — days on or after the habit started — and the grid draws 365
 * regardless, so a second line counting the grid instead of the rate would put two numbers on one
 * card that cannot both be true. `SampleHabits` has habits from 400 days old to 95, so a card whose
 * count came from the wrong place shows it immediately.
 *
 * The other is the read-only claim, which is a claim about the *absence* of something and so needs
 * an assertion rather than a description.
 */
@RunWith(AndroidJUnit4::class)
class YearRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reference = SampleHabits.referenceDate
    private var syncStatusOpened = 0

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showYear() {
        composeTestRule.setContent {
            HabitsTheme {
                YearContent(
                    state = YearUiState.Content(SampleHabits.years(reference)),
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }
    }

    private fun showEmpty(reason: HabitsEmptyReason) {
        composeTestRule.setContent {
            HabitsTheme {
                YearContent(
                    state = YearUiState.Empty(reason),
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }
    }

    @Test
    fun theTitleIsTheWordYearRatherThanADate() {
        // Unlike Month, whose title is the paged-to month's name. This view has no paging at all,
        // so its window never changes and there is nothing for a title to track.
        showYear()

        composeTestRule.onNodeWithText(string(R.string.screen_title_year)).assertIsDisplayed()
    }

    @Test
    fun everyHabitGetsACardWithItsCompletionFigure() {
        showYear()

        SampleHabits.years(reference).forEach { year ->
            composeTestRule.onNodeWithText(year.period.habit.name).assertIsDisplayed()
        }
        composeTestRule
            .onAllNodesWithText(string(R.string.year_completion_eyebrow))
            .assertCountEquals(SampleHabits.habits(reference).size)
    }

    @Test
    fun theSecondLineCountsTheDaysThePercentageIsOverAndNotTheGrid() {
        // ⭐⭐ The assertion this file exists for. `Focus` starts 95 days before the reference date,
        // so its grid is 365 cells and its rate is over 95 — and the card must print the pair that
        // agrees with the percentage beside it. A line reading "66 of 365" next to "69%" is two
        // numbers on one card that cannot both be the rate, and it is the count a session copying
        // the Month card would write.
        val focus = SampleHabits.years(reference).first { it.period.habit.name == "Focus" }
        assertTrue(
            "the sample data must give Focus a short window for this to mean anything, " +
                "got ${focus.rate.eligibleDays}",
            focus.rate.eligibleDays < DAYS_IN_YEAR,
        )

        showYear()

        composeTestRule
            .onNodeWithText(
                string(R.string.habit_card_summary)
                    .format(focus.rate.completedDays, focus.rate.eligibleDays),
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.year_completion_value).format(focus.rate.percent))
            .assertIsDisplayed()
    }

    @Test
    fun nothingOnThisScreenCanBeTappedAtAll() {
        // ⭐⭐ `CLAUDE.md` §1's read-only rule, and this screen is the strongest form of it: Month
        // has two arrows, and this one has nothing. A 4dp cell is a quarter of an unusable target,
        // the design gives this view no sub-navigation, and there is no `+` — ADR-010 makes habit
        // creation a Notion schema change the app is provably incapable of. A future session adding
        // any handler here fails on this line.
        showYear()

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun theEmptyScreenKeepsItsTitleAndRoutesToSyncStatus() {
        showEmpty(HabitsEmptyReason.SyncFailed)

        composeTestRule.onNodeWithText(string(R.string.screen_title_year)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.habits_empty_failed_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.habits_empty_failed_action)).performClick()

        assertEquals(1, syncStatusOpened)
    }

    @Test
    fun aPendingImportOffersNoButtonAtAll() {
        showEmpty(HabitsEmptyReason.SyncPending)

        composeTestRule.onNodeWithText(string(R.string.habits_empty_pending_title)).assertIsDisplayed()
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private companion object {
        const val DAYS_IN_YEAR = 365
    }
}

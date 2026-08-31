package com.cristianprincipi.habits.feature.history

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.monthLabel
import com.cristianprincipi.habits.core.datetime.monthName
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.PeriodCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.Locale

/**
 * The Month screen on a device, driven through its stateless half.
 *
 * The two things that can go wrong quietly here are both counting mistakes, and neither shows up in
 * a screenshot: a summary line whose denominator includes the **padding** cells (`18 of 36` for a
 * 31-day August), and a grid that keeps a cell for every day of the month it was previously showing.
 * Both look like a plausible number in a card nobody re-counts.
 *
 * The third is the claim `CLAUDE.md` §1 makes about this screen — that it is **read-only** — which
 * is a claim about the *absence* of something, so nothing but an assertion can hold it.
 *
 * `SampleHabits.referenceDate` is **Saturday 22 August 2026**: 31 days, starting on a Saturday, so
 * the window opens with five blanks and holds completed, incomplete and future days at once.
 */
@RunWith(AndroidJUnit4::class)
class MonthRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reference = SampleHabits.referenceDate
    private val august = SampleHabits.referenceMonth

    private val events = mutableListOf<MonthEvent>()
    private var syncStatusOpened = 0

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun locale() = Locale.getDefault()

    /** The whole screen over the sample month, with paging wired the way the ViewModel wires it. */
    private fun showMonth(month: YearMonth = august) {
        composeTestRule.setContent {
            HabitsTheme {
                MonthContent(
                    state =
                        MonthUiState.Content(
                            month = month,
                            habits = SampleHabits.monthPeriods(reference, monthOf = month),
                        ),
                    onEvent = { event -> events += event },
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }
    }

    @Test
    fun everyHabitGetsACard() {
        showMonth()

        SampleHabits.habits(reference).forEach { habit ->
            composeTestRule.onNodeWithText(habit.name).assertIsDisplayed()
        }
    }

    @Test
    fun theSummaryCountsDaysInTheMonthAndNotTheLeadingBlanks() {
        // ⭐⭐ The mutant: `cells.size` instead of the `PeriodCell.Day` count. August 2026 opens with
        // five blanks, so it would read "21 of 36" — a number with no meaning that still looks like
        // a number. The Week card CAN use `cells.size`, because a week window has no padding at all,
        // which is exactly why copying that line across is the mistake worth pinning.
        val period = SampleHabits.monthPeriods(reference).first()
        val completed = period.cells.count { it is PeriodCell.Day && it.state == DayState.COMPLETED }

        showMonth()

        composeTestRule
            .onNodeWithText(string(R.string.habit_card_summary).format(completed, DAYS_IN_AUGUST))
            .assertIsDisplayed()
    }

    @Test
    fun theHeaderNamesTheMonthTwiceOverTheDesignsOwnDoing() {
        // The title is the month's name and the sub-nav label is the name plus the year, which is
        // what `App.tsx` does (`headerTitles.month` and the NavArrows label). Asserted rather than
        // assumed, because it is the pair the user is being asked to rule on at M8.7.
        showMonth()

        composeTestRule.onNodeWithText(monthName(august, locale())).assertIsDisplayed()
        composeTestRule.onNodeWithText(monthLabel(august, locale())).assertIsDisplayed()
    }

    @Test
    fun theArrowsSendPagingEventsRatherThanMovingTheLabelThemselves() {
        showMonth()

        composeTestRule.onNodeWithContentDescription(string(R.string.month_previous)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.month_next)).performClick()

        assertEquals(
            listOf(MonthEvent.PreviousMonthPressed, MonthEvent.NextMonthPressed),
            events.toList(),
        )
    }

    @Test
    fun aShortMonthDrawsItsOwnLengthRatherThanTheOneBeforeIt() {
        // February 2026: 28 days, and the summary has to follow the month rather than a constant.
        val february = YearMonth.of(2026, 2)
        val period = SampleHabits.monthPeriods(reference, monthOf = february).first()
        val completed = period.cells.count { it is PeriodCell.Day && it.state == DayState.COMPLETED }

        showMonth(february)

        composeTestRule.onNodeWithText(monthLabel(february, locale())).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.habit_card_summary).format(completed, DAYS_IN_FEBRUARY))
            .assertIsDisplayed()
    }

    @Test
    fun nothingInThisScreenExceptTheTwoArrowsCanBeTapped() {
        // ⭐⭐ `CLAUDE.md` §1's read-only rule, asserted rather than described. At a typical width a
        // cell here is about 14dp — far under a usable target — so check-off lives on the Week pills
        // and a tappable dot would be a tap the user cannot aim. A future session adding an
        // `onClick` to `MonthDotGrid` or `MonthHabitCard` fails exactly here.
        showMonth()

        val clickable = composeTestRule.onAllNodes(hasClickAction())
        clickable.assertCountEquals(NAV_ARROWS)

        composeTestRule.onNodeWithContentDescription(string(R.string.month_previous)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.month_next)).assertIsDisplayed()
        assertTrue("tapping the arrows must be the only thing this screen accepts", events.isEmpty())
    }

    @Test
    fun anEmptyScreenHidesTheSubNavButKeepsItsTitle() {
        // Habits are global (ADR-010), so paging cannot turn Empty into Content — the arrows would
        // move a label above a screen that provably cannot change. The title stays, because it is
        // the month's name and this screen still has a month.
        composeTestRule.setContent {
            HabitsTheme {
                MonthContent(
                    state = MonthUiState.Empty(august, HabitsEmptyReason.SyncFailed),
                    onEvent = { event -> events += event },
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }

        composeTestRule.onNodeWithText(monthName(august, locale())).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(monthLabel(august, locale())).assertCountEquals(0)
        composeTestRule
            .onNodeWithText(string(R.string.habits_empty_failed_title))
            .assertIsDisplayed()
    }

    @Test
    fun theEmptyScreenRoutesToSyncStatusRatherThanRetryingHere() {
        composeTestRule.setContent {
            HabitsTheme {
                MonthContent(
                    state = MonthUiState.Empty(august, HabitsEmptyReason.SyncFailed),
                    onEvent = {},
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.habits_empty_failed_action)).performClick()

        assertEquals(1, syncStatusOpened)
    }

    @Test
    fun aPendingImportOffersNoButtonAtAll() {
        composeTestRule.setContent {
            HabitsTheme {
                MonthContent(
                    state = MonthUiState.Empty(august, HabitsEmptyReason.SyncPending),
                    onEvent = {},
                    onOpenSyncStatus = { syncStatusOpened++ },
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.habits_empty_pending_title)).assertIsDisplayed()
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private companion object {
        const val DAYS_IN_AUGUST = 31
        const val DAYS_IN_FEBRUARY = 28

        /** The only two things on this screen that accept a tap. */
        const val NAV_ARROWS = 2
    }
}

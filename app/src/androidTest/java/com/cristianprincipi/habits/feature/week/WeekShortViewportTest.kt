package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * The Week screen with more habits than fit on it.
 *
 * ### ⚠ Why this exists — the repository has shipped this bug before
 *
 * **ADR-055.** The database picker rendered fifteen databases in a non-scrolling `Column`, which put
 * `Continue` below the fold: setup could not be completed at all. **ADR-057/M5.5** then found the
 * same failure one step earlier in the same wizard, where a raised keyboard shrank the viewport and
 * a `Column` clipped its overflow in silence. `NotionSetupShortViewportTest` exists because of both.
 *
 * The landing screen had no equivalent, and it is the more exposed of the two. The wizard's content
 * is fixed; **the Week screen's length is the user's Notion schema**, which they change in Notion
 * without telling the app (ADR-010). Five habits happen to fit a phone today. Ten do not, and
 * nothing in the suite would have noticed.
 *
 * ### ⭐ Scrolling is what makes this a real assertion — and it is not `performScrollTo`
 *
 * The wizard's version uses `performScrollTo`, which works there because a `Column` with
 * `verticalScroll` composes every child whether or not it is on screen. **A `LazyColumn` does not**:
 * an off-screen card has no node in the semantics tree at all, so `performScrollTo` fails looking
 * exactly like a missing view rather than like an unreachable one. This was learned here, by
 * writing it the wizard's way first.
 *
 * So the scroll is driven through the list itself — `onNode(hasScrollAction()).performScrollToNode`
 * — which keeps the property that matters: it fails outright when nothing is scrollable, so this
 * cannot pass by accident against a `Column` that has quietly lost its scrolling. `assertIsDisplayed`
 * alone would be far weaker: a card just off the bottom edge is not displayed either way, and the
 * test would say nothing about whether the user could ever get to it.
 *
 * ### And the fixed-height Box is the point, not a shortcut
 *
 * [SHORT_VIEWPORT] reproduces the shortage of room on any device, in any orientation, with no
 * rotation to wait for — the same technique and the same reasoning as the wizard's version. It also
 * stands in for the case `Plan.md` M6.11 carries for the user: **landscape**, where the bottom bar
 * and its divider already eat ~17% of the window.
 */
@RunWith(AndroidJUnit4::class)
class WeekShortViewportTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reference = SampleHabits.referenceDate
    private val monday = startOfWeek(reference)
    private val events = mutableListOf<WeekEvent>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /**
     * [count] habits over the sample week, by repeating the sample set under distinct ids.
     *
     * The ids have to differ: `WeekList` keys its `LazyColumn` on `habit.id`, and duplicate keys are
     * a crash rather than a rendering quirk. Repeating the five real ones keeps every card's shape,
     * colour and history exactly what production draws.
     */
    private fun manyHabits(count: Int): List<HabitPeriod> {
        val base = SampleHabits.weekPeriods(reference)
        return (0 until count).map { index ->
            val source = base[index % base.size]
            val habit: Habit = source.habit
            source.copy(
                habit =
                    habit.copy(
                        id = HabitId("${habit.id.value}-$index"),
                        name = "${habit.name} $index",
                    ),
            )
        }
    }

    private fun showWeek(
        habitCount: Int,
        height: Dp = SHORT_VIEWPORT,
    ) {
        composeTestRule.setContent {
            HabitsTheme {
                Box(modifier = Modifier.fillMaxWidth().height(height)) {
                    WeekContent(
                        state =
                            WeekUiState.Content(
                                weekStart = monday,
                                habits = manyHabits(habitCount),
                                isCurrentWeek = true,
                                currentYear = reference.year,
                            ),
                        onEvent = { event -> events += event },
                        onOpenSyncStatus = {},
                    )
                }
            }
        }
    }

    @Test
    fun theLastHabitIsReachableWhenThereAreFarMoreThanFit() {
        showWeek(habitCount = MANY)

        scrollTo(lastHabitName(MANY))

        composeTestRule.onNodeWithText(lastHabitName(MANY)).assertIsDisplayed()
    }

    @Test
    fun theLastHabitCanStillBeCheckedOffOnceYouHaveScrolledToIt() {
        // ⚠ Reaching a card says nothing about being able to use it. ADR-055's defect was precisely
        // that the control existed and could not be operated, so the assertion that matters is the
        // tap, not the sight of it.
        showWeek(habitCount = MANY)
        val pill = pillOn(lastHabitName(MANY), monday)

        scrollTo(lastHabitName(MANY))
        composeTestRule.onNodeWithContentDescription(pill, useUnmergedTree = true).performClick()

        composeTestRule.runOnIdle {
            val toggled = events.filterIsInstance<WeekEvent.DayToggled>().single()
            assertEquals(monday, toggled.date)
        }
    }

    @Test
    fun theHeaderStaysPutRatherThanScrollingAwayWithTheCards() {
        // `docs/design-spec.md` §1 pins the sub-nav above the list rather than in it. It is also
        // what stops a user who has scrolled to habit thirty from having to scroll back to change
        // week — and `WeekContent` gets that by putting the header outside the `LazyColumn`, which
        // is an arrangement a refactor could undo without any other test noticing.
        showWeek(habitCount = MANY)

        scrollTo(lastHabitName(MANY))

        composeTestRule.onNodeWithText(string(R.string.screen_title_week)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Aug 17 – Aug 23").assertIsDisplayed()
    }

    @Test
    fun aSingleHabitStillDrawsWithoutNeedingToBeScrolledTo() {
        // The other end of the range, and the case the user's own database is nearest to. A list
        // that only worked once it overflowed would be a strange kind of fixed.
        showWeek(habitCount = 1)

        composeTestRule.onNodeWithText(lastHabitName(1)).assertIsDisplayed()
        // Its pills are on screen without any scrolling, which is the claim. Their ticked state is
        // the sample's business and `WeekRenderTest` asserts it — repeating it here would couple
        // this test to history it does not care about.
        composeTestRule
            .onNodeWithContentDescription(pillOn(lastHabitName(1), monday), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * Brings the card named [habitName] into view, through the list rather than through the node.
     *
     * ⚠ Deliberately **not** `onNodeWithText(...).performScrollTo()`. A `LazyColumn` has not
     * composed the card yet, so that call cannot find the node to scroll to it — see this class's
     * KDoc. Starting from the scrollable itself is what makes the scroll possible *and* keeps the
     * test failing loudly if the list ever stops being scrollable.
     */
    private fun scrollTo(habitName: String) {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(habitName))
    }

    private fun lastHabitName(count: Int): String {
        val base = SampleHabits.habits(reference)
        val index = count - 1
        return "${base[index % base.size].name} $index"
    }

    private fun pillOn(
        habitName: String,
        date: LocalDate,
    ): String =
        string(R.string.week_pill_description)
            .format(habitName, accessibleDate(date, Locale.getDefault()))

    private companion object {
        /**
         * Shorter than any phone in portrait, so the overflow is certain rather than device-dependent.
         *
         * A test that only overflowed on some screens would pass on the emulator and fail in the
         * hand, which is the opposite of what this is for.
         */
        val SHORT_VIEWPORT = 320.dp

        /** Far more habits than a phone can show, and more than the user's five. */
        const val MANY = 24
    }
}

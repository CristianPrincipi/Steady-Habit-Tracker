package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises every shared component on a device.
 *
 * Most of this is a smoke test — the two dot grids do real arithmetic inside a `Canvas`, and a
 * division that lands on a negative or a `NaN` throws during draw, where no unit test would see it.
 *
 * The week pill assertions are not a smoke test. Check-off is the app's core interaction and lives
 * entirely on these pills (ADR-009), so the two things that would silently ruin it — a target
 * smaller than a thumb, and a toggle that does not report its state to a screen reader — are pinned
 * here rather than left to be noticed on a device later.
 */
@RunWith(AndroidJUnit4::class)
class ComponentRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everyComponentRendersWithoutThrowing() {
        composeTestRule.setContent {
            HabitsTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).width(GALLERY_WIDTH)) {
                    HabitIcon(emoji = "🎸", habitColor = HabitsTheme.colors.habit.blue, size = HabitIconSize.SMALL)
                    HabitIcon(emoji = "💧", habitColor = HabitsTheme.colors.habit.green)
                    SectionEyebrow(text = "YEAR OVERVIEW")
                    HabitChip(
                        emoji = "🎸",
                        name = "Learn Guitar",
                        habitColor = HabitsTheme.colors.habit.blue,
                        selected = true,
                        onClick = {},
                    )
                    NavArrows(
                        label = "August 2026",
                        previousContentDescription = "Previous",
                        nextContentDescription = "Next",
                        onPrevious = {},
                        onNext = {},
                    )
                    StatTile(label = "Best streak", value = "111", habitColor = HabitsTheme.colors.habit.purple)
                    StatTile(
                        label = "Best streak",
                        value = "111",
                        habitColor = HabitsTheme.colors.habit.purple,
                        numeralStyle = NumeralStyle.MONO,
                    )
                    MonthHabitCard(
                        name = "Learn Guitar",
                        description = "15 min practice",
                        emoji = "🎸",
                        habitColor = HabitsTheme.colors.habit.blue,
                        days = previewMonth(),
                    )
                    YearHabitCard(
                        name = "Morning Yoga",
                        description = "20 min flow",
                        emoji = "🧘",
                        habitColor = HabitsTheme.colors.habit.orange,
                        completionLabel = "84%",
                        completionEyebrow = "COMPLETION",
                        days = previewYear(),
                    )
                    EmptyState(emoji = "🌱", title = "No habits yet", body = "Nothing here.")
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Previous").assertHeightIsAtLeast(Sizing.minimumTouchTarget)
    }

    @Test
    fun aWeekPillIsAFullTouchTargetEvenThoughItIsDrawnSmaller() {
        composeTestRule.setContent { HabitsTheme { TestWeekCard(onToggle = {}) } }

        // The design draws 36dp; docs/tokens.md §4.4 requires the target to be 48dp without the
        // drawn pill growing. Anything less and the app's primary interaction is hard to hit.
        composeTestRule
            .onNodeWithContentDescription(WEDNESDAY)
            .assertHeightIsAtLeast(Sizing.minimumTouchTarget)
    }

    @Test
    fun aWeekPillReportsItsCheckedStateAndReportsTaps() {
        val toggled = mutableListOf<Int>()
        composeTestRule.setContent { HabitsTheme { TestWeekCard(onToggle = toggled::add) } }

        composeTestRule.onNodeWithContentDescription(MONDAY).assertIsOn()
        composeTestRule.onNodeWithContentDescription(WEDNESDAY).assertIsOff()

        composeTestRule.onNodeWithContentDescription(WEDNESDAY).performClick()

        composeTestRule.runOnIdle { assertEquals(listOf(WEDNESDAY_INDEX), toggled) }
    }

    // --- which pills can be written at all (M6.7) ---------------------------------------------

    @Test
    fun onlyDaysThatCanBeWrittenAreEnabled() {
        // ⭐ Exhaustive over `DayStatus`, which the feature layer cannot manage: a week never
        // contains BEFORE_START unless you page back past a habit's start date, and it can never
        // contain PADDING at all (`weekWindow` produces none). Here every case is one list entry.
        composeTestRule.setContent { HabitsTheme { TestWeekCard(days = EVERY_STATUS, onToggle = {}) } }

        composeTestRule.onNodeWithContentDescription(MONDAY).assertIsEnabled()
        composeTestRule.onNodeWithContentDescription(TUESDAY).assertIsEnabled()
        composeTestRule.onNodeWithContentDescription(WEDNESDAY).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(THURSDAY).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(FRIDAY).assertIsNotEnabled()
    }

    @Test
    fun aDisabledPillCannotBeActivatedThroughSemanticsEither() {
        // ⚠ `performClick` goes through the **semantics** action, which is the path an accessibility
        // service takes — not the pointer path. A pill that ignores a finger but still fires for
        // TalkBack would be a real bug, and only this direction of the test can see it.
        val toggled = mutableListOf<Int>()
        composeTestRule.setContent { HabitsTheme { TestWeekCard(days = EVERY_STATUS, onToggle = toggled::add) } }

        composeTestRule.onNodeWithContentDescription(WEDNESDAY).performClick()

        composeTestRule.runOnIdle { assertEquals(emptyList<Int>(), toggled) }
    }

    // --- the today ring has to be visible, not merely drawn (M6.11) ---------------------------

    @Test
    fun theTodayRingChangesACompletedPillsPixels() {
        // ⭐⭐ **The M6.11 defect, as a regression guard.** The ring was drawn in the habit's colour
        // inside a pill *filled* with that colour, so on a completed day it was the same colour on
        // the same colour and rendered as nothing at all — a habit done every day never showed its
        // today marker once. Every existing assertion passed throughout: the modifier was applied,
        // the semantics were right, the size was right. Only the pixels were wrong.
        //
        // So this asserts the property the bug broke rather than a colour value: **ringing a pill
        // must change what it looks like.** It cannot be satisfied by drawing an invisible border,
        // and it does not break when the palette is retuned.
        assertRingIsVisible(days = List(DAYS_IN_WEEK) { DayStatus.COMPLETED }, case = "completed")
    }

    @Test
    fun theTodayRingStillChangesAnIncompletePillsPixels() {
        // The case that always worked, pinned so the fix for the other one cannot quietly cost it.
        assertRingIsVisible(days = List(DAYS_IN_WEEK) { DayStatus.INCOMPLETE }, case = "incomplete")
    }

    /** Renders [days] with and without a ring on the same pill and fails if they look identical. */
    private fun assertRingIsVisible(
        days: List<DayStatus>,
        case: String,
    ) {
        var ringed by mutableStateOf(true)
        composeTestRule.setContent {
            HabitsTheme {
                TestWeekCard(
                    days = days,
                    todayIndex = if (ringed) TODAY_INDEX else NO_TODAY,
                    onToggle = {},
                )
            }
        }

        val withRing = composeTestRule.onNodeWithContentDescription(FRIDAY).captureToImage()
        ringed = false
        composeTestRule.waitForIdle()
        val withoutRing = composeTestRule.onNodeWithContentDescription(FRIDAY).captureToImage()

        assertTrue(
            "the today ring made no visible difference to a $case pill",
            differs(withRing, withoutRing),
        )
    }

    private fun differs(
        a: ImageBitmap,
        b: ImageBitmap,
    ): Boolean {
        val left = a.toPixelMap()
        val right = b.toPixelMap()
        if (left.width != right.width || left.height != right.height) return true
        for (y in 0 until left.height) {
            for (x in 0 until left.width) {
                if (left[x, y] != right[x, y]) return true
            }
        }
        return false
    }

    @androidx.compose.runtime.Composable
    private fun TestWeekCard(
        days: List<DayStatus> = previewWeek(),
        todayIndex: Int = TODAY_INDEX,
        onToggle: (Int) -> Unit,
    ) {
        WeekHabitCard(
            name = "Drink water",
            description = "8 glasses a day",
            emoji = "💧",
            habitColor = HabitsTheme.colors.habit.green,
            days = days,
            dayLabels = PREVIEW_DAY_LABELS,
            dayContentDescriptions = PREVIEW_DAY_LABELS,
            todayIndex = todayIndex,
            onToggleDay = onToggle,
            modifier = Modifier.width(GALLERY_WIDTH),
        )
    }

    private companion object {
        val GALLERY_WIDTH = 360.dp
        const val MONDAY = "Mon"
        const val TUESDAY = "Tue"
        const val WEDNESDAY = "Wed"
        const val THURSDAY = "Thu"
        const val FRIDAY = "Fri"
        const val WEDNESDAY_INDEX = 2
        const val TODAY_INDEX = 4
        const val DAYS_IN_WEEK = 7

        /** `WeekPills`' own contract for "this week does not contain today". */
        const val NO_TODAY = -1

        /** One pill per `DayStatus`, in enum order, then two fillers to make up the seven. */
        val EVERY_STATUS =
            listOf(
                DayStatus.COMPLETED,
                DayStatus.INCOMPLETE,
                DayStatus.FUTURE,
                DayStatus.BEFORE_START,
                DayStatus.PADDING,
                DayStatus.COMPLETED,
                DayStatus.INCOMPLETE,
            )
    }
}

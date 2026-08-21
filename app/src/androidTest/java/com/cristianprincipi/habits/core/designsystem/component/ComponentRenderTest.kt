package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import org.junit.Assert.assertEquals
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

    @androidx.compose.runtime.Composable
    private fun TestWeekCard(onToggle: (Int) -> Unit) {
        WeekHabitCard(
            name = "Drink water",
            description = "8 glasses a day",
            emoji = "💧",
            habitColor = HabitsTheme.colors.habit.green,
            days = previewWeek(),
            dayLabels = PREVIEW_DAY_LABELS,
            dayContentDescriptions = PREVIEW_DAY_LABELS,
            todayIndex = TODAY_INDEX,
            onToggleDay = onToggle,
            modifier = Modifier.width(GALLERY_WIDTH),
        )
    }

    private companion object {
        val GALLERY_WIDTH = 360.dp
        const val MONDAY = "Mon"
        const val WEDNESDAY = "Wed"
        const val WEDNESDAY_INDEX = 2
        const val TODAY_INDEX = 4
    }
}

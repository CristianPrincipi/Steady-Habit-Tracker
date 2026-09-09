package com.cristianprincipi.habits.feature.managehabits

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.MoveDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The reorder arrows on a device — M15.5.
 *
 * ### ⭐⭐ Why this exists rather than a `uiautomator` check
 *
 * The first attempt to verify these buttons used `adb shell uiautomator dump`, which reported them
 * as `clickable="false" enabled="true"` — for **every** row, including the first, whose up arrow is
 * disabled. That reading survived three different modifier arrangements and an exact copy of
 * `NavArrows`' working `ChevronButton`, which is what eventually made it clear the *instrument* was
 * the thing to distrust: uiautomator flattens Compose's semantics tree through
 * `AccessibilityNodeInfo`, and what it prints for a node inside a `LazyColumn` item is not what
 * Compose's own semantics say.
 *
 * ⚠ **This is ADR-124's lesson again** — the measurement was slower to doubt than the code. The
 * Compose test framework reads the semantics tree directly, so it answers the question that was
 * actually being asked, and it keeps answering it after this session.
 *
 * Everything here drives [ManageHabitsContent], the stateless half, so the end rows can be set up
 * exactly. Labels are read from the same resources the screen uses, so a test cannot pass against
 * wording that no longer exists.
 */
@RunWith(AndroidJUnit4::class)
class HabitReorderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun habit(
        id: String,
        name: String,
    ) = Habit(
        id = HabitId(id),
        name = name,
        emoji = "",
        color = HabitColor.BLUE,
        startDate = LocalDate.of(2026, 1, 1),
    )

    private val read = habit("read", "Read")
    private val journal = habit("journal", "Journal")
    private val quote = habit("quote", "Quote")

    private fun moveUp(name: String) = context.getString(R.string.habits_move_up, name)

    private fun moveDown(name: String) = context.getString(R.string.habits_move_down, name)

    private val moves = mutableListOf<Pair<HabitId, MoveDirection>>()

    private fun show(habits: List<Habit> = listOf(read, journal, quote)) {
        composeTestRule.setContent {
            HabitsTheme {
                ManageHabitsContent(
                    state = ManageHabitsUiState.Content(habits = habits, measures = emptyList(), notionUrl = null),
                    onEvent = { event ->
                        if (event is ManageHabitsEvent.Moved) {
                            moves += event.habitId to event.direction
                        }
                    },
                    onOpenInNotion = {},
                    onEditMapping = {},
                )
            }
        }
    }

    @Test
    fun everyArrowIsNamedAfterItsOwnHabit() {
        // ⚠ Six identical "Move up"s would be six indistinguishable stops for a screen reader on a
        // screen whose whole purpose is telling the rows apart.
        show()

        composeTestRule.onNodeWithContentDescription(moveUp("Journal")).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(moveDown("Journal")).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(moveUp("Quote")).assertHasClickAction()
    }

    @Test
    fun theFirstHabitCannotBeMovedUp() {
        // ⭐ The claim uiautomator got wrong. Disabled rather than hidden (ADR-060), so it has to be
        // present *and* refused — a control that is merely absent tells the user nothing.
        show()

        composeTestRule.onNodeWithContentDescription(moveUp("Read")).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(moveDown("Read")).assertIsEnabled()
    }

    @Test
    fun theLastHabitCannotBeMovedDown() {
        show()

        composeTestRule.onNodeWithContentDescription(moveDown("Quote")).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(moveUp("Quote")).assertIsEnabled()
    }

    @Test
    fun theOnlyHabitCanBeMovedNeitherWay() {
        show(habits = listOf(read))

        composeTestRule.onNodeWithContentDescription(moveUp("Read")).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(moveDown("Read")).assertIsNotEnabled()
    }

    @Test
    fun tappingAnArrowAsksForThatHabitToMoveThatWay() {
        show()

        composeTestRule.onNodeWithContentDescription(moveUp("Quote")).performClick()

        assertEquals(listOf(HabitId("quote") to MoveDirection.Up), moves)
    }

    @Test
    fun aDisabledArrowSendsNothing() {
        // Not merely cosmetic: a no-op write would still emit through the flow and animate the
        // list, so a tap on a disabled arrow would twitch the screen for nothing.
        show()

        composeTestRule.onNodeWithContentDescription(moveUp("Read")).performClick()

        assertEquals(emptyList<Pair<HabitId, MoveDirection>>(), moves)
    }
}

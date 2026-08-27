package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * The Week header: the label, the two arrows, and the jump home.
 *
 * These are the three controls M6.4 added, and until it none of them could be fired from the UI at
 * all — `WeekViewModel` had handled all three since M6.2 with nothing to send them.
 *
 * The locale is fixed to [Locale.UK] rather than taken from the device, so the label assertion is a
 * claim about *formatting* rather than about whichever locale the emulator happens to run in.
 */
@RunWith(AndroidJUnit4::class)
class WeekHeaderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val monday = startOfWeek(LocalDate.of(2026, 8, 22))

    private val events = mutableListOf<WeekEvent>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showHeader(isCurrentWeek: Boolean = true) {
        composeTestRule.setContent {
            HabitsTheme {
                WeekHeader(
                    week =
                        WeekUiState.Content(
                            weekStart = monday,
                            habits = emptyList(),
                            isCurrentWeek = isCurrentWeek,
                        ),
                    onEvent = { event -> events += event },
                    locale = Locale.UK,
                )
            }
        }
    }

    private fun thisWeekButton() = composeTestRule.onNodeWithText(string(R.string.week_this_week))

    @Test
    fun theHeaderNamesTheScreenAndTheWeekOnShow() {
        showHeader()

        composeTestRule.onNodeWithText(string(R.string.screen_title_week)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Aug 17 – Aug 23").assertIsDisplayed()
    }

    @Test
    fun theBackArrowAsksForThePreviousWeek() {
        showHeader()

        composeTestRule.onNodeWithContentDescription(string(R.string.week_previous)).performClick()

        assertEquals(listOf(WeekEvent.PreviousWeekPressed), events)
    }

    @Test
    fun theForwardArrowAsksForTheNextWeek() {
        showHeader()

        composeTestRule.onNodeWithContentDescription(string(R.string.week_next)).performClick()

        assertEquals(listOf(WeekEvent.NextWeekPressed), events)
    }

    @Test
    fun theArrowsAreNotTheSameControl() {
        // ⭐ Worth its own test because the two chevrons differ by one boolean in `NavArrows`, and a
        // header where both arrows page the same way looks completely correct in a screenshot.
        showHeader()

        composeTestRule.onNodeWithContentDescription(string(R.string.week_previous)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.week_next)).performClick()

        assertEquals(listOf(WeekEvent.PreviousWeekPressed, WeekEvent.NextWeekPressed), events)
    }

    @Test
    fun jumpingHomeIsOfferedButDisabledWhenAlreadyHome() {
        // ⚠ Disabled **in place**, not hidden — the user's stated preference (ADR-060). The control
        // being visible is what tells you the app can bring you back, and a row that changes width
        // as you page is a row that moves the title beside it.
        showHeader(isCurrentWeek = true)

        thisWeekButton().assertIsDisplayed()
        thisWeekButton().assertIsNotEnabled()
    }

    @Test
    fun jumpingHomeIsEnabledOnceYouHavePagedAway() {
        showHeader(isCurrentWeek = false)

        thisWeekButton().assertIsEnabled()
    }

    @Test
    fun jumpingHomeAsksToGoBackToThisWeek() {
        showHeader(isCurrentWeek = false)

        thisWeekButton().performClick()

        assertEquals(listOf(WeekEvent.ThisWeekPressed), events)
    }

    @Test
    fun aDisabledJumpDoesNothingAtAll() {
        // The other half of "disabled in place": it must be inert, not merely dim.
        showHeader(isCurrentWeek = true)

        thisWeekButton().performClick()

        assertEquals(emptyList<WeekEvent>(), events)
    }

    @Test
    fun aScreenWithNoWeekDrawsTheTitleAndNothingElse() {
        // Loading and Empty both pass `null`. Empty is week-independent (`WeekUiState.Empty`), so
        // arrows there would move a label above a screen that cannot change.
        composeTestRule.setContent {
            HabitsTheme { WeekHeader(week = null, onEvent = { events += it }, locale = Locale.UK) }
        }

        composeTestRule.onNodeWithText(string(R.string.screen_title_week)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.week_this_week)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(string(R.string.week_previous)).assertDoesNotExist()
    }
}

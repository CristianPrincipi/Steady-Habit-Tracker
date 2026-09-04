package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * The check-off's haptics — M12.6, and the only haptic in the app.
 *
 * ### Why a fake `HapticFeedback` rather than a device
 *
 * `CLAUDE.md` §7 is explicit that a screenshot cannot show what something feels like, and no test
 * can either: whether these two buzzes feel *right* is the user's call on a real phone. What a test
 * **can** pin is that the right one fires, on the right gesture, and that a day nobody can check
 * fires nothing at all — which is the part that would rot silently.
 *
 * `LocalHapticFeedback` is a composition local, so the pill can be handed a recorder in place of the
 * platform implementation without touching production code for the sake of the test.
 *
 * ### ⚠ What this deliberately does not assert
 *
 * Not the platform constant. `HapticFeedbackType.ToggleOn` maps to `TOGGLE_ON` on API 34+ and, via
 * `HapticFeedbackConstantsCompat.getFeedbackConstantOrFallback`, to `CONTEXT_CLICK` below it —
 * androidx's business, verified in its source, and not something this app should re-assert.
 */
@RunWith(AndroidJUnit4::class)
class WeekCheckOffHapticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Saturday 22 August 2026 — the week runs Mon 17 – Sun 23, so Sunday is still in the future. */
    private val reference = SampleHabits.referenceDate
    private val monday = startOfWeek(reference)
    private val subject: Habit = SampleHabits.habits().first()

    /** One completion, on the Monday — so Monday is checked and Tuesday reliably is not. */
    private val history = listOf(Completion(subject.id, monday))

    private val tuesday = monday.plusDays(1)

    /** Sunday of this week, which is after the reference date and therefore not checkable. */
    private val sunday = monday.plusDays(6)

    private val fired = mutableListOf<HapticFeedbackType>()

    private val recorder =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                fired += hapticFeedbackType
            }
        }

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showWeek() {
        composeTestRule.setContent {
            HabitsTheme {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    WeekContent(
                        state =
                            WeekUiState.Content(
                                weekStart = monday,
                                habits = SampleHabits.weekPeriods(reference, history),
                                isCurrentWeek = true,
                                currentYear = reference.year,
                            ),
                        onEvent = {},
                        onOpenSyncStatus = {},
                    )
                }
            }
        }
    }

    private fun pill(date: LocalDate): SemanticsNodeInteraction =
        composeTestRule.onNodeWithContentDescription(
            string(R.string.week_pill_description)
                .format(subject.name, accessibleDate(date, Locale.getDefault())),
            useUnmergedTree = true,
        )

    @Test
    fun checkingADayOffFeelsLikeSwitchingSomethingOn() {
        showWeek()

        pill(tuesday).performClick()

        assertEquals(listOf(HapticFeedbackType.ToggleOn), fired)
    }

    /**
     * ⭐ The half that makes this worth having two of.
     *
     * Clearing a day is not the same event as recording one, and one buzz for both would say it was.
     */
    @Test
    fun clearingADayFeelsDifferentFromCheckingIt() {
        showWeek()

        pill(monday).performClick()

        assertEquals(listOf(HapticFeedbackType.ToggleOff), fired)
    }

    /**
     * ⚠ A future day is disabled, not absent (ADR-060's rule, and the pill's own KDoc).
     *
     * `toggleable(enabled = false)` never calls `onValueChange`, so the haptic cannot fire — but
     * that is a property of where the call was placed, and moving it up to the outer `Box` would
     * buzz for a tap that does nothing. This is what fails if it ever moves.
     */
    @Test
    fun aDayThatCannotBeCheckedDoesNotBuzzAboutIt() {
        showWeek()

        pill(sunday).performClick()

        assertEquals(emptyList<HapticFeedbackType>(), fired)
    }
}

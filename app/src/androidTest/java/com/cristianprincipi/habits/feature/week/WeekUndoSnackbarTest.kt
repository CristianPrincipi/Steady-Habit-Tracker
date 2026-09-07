package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.shortDateLabel
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Habit
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * What the undo snackbar actually says, and what pressing it does — `Plan.md` M6.6.
 *
 * ⚠ **Undesigned UI.** The prototype has no snackbar because it has no click handlers at all
 * (`docs/design-spec.md` §0), so nothing here is checking a design: it pins the three claims the
 * feature rests on, which are behaviour rather than appearance.
 *
 * 1. **The sentence names the habit and the day.** That is the entire justification for the feature
 *    — tapping the pill again already undoes a tap, so if the message did not say *what* changed it
 *    would be a second route to something the user has, offered every time they used the app.
 * 2. **Undo restores the value the tap replaced**, not "whatever is there now".
 * 3. **A second offer replaces the first** rather than queueing behind it, because checking off
 *    several habits in a row is the normal way this screen gets used.
 *
 * It drives [WeekWithUndo] with a flow the test owns, which is the same seam `WeekPreviewActivity`
 * plugs into. `WeekViewModelTest` covers the other half — when a [WeekMessage] is sent at all, and
 * the one case that must send none.
 *
 * `SampleHabits.referenceDate` is **Saturday 22 August 2026**, so its week runs Mon 17 – Sun 23.
 */
@RunWith(AndroidJUnit4::class)
class WeekUndoSnackbarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reference = SampleHabits.referenceDate
    private val monday = startOfWeek(reference)
    private val subject: Habit = SampleHabits.habits().first()

    private val events = mutableListOf<WeekEvent>()
    private val messages = MutableSharedFlow<WeekMessage>(extraBufferCapacity = BUFFER)

    /** `createComposeRule` has no activity, so strings come from the app context. */
    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /**
     * The device's locale, which is what the composable resolves too.
     *
     * Read rather than hardcoded so the expected text is built the same way the screen builds it —
     * asserting a literal `"Mon, Aug 17"` would pin the test to one locale and one CLDR revision.
     */
    private fun expectedDay(date: LocalDate) = shortDateLabel(date, Locale.getDefault())

    private fun showWeek() {
        composeTestRule.setContent {
            HabitsTheme {
                WeekWithUndo(
                    state =
                        WeekUiState.Content(
                            weekStart = monday,
                            habits = SampleHabits.weekPeriods(),
                            isCurrentWeek = true,
                            currentYear = reference.year,
                        ),
                    messages = messages,
                    onEvent = { event -> events += event },
                    onOpenSyncStatus = {},
                )
            }
        }
    }

    private fun send(
        date: LocalDate = monday,
        completed: Boolean = true,
        habit: Habit = subject,
    ) = messages.tryEmit(
        WeekMessage.Toggled(
            habitId = habit.id,
            habitName = habit.name,
            date = date,
            completed = completed,
        ),
    )

    /** Advances the clock until [text] is on screen — the snackbar animates in. */
    private fun awaitText(text: String) =
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    private fun awaitTextGone(text: String) =
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }

    // --- what it says -------------------------------------------------------------------------

    @Test
    fun aCheckOffIsAnnouncedWithBothTheHabitAndTheDay() {
        showWeek()

        send(date = monday, completed = true)

        // ⭐ The claim the whole feature rests on. Neither half is decoration: the habit name catches
        // a tap on the wrong card, and the day catches a tap on the neighbouring pill — the two
        // mistakes seven 44dp-wide pills across five stacked cards invite.
        awaitText(string(R.string.week_undo_checked).format(subject.name, expectedDay(monday)))
    }

    @Test
    fun clearingADayIsAnnouncedAsClearingRatherThanAsCheckingOff() {
        showWeek()

        send(date = monday, completed = false)

        awaitText(string(R.string.week_undo_cleared).format(subject.name, expectedDay(monday)))
    }

    // ⚠ **There is no "today" case here any more, and the absence is deliberate.** M6.11 made a tap
    // on today silent, so no message this screen can receive describes today and there is no
    // relative wording left to assert. The rule that produces the silence is the ViewModel's and is
    // pinned there — `WeekViewModelTest.aTapOnTodaySaysNothingAtAll`, with its companion proving the
    // tick is still written. Adding a case here for a message production cannot emit would be the
    // unreachable branch ADR-067 argues against.

    // --- what pressing it does ----------------------------------------------------------------

    @Test
    fun pressingUndoAsksForTheValueTheTapReplaced() {
        showWeek()
        send(date = monday, completed = true)
        awaitText(string(R.string.week_undo_checked).format(subject.name, expectedDay(monday)))

        composeTestRule.onNodeWithText(string(R.string.week_undo_action)).performClick()
        composeTestRule.waitForIdle()

        // `completed = false` — the inverse of the tap, not a flip of whatever the pill holds now.
        // ⚠ And `UndoPressed`, never a second `DayToggled`: that would earn another message offering
        // to undo the undo, and another after that. See `WeekEvent.UndoPressed`.
        assertEquals(
            listOf(WeekEvent.UndoPressed(habitId = subject.id, date = monday, completed = false)),
            events,
        )
    }

    @Test
    fun aSecondCheckOffReplacesTheFirstOfferRatherThanQueueingBehindIt() {
        showWeek()
        val second = SampleHabits.habits()[1]
        val first = string(R.string.week_undo_checked).format(subject.name, expectedDay(monday))
        val latest = string(R.string.week_undo_checked).format(second.name, expectedDay(monday))

        send(date = monday, completed = true)
        awaitText(first)
        send(date = monday, completed = true, habit = second)

        // ⭐ `collectLatest` in the screen and a CONFLATED channel in the ViewModel, seen from the
        // outside: the newest sentence is up and the older one is gone, rather than the user
        // watching four seconds of a message about a tap they have already moved on from.
        awaitText(latest)
        awaitTextGone(first)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L

        /** Room for the back-to-back sends in the replacement test, with slack. */
        const val BUFFER = 8
    }
}

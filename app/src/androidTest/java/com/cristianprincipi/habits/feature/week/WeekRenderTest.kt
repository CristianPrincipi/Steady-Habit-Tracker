package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.PeriodCell
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * The Week screen on a device, driven through its stateless half.
 *
 * The subject is the join between a **pill's position** and **the date it stands for** —
 * `WeekContent.toggleAt`. Nothing else in this milestone can go wrong quietly: a wrong colour or a
 * missing card shows up in a screenshot, but a tap on Thursday that writes Wednesday looks entirely
 * correct until somebody checks the data days later. `WeekViewModelTest` cannot catch it either,
 * because it fires events directly instead of through the pills.
 *
 * `SampleHabits.referenceDate` is **Saturday 22 August 2026**, so the week runs Mon 17 – Sun 23 and
 * holds completed, incomplete, today and future days at once.
 */
@RunWith(AndroidJUnit4::class)
class WeekRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reference = SampleHabits.referenceDate
    private val monday = startOfWeek(reference)

    /** The habit every date-based assertion below is made against. */
    private val subject: Habit = SampleHabits.habits().first()

    private val events = mutableListOf<WeekEvent>()

    /** `createComposeRule` has no activity, so strings come from the app context. */
    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showWeek() {
        composeTestRule.setContent {
            HabitsTheme {
                WeekContent(
                    state =
                        WeekUiState.Content(
                            weekStart = monday,
                            habits = SampleHabits.weekPeriods(reference),
                            isCurrentWeek = true,
                        ),
                    onEvent = { event -> events += event },
                )
            }
        }
    }

    /**
     * One habit's pill for one date.
     *
     * ⚠ **The habit half is not optional.** All five cards carry a pill for every date in the week,
     * so a description of the date alone matches five nodes and the query fails outright — which is
     * exactly the ambiguity a screen-reader user would hear. `WeekContent.spokenLabels` names both.
     */
    private fun pill(
        habit: Habit,
        date: LocalDate,
    ) = composeTestRule.onNodeWithContentDescription(
        string(R.string.week_pill_description)
            .format(habit.name, accessibleDate(date, Locale.getDefault())),
        useUnmergedTree = true,
    )

    private fun toggled() = events.filterIsInstance<WeekEvent.DayToggled>().single()

    /**
     * Any habit and day in the sample week that is in [state].
     *
     * ⚠ **Searched across every habit, not fixed to one, and that is not fussiness.** The first
     * version of this pinned it to [subject] and two tests failed with `NoSuchElementException`:
     * `Read` has the highest keep-rate in `SampleHabits`, so in this particular week it has no
     * `INCOMPLETE` day at all. Which habit happens to have missed a day is a property of a seeded
     * generator, and a test should not depend on it.
     */
    private fun dayWith(state: DayState): Pair<Habit, LocalDate> {
        val period =
            SampleHabits.weekPeriods(reference).first { candidate ->
                candidate.cells.any { it is PeriodCell.Day && it.state == state }
            }
        val day = period.cells.filterIsInstance<PeriodCell.Day>().first { it.state == state }
        return period.habit to day.date
    }

    @Test
    fun everyHabitInTheSampleGetsACard() {
        showWeek()

        SampleHabits.habits().forEach { habit ->
            composeTestRule.onNodeWithText(habit.name).assertIsDisplayed()
        }
    }

    @Test
    fun everyDayOfTheWeekIsReachableAndDistinct() {
        // Seven pills, seven different labels. A collision would make two days one target.
        showWeek()

        (0 until 7).forEach { offset ->
            pill(subject, monday.plusDays(offset.toLong())).assertIsDisplayed()
        }
    }

    @Test
    fun tappingAPillReportsThatPillsOwnDate() {
        // ⭐ The assertion this file exists for. The fourth pill must report **Thursday** — index 3
        // counted from the week start — and the screen would look identical if it said Wednesday.
        showWeek()
        val thursday = monday.plusDays(3)

        pill(subject, thursday).performClick()

        assertEquals(thursday, toggled().date)
    }

    @Test
    fun tappingAPillReportsThatPillsOwnHabit() {
        // The other axis of the same mistake: right day, wrong row.
        showWeek()
        val other = SampleHabits.habits().last()

        pill(other, monday).performClick()

        assertEquals(other.id, toggled().habitId)
    }

    @Test
    fun aCompletedPillAsksToBeUnticked() {
        // "States rather than toggles" (`ToggleCompletion`): the payload carries the wanted value,
        // so a tap on a completed day must say `completed = false`.
        showWeek()

        val (habit, date) = dayWith(DayState.COMPLETED)

        pill(habit, date).performClick()

        assertEquals(false, toggled().completed)
    }

    @Test
    fun anIncompletePillAsksToBeTicked() {
        showWeek()

        val (habit, date) = dayWith(DayState.INCOMPLETE)

        pill(habit, date).performClick()

        assertEquals(true, toggled().completed)
    }

    @Test
    fun aPillCarriesItsTickedStateIntoSemantics() {
        // What a screen reader announces. The pill is a checkbox, which is why the label names the
        // habit and the date but not the state — Android says "ticked" itself.
        showWeek()

        val (doneHabit, doneDate) = dayWith(DayState.COMPLETED)
        val (missedHabit, missedDate) = dayWith(DayState.INCOMPLETE)

        pill(doneHabit, doneDate).assertIsOn()
        pill(missedHabit, missedDate).assertIsOff()
    }

    // --- days that cannot be written (M6.7) ---------------------------------------------------

    @Test
    fun aFutureDayIsNotTappable() {
        // ⭐ The step's whole point, on real dates. The reference Saturday leaves Sunday still to
        // come, and the design already draws it dimmed — what it did not do was stop it behaving
        // like a live checkbox.
        showWeek()
        val sunday = monday.plusDays(6)

        pill(subject, sunday).performClick()

        assertEquals(emptyList<WeekEvent>(), events)
    }

    @Test
    fun aFutureDayIsMarkedUnavailableRatherThanSilentlyInert() {
        // ⚠ A pill that ignores a tap without saying so is worse for a screen-reader user than one
        // that refuses out loud: they get no signal at all. "Disabled in place" is the app's
        // established preference (ADR-060) and it is what carries the meaning here.
        showWeek()

        pill(subject, monday.plusDays(6)).assertIsNotEnabled()
    }

    @Test
    fun todayIsStillTappable() {
        // ⚠ The off-by-one that would hurt most. Today is not the future, and a boundary that
        // included it would disable the single pill this app exists to let you tap.
        showWeek()

        pill(subject, reference).assertIsEnabled()
        pill(subject, reference).performClick()

        assertEquals(reference, toggled().date)
    }

    @Test
    fun aPastDayStaysFreelyEditable() {
        // The step's other question, answered yes: Notion allows it, and catching up on a day you
        // missed is most of what a week view is for. Nothing restricts them.
        showWeek()

        pill(subject, monday).assertIsEnabled()
        pill(subject, monday).performClick()

        assertEquals(monday, toggled().date)
    }

    @Test
    fun theEmptyStateOffersNoWayToCreateAHabit() {
        // ⚠ Placeholder copy — M6.8 owns the wording. What is pinned is the shape it has to keep:
        // no call to action, because ADR-010 means there is no habit creation to offer.
        composeTestRule.setContent {
            HabitsTheme { WeekContent(state = WeekUiState.Empty, onEvent = {}) }
        }

        composeTestRule.onNodeWithText(string(R.string.week_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.week_empty_body)).assertIsDisplayed()
    }
}

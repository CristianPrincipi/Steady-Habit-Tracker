package com.cristianprincipi.habits.feature.week

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.shortDateLabel
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.component.ReadingEntry
import com.cristianprincipi.habits.core.designsystem.component.prefillText
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureDay
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.util.Locale

/**
 * The Week screen's number rows (M16.1), drawn through the real `WeekContent`.
 *
 * The subject is the mapping from a day's value to what the cell says: the number without a
 * trailing `.0`, **the user's dash for a day with none** (2026-10-16), and nothing at all for a day
 * that has not happened. A wrong one of these does not crash; it draws a believable row.
 *
 * One habit card above, not five, so the number card is on screen without scrolling.
 *
 * Since M16.2b a day that has happened is tappable and opens the number dialog; the last tests here
 * hold that, and the dialog's Save / Clear rules, still.
 */
@RunWith(AndroidJUnit4::class)
class WeekMeasureRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reference = SampleHabits.referenceDate
    private val monday = startOfWeek(reference)

    private val weight = Measure(MeasureId("w"), name = "Weight", emoji = "", unit = "kg", color = HabitColor.GREEN)

    /** Mon 82.94, Tue none, Wed 8.0 (a deliberately whole number), Thu–Fri none, then the future. */
    private val values = listOf(82.94, null, 8.0, null, null, null, null)

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showWeek(
        onEvent: (WeekEvent) -> Unit = {},
        readingEntry: ReadingEntry? = null,
    ) {
        // The reference date is a Saturday, so Sat is today and only Sun is future.
        val days = values.mapIndexed { index, value -> monday.plusDays(index.toLong()) to value }
        val week =
            MeasureWeek(
                measure = weight,
                days = days.map { (date, value) -> MeasureDay(date, value, isFuture = date > reference) },
                todayIndex = 5,
            )
        composeTestRule.setContent {
            HabitsTheme {
                WeekContent(
                    state =
                        WeekUiState.Content(
                            weekStart = monday,
                            habits = SampleHabits.weekPeriods(reference).take(1),
                            isCurrentWeek = true,
                            currentYear = reference.year,
                            measures = listOf(week),
                            readingEntry = readingEntry,
                        ),
                    onEvent = onEvent,
                    onOpenSyncStatus = {},
                )
            }
        }
    }

    private fun cell(index: Int) =
        composeTestRule.onNodeWithContentDescription(
            string(R.string.week_measure_cell_description).format(
                weight.name,
                accessibleDate(monday.plusDays(index.toLong()), Locale.getDefault()),
                if (values[index] == null) {
                    string(R.string.week_measure_cell_empty)
                } else {
                    string(
                        R.string.week_measure_value_with_unit,
                    ).format(shown(requireNotNull(values[index])), weight.unit)
                },
            ),
            useUnmergedTree = true,
        )

    @Test
    fun theCardIsNamedAndCarriesTheWeeksAverage() {
        showWeek()

        composeTestRule.onNodeWithText(weight.name).assertIsDisplayed()
        // (82.94 + 8.0) / 2 — over the two days that had a reading, never over seven.
        val average = string(R.string.stats_measure_value_with_unit).format(45.47, weight.unit)
        composeTestRule
            .onNodeWithText(string(R.string.week_measure_average).format(average))
            .assertIsDisplayed()
    }

    @Test
    fun aValueDrawsToOneDecimalAndAWholeOneDropsItsZero() {
        showWeek()

        // `82.9` and `8` in English, `82,9` and `8` on the user's Italian phone.
        composeTestRule.onNodeWithText(shown(82.94), useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(shown(8.0), useUnmergedTree = true).assertIsDisplayed()
        cell(0).assertIsDisplayed()
        cell(2).assertIsDisplayed()
    }

    @Test
    fun aPastDayWithoutAValueIsADashAndIsSpokenAsNoEntry() {
        showWeek()

        // Tue, Thu, Fri and Sat (today) have none — four dashes. Sunday is future and draws nothing.
        composeTestRule
            .onAllNodesWithText(string(R.string.week_measure_missing), useUnmergedTree = true)
            .assertCountEquals(4)
        cell(1).assertIsDisplayed()
    }

    @Test
    fun aFutureDayIsTheDateAloneAndDrawsNoDash() {
        showWeek()

        composeTestRule
            .onNodeWithContentDescription(
                string(R.string.week_pill_description)
                    .format(weight.name, accessibleDate(monday.plusDays(6), Locale.getDefault())),
                useUnmergedTree = true,
            ).assertIsDisplayed()
    }

    @Test
    fun everyDayThatHasHappenedIsTappableAndTheFutureIsNot() {
        showWeek()

        // M16.2b. Monday to Saturday (today), recorded or not; Sunday has not happened, and a cell
        // that answered a tap there would open a dialog whose Save could only be refused.
        (0..5).forEach { index -> cell(index).assert(hasClickAction()) }
        composeTestRule
            .onNodeWithContentDescription(
                string(R.string.week_pill_description)
                    .format(weight.name, accessibleDate(monday.plusDays(6), Locale.getDefault())),
                useUnmergedTree = true,
            ).assert(!hasClickAction())
    }

    @Test
    fun aTapSendsTheDayWithItsValueAsStoredNotAsDrawn() {
        // The cell draws 82.9; the field must start at 82.94, or an untouched Save rounds the value.
        val sent = mutableListOf<WeekEvent>()
        showWeek(onEvent = sent::add)

        cell(0).performClick()

        assertEquals(
            listOf(WeekEvent.ReadingCellTapped(weight.id, monday, prefillText(82.94, Locale.getDefault()))),
            sent,
        )
    }

    @Test
    fun theDialogIsNamedForTheNumberAndDayAndOffersClearOnlyForAValue() {
        showWeek(readingEntry = entry(text = "82.94", hadValue = true))

        composeTestRule
            .onNodeWithText(
                string(R.string.week_reading_title)
                    .format(weight.name, shortDateLabel(monday, Locale.getDefault())),
            ).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.week_reading_clear)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.week_reading_save)).assertIsEnabled()
    }

    @Test
    fun aDayWithoutAValueOffersNoClearAndCannotSaveEmpty() {
        showWeek(readingEntry = entry(text = "", hadValue = false))

        composeTestRule.onAllNodesWithText(string(R.string.week_reading_clear)).assertCountEquals(0)
        composeTestRule.onNodeWithText(string(R.string.week_reading_save)).assertIsNotEnabled()
        // Empty is where it starts, not a mistake — so no error under the field yet.
        composeTestRule.onAllNodesWithText(string(R.string.week_reading_invalid)).assertCountEquals(0)
    }

    @Test
    fun somethingThatIsNotANumberDisablesSaveAndSaysWhy() {
        showWeek(readingEntry = entry(text = "7:30", hadValue = false))

        composeTestRule.onNodeWithText(string(R.string.week_reading_save)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.week_reading_invalid)).assertIsDisplayed()
    }

    private fun entry(
        text: String,
        hadValue: Boolean,
    ) = ReadingEntry(weight.id, weight.name, weight.unit, monday, text, hadValue)

    /** What a recorded cell shows: one decimal at most, as `WeekMeasureRow` formats it. */
    private fun shown(value: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }.format(value)
}

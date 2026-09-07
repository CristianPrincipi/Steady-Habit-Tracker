package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
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
                    HabitIcon(
                        emoji = "🎸",
                        name = "Learn Guitar",
                        habitColor = HabitsTheme.colors.habit.blue,
                        size = HabitIconSize.SMALL,
                    )
                    HabitIcon(emoji = "💧", name = "Drink water", habitColor = HabitsTheme.colors.habit.green)
                    // The M7.2 fallback: no emoji, so the circle carries the name's initial.
                    HabitIcon(emoji = "", name = "Meditate", habitColor = HabitsTheme.colors.habit.orange)
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
                        completionContentDescription = "84% completion",
                        days = previewYear(),
                    )
                    ColorSwatchRow(
                        colors = HabitsTheme.colors.habit.assignable,
                        selectedIndex = 0,
                        contentDescriptions = SWATCH_NAMES,
                        onSelect = {},
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

    // --- the selected colour has to be visible, on every colour (M7.5) ------------------------

    @Test
    fun theSelectedSwatchLooksDifferentOnEveryColourInThePalette() {
        // ⭐ M6.11's lesson applied before it could bite again. The obvious marker for a chosen
        // swatch is a ring, and `Modifier.border` draws INSIDE the bounds — a ring around a circle
        // filled with the same colour is invisible, which is exactly how the today marker vanished
        // on a completed pill. So the marker is a check glyph, and this asserts the property rather
        // than the glyph: **selecting a swatch must change what it looks like**, on every colour.
        //
        // Each matters separately. A marker can read on blue and disappear on orange, and a test
        // that only ever selected the first colour would never find out. ⚠ Red (M15.2) is the
        // darkest of the five against the card, so it is the one this now has to carry.
        var selected by mutableStateOf(0)
        composeTestRule.setContent {
            HabitsTheme {
                // ⚠ **The background is load-bearing, and its absence made this test lie.** A bare
                // `setContent` draws on the host's white, and a 28dp circle inside a 48dp target
                // leaves plenty of that white in frame — so the brightest pixel was 0.96 whether or
                // not anything was selected. The app is dark-only; the test has to be too.
                Surface(color = MaterialTheme.colorScheme.background) {
                    ColorSwatchRow(
                        colors = HabitsTheme.colors.habit.assignable,
                        selectedIndex = selected,
                        contentDescriptions = SWATCH_NAMES,
                        onSelect = {},
                    )
                }
            }
        }

        SWATCH_NAMES.forEachIndexed { index, name ->
            selected = index
            composeTestRule.waitForIdle()
            val chosen = brightestPixel(composeTestRule.onNodeWithContentDescription(name).captureToImage())

            selected = NO_SELECTION
            composeTestRule.waitForIdle()
            val plain = brightestPixel(composeTestRule.onNodeWithContentDescription(name).captureToImage())

            assertTrue(
                "selecting the $name swatch put nothing brighter on it: $chosen against $plain",
                chosen > plain + MARKER_CONTRAST,
            )
        }
    }

    /**
     * The luminance of the lightest pixel in an image.
     *
     * ⚠ **This replaced a plain "the two images differ" comparison, which is what the week pill's
     * ring test uses — and here it was not enough.** Against the mutant that matters (a ring in the
     * habit's own colour, `Modifier.border` drawing inside the bounds — the M6.11 defect) the two
     * images *do* differ: a same-colour stroke over the fill's antialiased edge moves a few hundred
     * edge pixels while being invisible to a person. The test passed on a marker nobody could see.
     *
     * An unselected swatch is a flat fill on a dark ground, so its brightest pixel *is* the fill.
     * Anything drawn on top in the same colour cannot raise that; a white check raises it to ~1.0.
     * So this asserts the property the design actually needs — **the marker contrasts with what is
     * behind it** — rather than "something changed".
     */
    private fun brightestPixel(image: ImageBitmap): Float {
        val pixels = image.toPixelMap()
        var brightest = 0f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                brightest = maxOf(brightest, pixels[x, y].luminance())
            }
        }
        return brightest
    }

    @Test
    fun aColourSwatchIsAFullTouchTargetEvenThoughItIsDrawnSmaller() {
        // The same rule as a week pill: the design's 28dp circle keeps its size and the target
        // around it grows. Four of these sit in a row, so a shortfall would be four missed taps.
        composeTestRule.setContent {
            HabitsTheme {
                ColorSwatchRow(
                    colors = HabitsTheme.colors.habit.assignable,
                    selectedIndex = 0,
                    contentDescriptions = SWATCH_NAMES,
                    onSelect = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(SWATCH_NAMES.last())
            .assertHeightIsAtLeast(Sizing.minimumTouchTarget)
            .assertWidthIsAtLeast(Sizing.minimumTouchTarget)
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

        /** `ColorSwatchRow`'s equivalent: a stored colour that is not in the palette shown. */
        const val NO_SELECTION = -1

        /**
         * How much brighter the marker has to be than the swatch it sits on.
         *
         * Measured, not guessed: the four fills land between 0.44 and 0.67 luminance and the check
         * is white, so the real gap is 0.33 at its narrowest (purple is the widest at 0.56). 0.15
         * sits well below every one of those and well above the antialiasing noise that a
         * same-colour marker produces, which is the whole reason this number exists.
         */
        const val MARKER_CONTRAST = 0.15f

        /** The four assignable habit colours, in `HabitPalette.assignable` order. */
        /**
         * ⚠ **Must stay the same length as `HabitPalette.assignable`, and nothing enforces that.**
         *
         * `ColorSwatchRow` is handed `assignable` for its colours and this for its labels, so a
         * palette change that misses this line hands the row more colours than names. M15.2 added
         * red and this list is where that was felt — the JVM gate was green and the mismatch lived
         * here, in a suite that only runs on a device.
         */
        val SWATCH_NAMES = listOf("Blue", "Green", "Orange", "Purple", "Red")

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

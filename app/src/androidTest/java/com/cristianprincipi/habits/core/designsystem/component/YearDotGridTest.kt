package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * M8.4 — the Year grid's fills, its flow direction, and where it opens.
 *
 * ### Three claims, none of them checked before this file
 *
 * `MonthDotGridTest` explains why a pixel test is the only kind available here: the grid clears its
 * own semantics on purpose, so nothing else can reach what it draws. What is different about *this*
 * grid is that all three of its claims are ones a reasonable session would get wrong by copying the
 * Month grid, which sits in the same package and looks like the same component.
 *
 * 1. **The fills pair the opposite way.** Month draws `FUTURE` and `BEFORE_START` alike; this one
 *    draws `INCOMPLETE` and `FUTURE` alike and gives `BEFORE_START` its own, fainter fill. A year
 *    is long enough that a habit's start date is a real feature of the picture rather than noise
 *    (`docs/design-spec.md` §2, §4).
 * 2. **The layout is column-major.** `column = index / 7`, `row = index % 7` — the reverse of the
 *    Month grid's. Swap the two operators and you still get seven rows of plausible-looking noise.
 * 3. **It opens on today**, not on the oldest day, which is the one place this deliberately departs
 *    from the web prototype.
 *
 * ### ⚠ Why the dot size here is [Sizing.yearDotLarge]
 *
 * Not for legibility in a bitmap — because of ADR-087 §4. A Pixel 9 is **411dp** (1080px at 420dpi,
 * density 2.625), so a Year card's interior is 347dp and the grid at [Sizing.yearDotSmall] is
 * 316dp: it **fits**, `maxValue` is 0, and a scroll assertion against it would pass with the
 * `LaunchedEffect` deleted. The large size is what the Stats screen renders, so it is a real case
 * rather than a contrived one — and the scroll tests below constrain the width as well, so they do
 * not depend on any particular device being narrow enough.
 */
@RunWith(AndroidJUnit4::class)
class YearDotGridTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var background = Color.Black

    @Composable
    private fun Grid(
        days: List<DayStatus>,
        width: Dp,
    ) {
        // ⚠ The app is dark-only and so is this test — a bare `setContent` draws on the host's
        // white, which is the mistake M7.5's second attempt made and paid for.
        Surface(color = MaterialTheme.colorScheme.background) {
            background = MaterialTheme.colorScheme.background
            YearDotGrid(
                days = days,
                habitColor = HabitsTheme.colors.habit.blue,
                modifier = Modifier.width(width).testTag(GRID),
                dotSize = DOT,
            )
        }
    }

    private fun show(
        days: List<DayStatus>,
        width: Dp = WIDE,
    ) {
        composeTestRule.setContent { HabitsTheme { Grid(days, width) } }
        composeTestRule.waitForIdle()
    }

    private fun image(): ImageBitmap = composeTestRule.onNodeWithTag(GRID).captureToImage()

    /** The colour drawn at the centre of one cell, addressed the way the design speaks of it. */
    private fun cellColour(
        image: ImageBitmap,
        column: Int,
        row: Int,
    ): Color {
        val pixels = image.toPixelMap()
        val step = with(composeTestRule.density) { (DOT + GAP).toPx() }
        val half = with(composeTestRule.density) { DOT.toPx() } / 2
        return pixels[(column * step + half).roundToInt(), (row * step + half).roundToInt()]
    }

    /** How far a cell has travelled from the ground behind it — monotonic in alpha, unlike luminance. */
    private fun distance(colour: Color): Float =
        abs(colour.red - background.red) +
            abs(colour.green - background.green) +
            abs(colour.blue - background.blue)

    /** The brightest thing anywhere in the viewport, for the two scroll tests. */
    private fun brightestDistance(image: ImageBitmap): Float {
        val pixels = image.toPixelMap()
        var most = 0f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                most = maxOf(most, distance(pixels[x, y]))
            }
        }
        return most
    }

    /** A column of one cell per state, so every fill is in the first column at a known row. */
    private val oneOfEach =
        listOf(
            DayStatus.COMPLETED,
            DayStatus.INCOMPLETE,
            DayStatus.FUTURE,
            DayStatus.BEFORE_START,
            DayStatus.PADDING,
            DayStatus.PADDING,
            DayStatus.PADDING,
        )

    private fun onlyAt(
        index: Int,
        cells: Int,
    ): List<DayStatus> = List(cells) { if (it == index) DayStatus.COMPLETED else DayStatus.PADDING }

    // --- the fills ----------------------------------------------------------------------------

    @Test
    fun theFourStatesAreThreeFillsAndNothing() {
        show(oneOfEach)

        val picture = image()
        val completed = distance(cellColour(picture, column = 0, row = COMPLETED_ROW))
        val incomplete = distance(cellColour(picture, column = 0, row = INCOMPLETE_ROW))
        val beforeStart = distance(cellColour(picture, column = 0, row = BEFORE_START_ROW))
        val padding = distance(cellColour(picture, column = 0, row = PADDING_ROW))

        assertTrue("a completed day is the habit colour at full strength, got $completed", completed > incomplete)
        assertTrue("an incomplete day must be visible against the ground, got $incomplete", incomplete > beforeStart)
        assertTrue("a day before the habit started must still be drawn, got $beforeStart", beforeStart > padding)
        assertEquals("a padding cell draws nothing at all", 0f, padding, EXACT)
    }

    @Test
    fun theYearGridDoesSayWhenAHabitBeganAndTheMonthGridDoesNot() {
        // ⭐⭐ The pairing, and it is the **opposite** of `MonthDotGridTest`'s equality. There, FUTURE
        // and BEFORE_START are one fill and the grid says nothing about when a habit began; here a
        // year is long enough for a start date to be a feature of the picture, so BEFORE_START is
        // its own, fainter fill and FUTURE joins INCOMPLETE instead.
        //
        // A `when` copied across from the Month grid satisfies neither assertion, which is the
        // point: the two components look interchangeable and are not.
        show(oneOfEach)

        val picture = image()
        val incomplete = distance(cellColour(picture, column = 0, row = INCOMPLETE_ROW))
        val future = distance(cellColour(picture, column = 0, row = FUTURE_ROW))
        val beforeStart = distance(cellColour(picture, column = 0, row = BEFORE_START_ROW))

        assertEquals("a future day is drawn exactly like an incomplete one", incomplete, future, EXACT)
        assertTrue(
            "a day before the habit started must be fainter than a plain miss: $beforeStart against $incomplete",
            beforeStart < incomplete,
        )
    }

    // --- the flow -----------------------------------------------------------------------------

    @Test
    fun theGridFlowsDownColumnsRatherThanAcrossRows() {
        // ⭐⭐ `gridAutoFlow: column` (`docs/design-spec.md` §4). Index 1 is the second cell of the
        // FIRST column, and index 7 is the top of the SECOND — the exact pair that trades places if
        // `index / 7` and `index % 7` are swapped, which is what copying the Month grid does.
        //
        // Nothing else would notice. Both arrangements draw seven rows of the same cells in the same
        // colours, and a year of habit data looks like plausible noise either way.
        show(onlyAt(index = 1, cells = TWO_COLUMNS))

        val secondCellOfFirstColumn = image()
        assertTrue(
            "index 1 belongs below index 0, not beside it",
            distance(cellColour(secondCellOfFirstColumn, column = 0, row = 1)) > MARK,
        )
        assertEquals(
            "index 1 must not be drawn at the top of the second column",
            0f,
            distance(cellColour(secondCellOfFirstColumn, column = 1, row = 0)),
            EXACT,
        )
    }

    @Test
    fun theEighthCellStartsTheSecondColumn() {
        // The other half of the same swap, so neither direction can pass by accident.
        show(onlyAt(index = DAYS_PER_WEEK, cells = TWO_COLUMNS))

        val picture = image()
        assertTrue(
            "index 7 begins the second column",
            distance(cellColour(picture, column = 1, row = 0)) > MARK,
        )
        assertEquals(
            "index 7 must not be drawn as the second cell of the first column",
            0f,
            distance(cellColour(picture, column = 0, row = 1)),
            EXACT,
        )
    }

    @Test
    fun thereAreSevenRowsWhateverTheDayCount() {
        // Height is Mon→Sun and nothing else: it must not follow the day count the way the Month
        // grid's row count does. A year and a fortnight are the same height and different widths.
        val expected = DOT * DAYS_PER_WEEK + GAP * (DAYS_PER_WEEK - 1)

        show(List(A_YEAR_OF_CELLS) { DayStatus.COMPLETED })
        composeTestRule.onNodeWithTag(GRID).assertHeightIsEqualTo(expected)
    }

    // --- where it opens -----------------------------------------------------------------------

    @Test
    fun theGridOpensOnTodayRatherThanOnTheOldestDay() {
        // ⭐⭐ The one place this deliberately departs from the web prototype, which starts at the
        // far past. On a phone the visible slice is a few weeks wide and the interesting end of a
        // year of habits is the recent one.
        //
        // ⚠ The width is constrained on purpose. ADR-087 §4: at `yearDotSmall` on a Pixel 9 this
        // grid FITS its card — `maxValue` is 0 — so an unconstrained version of this test would
        // pass with the `LaunchedEffect` deleted, which is a test that proves nothing.
        show(onlyAt(index = A_YEAR_OF_CELLS - 1, cells = A_YEAR_OF_CELLS), width = NARROW)

        assertTrue(
            "the most recent day should be on screen when the grid first appears",
            brightestDistance(image()) > MARK,
        )
    }

    @Test
    fun theOldestDayIsScrolledPastRatherThanShown() {
        // The other half, and the half that fails if the effect scrolls to 0 instead of to the end:
        // the first cell of a year must be off screen in a viewport a few weeks wide.
        show(onlyAt(index = 0, cells = A_YEAR_OF_CELLS), width = NARROW)

        assertEquals(
            "the oldest day must be scrolled off the start, not showing",
            0f,
            brightestDistance(image()),
            EXACT,
        )
    }

    // --- read-only, and not by way of semantics (M8.5) ------------------------------------------

    @Test
    fun aTapOnTheGridReachesWhateverIsBehindItRatherThanBeingSwallowed() {
        // ⭐⭐ The same assertion `MonthDotGridTest` carries, and for the same measured reason: a
        // `Modifier.clickable` placed after `clearAndSetSemantics` is invisible to every
        // `hasClickAction()` count in the suite and still swallows a finger. An inert child does not
        // consume a pointer event, so the tap must arrive at the clickable behind it.
        //
        // ⚠ Worth having twice rather than once. This grid is the one three screens share
        // (`docs/design-spec.md` §4, §5.2), so a handler added here would be tappable in Stats as
        // well — and at 4dp a cell is a quarter of a usable target wherever it is drawn.
        var behind = 0
        composeTestRule.setContent {
            HabitsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.width(WIDE).clickable { behind++ }) {
                        YearDotGrid(
                            days = oneOfEach,
                            habitColor = HabitsTheme.colors.habit.blue,
                            modifier = Modifier.testTag(GRID),
                            dotSize = DOT,
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(GRID, useUnmergedTree = true).performTouchInput { click() }

        assertEquals("a tap on a read-only grid belongs to whatever is behind it", 1, behind)
    }

    private companion object {
        const val GRID = "year-dot-grid"

        /** Stats' own size (`docs/design-spec.md` §5.2) — a real case, and see the class KDoc. */
        val DOT = Sizing.yearDotLarge
        val GAP = 2.dp

        /** Wide enough that nothing scrolls, so a cell's position is its position. */
        val WIDE = 400.dp

        /** A few weeks, so a year cannot fit and the scroll has somewhere to go. */
        val NARROW = 60.dp

        const val COMPLETED_ROW = 0
        const val INCOMPLETE_ROW = 1
        const val FUTURE_ROW = 2
        const val BEFORE_START_ROW = 3
        const val PADDING_ROW = 4

        const val TWO_COLUMNS = 14
        const val A_YEAR_OF_CELLS = 371

        /** These are flat fills at a cell's centre, so "the same colour" means the same colour. */
        const val EXACT = 0f

        /** Comfortably above the faintest fill the design draws, and far below a solid one. */
        const val MARK = 0.2f
    }
}

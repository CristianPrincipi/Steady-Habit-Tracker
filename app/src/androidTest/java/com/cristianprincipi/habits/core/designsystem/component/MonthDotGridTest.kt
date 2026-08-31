package com.cristianprincipi.habits.core.designsystem.component

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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * M8.2 — the Month grid's four per-day states, asserted rather than described.
 *
 * ### Why this needs pixels, and why nothing had them
 *
 * `MonthDotGrid` calls `clearAndSetSemantics {}` on purpose: 31 undifferentiated cells are noise to
 * a screen reader, and the card's completion figures carry the same information in a form worth
 * reading aloud. The consequence is that **a screen reader cannot see this grid and neither can a
 * semantics assertion** — so a build where `FUTURE` drew as `COMPLETED` would pass every one of the
 * 621 JVM tests and the render smoke test beside it, and show a month of solid colour to the user.
 *
 * ### The metric, and why it is not luminance
 *
 * M7.5's swatch test uses the brightest pixel, which works there because a white check on a
 * saturated fill is a large luminance step. The steps here are **not** large: the design expresses
 * the two dimmed states as a tint times an opacity, giving alphas of 0.045 and 0.015 over a near
 * black ground. On the blue habit colour that is about 7/255 of separation on the blue channel
 * alone, and rather less in luminance.
 *
 * So the metric is **distance from the background**, summed across the three channels. It is
 * monotonic in alpha by construction, which is exactly the property the design specifies — full,
 * dimmer, dimmest, nothing — and it does not need the three fills to differ in brightness the way a
 * luminance comparison silently assumes.
 *
 * ### What the design says, including the part that looks like a bug
 *
 * `FUTURE` and `BEFORE_START` are drawn **identically** here (`docs/design-spec.md` §2): a Month
 * grid says nothing about when a habit began, and only the Year grid tells the two apart. That is
 * asserted as an equality rather than left implicit, so a future session that "fixes" it has to
 * come here and read why first.
 */
@RunWith(AndroidJUnit4::class)
class MonthDotGridTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * One row, one cell per state, then padding.
     *
     * Seven cells exactly, so the grid is a single row and every sample is at a known column.
     */
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

    private var background = Color.Black
    private var habitColour = Color.Unspecified

    @Composable
    private fun Grid(days: List<DayStatus>) {
        // ⚠ The app is dark-only and so is this test. A bare `setContent` draws on the host's
        // white, which would put every one of these fills on the wrong side of the ground they are
        // designed against — the mistake M7.5's second attempt made and paid for.
        Surface(color = MaterialTheme.colorScheme.background) {
            background = MaterialTheme.colorScheme.background
            habitColour = HabitsTheme.colors.habit.blue
            MonthDotGrid(
                days = days,
                habitColor = HabitsTheme.colors.habit.blue,
                modifier = Modifier.width(GRID_WIDTH).testTag(GRID),
            )
        }
    }

    private fun show(days: List<DayStatus> = oneOfEach) {
        composeTestRule.setContent { HabitsTheme { Grid(days) } }
    }

    /** The colour drawn at the centre of column [column] of the first row. */
    private fun cellColour(
        image: ImageBitmap,
        column: Int,
    ): Color {
        val pixels = image.toPixelMap()
        val gapPx = with(composeTestRule.density) { GRID_GAP.toPx() }
        val cellPx = (pixels.width - gapPx * (COLUMNS - 1)) / COLUMNS
        val x = (column * (cellPx + gapPx) + cellPx / 2).roundToInt()
        val y = (cellPx / 2).roundToInt()
        return pixels[x, y]
    }

    /**
     * How far a cell's colour has travelled from the ground behind it.
     *
     * Monotonic in the alpha the design specifies, which is the whole point: it orders the states
     * without assuming anything about which channel carries the difference.
     */
    private fun distanceFromBackground(colour: Color): Float =
        abs(colour.red - background.red) +
            abs(colour.green - background.green) +
            abs(colour.blue - background.blue)

    private fun distances(): List<Float> {
        val image = composeTestRule.onNodeWithTag(GRID).captureToImage()
        return List(COLUMNS) { column -> distanceFromBackground(cellColour(image, column)) }
    }

    @Test
    fun theFourStatesAreFourDifferentFills() {
        // ⭐⭐ The assertion this file exists for. Three mutants fail here and nowhere else:
        // COMPLETED drawn at the incomplete alpha, INCOMPLETE drawn at full strength, and FUTURE
        // drawn as INCOMPLETE — the last of which is the one a reasonable session would write,
        // because collapsing two states that look similar is a tidy-looking simplification.
        show()

        val values = distances()
        val completed = values[COMPLETED_COLUMN]
        val incomplete = values[INCOMPLETE_COLUMN]
        val future = values[FUTURE_COLUMN]
        val padding = values[PADDING_COLUMN]

        assertTrue("a completed day is the habit colour at full strength, got $completed", completed > incomplete)
        assertTrue("an incomplete day must be visible against the ground, got $incomplete", incomplete > future)
        assertTrue("a future day must still be drawn, got $future", future > padding)
    }

    @Test
    fun aPaddingCellDrawsNothingAtAll() {
        // Not "draws something faint" — nothing. The leading blanks align the 1st to its weekday and
        // stand for no day, so a fill there would be a day the month does not have.
        show()

        assertEquals(0f, distances()[PADDING_COLUMN], EXACT)
    }

    @Test
    fun theMonthGridDoesNotSayWhenAHabitBegan() {
        // ⚠ The design's own choice (`docs/design-spec.md` §2), asserted so it cannot be "fixed" by
        // accident: FUTURE and BEFORE_START are the same fill here, and only the Year grid separates
        // them. A month is too short a window for a start date to be a feature of the picture.
        show()

        val values = distances()
        assertEquals(values[FUTURE_COLUMN], values[BEFORE_START_COLUMN], EXACT)
    }

    @Test
    fun aCompletedDayIsTheHabitColourItself() {
        // The other end of the scale, and the one a person actually reads: a checked day is the
        // habit's colour undimmed. Sampled at the cell's centre, so no antialiased edge is in it.
        show()

        val image = composeTestRule.onNodeWithTag(GRID).captureToImage()
        val completed = cellColour(image, COMPLETED_COLUMN)

        assertTrue(
            "a completed cell should be the habit colour, got $completed against $habitColour",
            abs(completed.red - habitColour.red) < CHANNEL_TOLERANCE &&
                abs(completed.green - habitColour.green) < CHANNEL_TOLERANCE &&
                abs(completed.blue - habitColour.blue) < CHANNEL_TOLERANCE,
        )
    }

    @Test
    fun theLastRowIsRaggedRatherThanPaddedOut() {
        // ⭐ `docs/design-spec.md` §2: there is NO trailing pad, so a 31-day August that starts on a
        // Saturday is six rows with one cell in the last. The height follows the row count, so a
        // grid that padded to a whole week would be the same six rows — and a grid that rounded
        // DOWN would clip the 31st off the bottom entirely.
        val august = List(5) { DayStatus.PADDING } + List(31) { DayStatus.COMPLETED }
        show(august)

        val cell = (GRID_WIDTH - GRID_GAP * (COLUMNS - 1)) / COLUMNS
        composeTestRule
            .onNodeWithTag(GRID)
            .assertHeightIsEqualTo(cell * AUGUST_ROWS + GRID_GAP * (AUGUST_ROWS - 1))
    }

    private companion object {
        const val GRID = "month-dot-grid"
        val GRID_WIDTH = 210.dp
        val GRID_GAP = 3.dp
        const val COLUMNS = 7

        const val COMPLETED_COLUMN = 0
        const val INCOMPLETE_COLUMN = 1
        const val FUTURE_COLUMN = 2
        const val BEFORE_START_COLUMN = 3
        const val PADDING_COLUMN = 4

        /** Six rows: five leading blanks plus 31 days is 36 cells, and 36 does not divide by 7. */
        const val AUGUST_ROWS = 6

        /** These are flat fills at a cell's centre, so "the same colour" means the same colour. */
        const val EXACT = 0f

        /** One 8-bit step, for a fill read back through a bitmap. */
        const val CHANNEL_TOLERANCE = 1f / 255f
    }
}

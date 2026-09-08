package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M15.6b — how the dot grid is fitted to the space a widget actually got.
 *
 * ### The defect these were rewritten for
 *
 * The first version fixed the dot at 6dp and derived only the column count, and on the user's phone
 * it drew a grid sized for a **160×120dp** bucket inside a **176×107dp** card: too narrow across,
 * and taller than the space it had. Two causes, one visible symptom — `SizeMode.Responsive` reports
 * the matched *bucket* rather than the real size, and nothing sized the dots to the height at all.
 *
 * `USER_*` below are that card, measured off their screenshot rather than imagined, and
 * [theGridFillsTheSpaceItIsGivenAtTheSizeMeasuredOnTheUsersPhone] is the regression.
 */
class WidgetGridFitTest {
    @Test
    fun theGridFillsTheSpaceItIsGivenAtTheSizeMeasuredOnTheUsersPhone() {
        val fit = fitGrid(USER_AVAILABLE_WIDTH_DP, USER_AVAILABLE_HEIGHT_DP)

        // ⚠ The height assertion is the one that would have caught the original bug: the old grid
        // wanted 54dp of a 37dp slot, so it did not merely look sparse — it did not fit.
        assertTrue(
            "grid is ${fit.heightDp}dp tall in ${USER_AVAILABLE_HEIGHT_DP}dp",
            fit.heightDp <= USER_AVAILABLE_HEIGHT_DP + TOLERANCE,
        )
        assertTrue(
            "grid fills only ${100 * fit.heightDp / USER_AVAILABLE_HEIGHT_DP}% of the height",
            fit.heightDp >= USER_AVAILABLE_HEIGHT_DP * FILL,
        )
        assertTrue(
            "grid is ${fit.widthDp}dp wide in ${USER_AVAILABLE_WIDTH_DP}dp",
            fit.widthDp <= USER_AVAILABLE_WIDTH_DP + TOLERANCE,
        )
        assertTrue(
            "grid fills only ${100 * fit.widthDp / USER_AVAILABLE_WIDTH_DP}% of the width",
            fit.widthDp >= USER_AVAILABLE_WIDTH_DP * FILL,
        )
    }

    @Test
    fun aGridNeverOverflowsTheSpaceItIsGivenAtAnyPlausibleWidgetSize() {
        // Swept rather than sampled, because the failure above was at one specific size and a
        // single example would have missed it. Every size a launcher could hand us must fit.
        for (width in 90..400 step 10) {
            for (height in 30..260 step 10) {
                val fit = fitGrid(width.toFloat(), height.toFloat())
                assertTrue("$width x $height overflowed across", fit.widthDp <= width + TOLERANCE)
                assertTrue("$width x $height overflowed down", fit.heightDp <= height + TOLERANCE)
                assertTrue("$width x $height had no columns", fit.columns >= 1)
            }
        }
    }

    @Test
    fun halfTheWidthHoldsAboutHalfTheHistoryAtTheSameDotSize() {
        // The reference's own claim (docs/widget-reference/README.md): same dot size, half the
        // history. Asserted as a relationship, because the absolute counts move with the device
        // and the ratio does not.
        val wide = fitGrid(availableWidthDp = 296f, availableHeightDp = 60f)
        val narrow = fitGrid(availableWidthDp = 136f, availableHeightDp = 60f)

        assertEquals("same height means the same dot size", wide.dotDp, narrow.dotDp, TOLERANCE)
        assertTrue(
            "narrow (${narrow.columns}) should be about half of wide (${wide.columns})",
            narrow.columns * 2 in (wide.columns - 4)..(wide.columns + 4),
        )
    }

    @Test
    fun aTallerWidgetDrawsBiggerDotsRatherThanMoreRows() {
        // Seven rows is the one count that never changes — that is what makes a column a week. So
        // vertical space can only buy dot size, which is also why the provider allows a vertical
        // resize at all.
        val short = fitGrid(availableWidthDp = 200f, availableHeightDp = 40f)
        val tall = fitGrid(availableWidthDp = 200f, availableHeightDp = 90f)

        assertTrue("taller should mean bigger dots", tall.dotDp > short.dotDp)
        assertTrue("and therefore fewer weeks in the same width", tall.columns < short.columns)
    }

    @Test
    fun aVeryWideWidgetStopsAtAYearRatherThanPaddingItselfOut() {
        // There is no more history to show, and the extra blanks would read as missed days.
        assertEquals(WEEKS_IN_YEAR, fitGrid(availableWidthDp = 4000f, availableHeightDp = 40f).columns)
    }

    @Test
    fun anImpossiblyThinWidgetStillAsksForOneColumnNeverZero() {
        // Zero columns would size the Bitmap at a negative width — see DotGridGeometryTest.
        assertEquals(1, fitGrid(availableWidthDp = 0f, availableHeightDp = 40f).columns)
    }

    @Test
    fun theSliceIsAlwaysAWholeNumberOfWeeksTakenFromTheRecentEnd() {
        // ⚠ The alignment is the point. The year arrives column-major and Monday-aligned, so a
        // slice that was not a multiple of seven would shift every day into the wrong row — and
        // would still draw a perfectly plausible-looking grid.
        val year = List(371) { index -> if (index % 3 == 0) DayStatus.COMPLETED else DayStatus.INCOMPLETE }

        val slice = year.lastWeeks(columns = 10)

        assertEquals(70, slice.size)
        assertEquals(year.takeLast(70), slice)
    }

    @Test
    fun askingForMoreWeeksThanExistYieldsWhatThereIs() {
        // A habit tracked for a fortnight, on a wide widget. Fewer dots — not a crash, not padding.
        val fortnight = List(14) { DayStatus.COMPLETED }

        assertEquals(14, fortnight.lastWeeks(columns = WEEKS_IN_YEAR).size)
    }

    private companion object {
        const val TOLERANCE = 0.01f

        /** How much of each axis a fitted grid must cover. One dot's worth of slack, no more. */
        const val FILL = 0.85f

        /** The card measured on the user's OnePlus 12: 176dp wide, less 12dp padding each side. */
        const val USER_AVAILABLE_WIDTH_DP = 152f

        /** 107dp tall, less that padding, the 36dp badge row and the 10dp gap under it. */
        const val USER_AVAILABLE_HEIGHT_DP = 37f
    }
}

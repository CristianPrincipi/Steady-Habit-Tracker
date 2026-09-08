package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M15.6b — the reference's "firmer fact", asserted instead of eyeballed.
 *
 * `docs/widget-reference/README.md` records the reference's column counts as "~30 and ~15, counted
 * off the image by eye", and says the **ratio** is the reliable part: same dot size, half the
 * history in half the width. ADR-128 §4 measured it and agreed.
 *
 * ⚠⚠ **Our large widget holds MORE than the reference's ~31 columns, and that is correct.** The
 * counts were never the specification — the pitch was. The reference's large widget is about 255pt
 * wide; a four-cell Android widget is wider, so at the same 8dp pitch it fits more weeks. Matching
 * 31 exactly would mean growing the dots on Android, which breaks the one thing the reference
 * actually holds constant between its two sizes.
 */
class WidgetGridFitTest {
    @Test
    fun `half the width holds about half the history at the same dot size`() {
        val wide = widgetGridColumns(availableDp = WIDE_DP, dotDp = DOT, gapDp = GAP)
        val narrow = widgetGridColumns(availableDp = NARROW_DP, dotDp = DOT, gapDp = GAP)

        assertEquals(37, wide)
        assertEquals(17, narrow)
        // The reference's actual claim: halving the width roughly halves the history. Asserted as
        // a relationship, because the absolute counts move with the device and the ratio does not.
        assertTrue("narrow ($narrow) should be about half of wide ($wide)", narrow * 2 in (wide - 4)..(wide + 4))
    }

    @Test
    fun `a wider widget stops at a year rather than padding itself out`() {
        // There is no more history to show. Without the cap a tablet-width widget would ask for
        // more columns than the year has, and the extra blanks would read as missed days.
        assertEquals(WEEKS_IN_YEAR, widgetGridColumns(availableDp = 4000f, dotDp = DOT, gapDp = GAP))
    }

    @Test
    fun `an impossibly narrow widget still asks for one column, never zero`() {
        // Zero columns would size the Bitmap at a negative width — see DotGridGeometryTest.
        assertEquals(1, widgetGridColumns(availableDp = 0f, dotDp = DOT, gapDp = GAP))
    }

    @Test
    fun `the slice is always a whole number of weeks, taken from the recent end`() {
        // ⚠ The alignment is the point. The year arrives column-major and Monday-aligned, so a
        // slice that was not a multiple of seven would shift every day into the wrong row — and
        // would still draw a perfectly plausible-looking grid.
        val year = List(371) { index -> if (index % 3 == 0) DayStatus.COMPLETED else DayStatus.INCOMPLETE }

        val slice = year.lastWeeks(columns = 10)

        assertEquals(70, slice.size)
        assertEquals(year.takeLast(70), slice)
    }

    @Test
    fun `asking for more weeks than exist yields what there is`() {
        // A habit tracked for a fortnight, on a wide widget. Fewer dots — not a crash, not padding.
        val fortnight = List(14) { DayStatus.COMPLETED }

        assertEquals(14, fortnight.lastWeeks(columns = WEEKS_IN_YEAR).size)
    }

    private companion object {
        const val DOT = 6f
        const val GAP = 2f

        /** A four-cell widget (320dp) less the card's 12dp padding on each side. */
        const val WIDE_DP = 296f

        /** The two-cell widget (160dp), same padding. */
        const val NARROW_DP = 136f
    }
}

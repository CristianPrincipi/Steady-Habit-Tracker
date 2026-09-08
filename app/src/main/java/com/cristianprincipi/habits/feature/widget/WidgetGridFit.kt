package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.designsystem.component.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.designsystem.component.DayStatus

/*
 * How much history a widget of a given width can hold, and which part of the year that is.
 *
 * ⭐⭐ Out of the composable on purpose. These two functions are the whole of the reference's
 * "firmer fact" — same dot size, half the history (`docs/widget-reference/README.md`) — and left
 * inline they would be a claim only a human comparing two home-screen screenshots could check.
 * Here they are two assertions.
 */

/**
 * The number of week-columns that fit in [availableDp] at a fixed dot size.
 *
 * ⚠ **The dot size is the constant and the column count is derived**, which is the opposite of how
 * the step first described this ("~30 columns large, ~15 small"). Measuring the reference settled
 * it: both widgets draw dots at an identical pitch (ADR-128 §4), so a narrow widget holds fewer
 * weeks rather than smaller dots. Deriving columns from a target count instead would shrink the
 * dots until the small widget was a different picture of the same year.
 *
 * Capped at [WEEKS_IN_YEAR] because there is no more history to show — a wider widget draws a year
 * and then stops, rather than padding itself out with blanks.
 */
internal fun widgetGridColumns(
    availableDp: Float,
    dotDp: Float,
    gapDp: Float,
): Int {
    // The trailing gap is added back because gaps sit *between* columns: n columns need n-1 gaps,
    // so the space that fits n is (n * dot) + ((n - 1) * gap), which rearranges to this.
    val fitted = ((availableDp + gapDp) / (dotDp + gapDp)).toInt()
    return fitted.coerceIn(1, WEEKS_IN_YEAR)
}

/**
 * The most recent [columns] weeks of a trailing year.
 *
 * ⚠ **The END of the list, matching `YearDotGrid`'s own decision to open scrolled to the end**: the
 * interesting end of a year of habits is the recent one, and a widget cannot be scrolled at all.
 *
 * ⭐ **Taking a multiple of seven is what keeps the rows meaning what they say.** The year arrives
 * column-major and Monday-aligned; dropping any other number of entries would shift every day into
 * the wrong row, and the result would still look like a perfectly plausible dot grid.
 */
internal fun List<DayStatus>.lastWeeks(columns: Int): List<DayStatus> = takeLast(columns * DAYS_PER_WEEK)

internal const val WEEKS_IN_YEAR = 53

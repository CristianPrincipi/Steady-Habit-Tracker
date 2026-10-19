package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.designsystem.component.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.designsystem.component.DayStatus

/*
 * How a dot grid is fitted to the space a widget actually got.
 *
 * ⭐⭐ Out of the composable on purpose. This is the whole of the reference's "firmer fact" —
 * same dot size, half the history (`docs/widget-reference/README.md`) — plus the filling rule the
 * user asked for after seeing v1 on their phone. Left inline it would be a claim only a human
 * comparing home-screen screenshots could check; here it is a handful of assertions.
 */

/** A fitted grid: how many week-columns, at what dot size and gap. All in dp. */
internal data class GridFit(
    val columns: Int,
    val dotDp: Float,
    val gapDp: Float,
) {
    val widthDp: Float get() = columns * dotDp + (columns - 1) * gapDp
    val heightDp: Float get() = DAYS_PER_WEEK * dotDp + (DAYS_PER_WEEK - 1) * gapDp
}

/**
 * Fits seven rows into [availableHeightDp] and as many weeks as will fit into [availableWidthDp].
 *
 * ⚠⚠ **The dot size comes from the HEIGHT, and that is what makes the grid fill its widget.** The
 * first version fixed the dot at 6dp and derived only the columns, which drew a 160dp-wide grid
 * inside a 176dp card and left the bottom third of it empty — measured on the user's phone, not
 * guessed. Seven rows is the one count that never changes, so the height is the constraint that
 * can size a dot; the width then decides how much history that dot size buys.
 *
 * ⭐ **The gap scales with the dot rather than staying at 2dp.** At [GAP_RATIO] = 3 a 6dp dot gets
 * the reference's own 2dp gap, so the proportion is preserved at every size instead of a fixed gap
 * swallowing a small grid and vanishing in a large one.
 *
 * ⚠ **[maxDotDp] means a very tall widget stops growing its dots** and leaves space below rather
 * than drawing seven fat rows. A dot grid reads as a density plot; past a certain size it stops
 * being one. [MIN_DOT_DP] is the other end: below it the dots merge into a smear.
 *
 * ⚠ A grid wider than a year caps at [WEEKS_IN_YEAR] columns and simply ends — there is no more
 * history, and padding it out would draw blanks that read as missed days.
 */
internal fun fitGrid(
    availableWidthDp: Float,
    availableHeightDp: Float,
    maxDotDp: Float = MAX_DOT_DP,
): GridFit {
    val dot = dotFilling(availableHeightDp).coerceIn(MIN_DOT_DP, maxDotDp)
    val gap = (dot / GAP_RATIO).coerceAtLeast(MIN_GAP_DP)
    // The trailing gap is added back because gaps sit *between* columns: n columns occupy
    // (n * dot) + ((n - 1) * gap), which rearranges to this.
    val columns = ((availableWidthDp + gap) / (dot + gap)).toInt().coerceIn(1, WEEKS_IN_YEAR)
    return GridFit(columns = columns, dotDp = dot, gapDp = gap)
}

/** The dot whose seven rows fill [heightDp]: height = rows*dot + (rows-1)*gap, with gap = dot/GAP_RATIO. */
private fun dotFilling(heightDp: Float): Float = heightDp / (DAYS_PER_WEEK + (DAYS_PER_WEEK - 1) / GAP_RATIO)

/**
 * [fitGrid] for a habit widget card of this size: what is left once the card's padding, the header
 * and the gap under it are taken out. One place, because the corner square is sized from this
 * grid's dot (`SquareFit.kt`) and two copies of the subtraction would drift.
 *
 * ### ⚠⚠ From a 2x2 up, the dot is a fixed share of the card's height — ADR-158
 *
 * **A launcher may draw a widget smaller than the size it reports, and by a different amount for each
 * widget.** The user's launcher reports their 2x2 as 148x148dp and their 2x3 as 286.5x179dp, then draws
 * both 110dp tall: the 2x2 at 0.745 of its size, the 2x3 at 0.614 — read off the phone's view tree, and
 * nothing in the widget's options says so. So the same dp comes out ~18% smaller on the 2x3, and the
 * user saw its corner square as smaller than the 2x2's beside it.
 *
 * ⭐ **That scaling keeps a widget's proportions, so a share of the card survives it.** Above the 2x2's
 * height the dot grows in step with the card — [TWO_BY_TWO_DOT_DP] per 148dp of it — and two widgets in
 * one row draw the same dot on the screen whatever each was scaled by. On a launcher that draws widgets
 * at their real size the rule is the same: equal heights, equal dots. Below a 2x2 nothing changes: the
 * grid fills the height, as it always has.
 */
internal fun fitHabitWidgetGrid(
    cardWidthDp: Float,
    cardHeightDp: Float,
): GridFit =
    fitGrid(
        availableWidthDp = cardWidthDp - CARD_PADDING.value * 2,
        availableHeightDp = habitGridHeight(cardHeightDp),
        maxDotDp = minOf(MAX_DOT_DP, TWO_BY_TWO_DOT_DP * (cardHeightDp / TWO_BY_TWO_CARD_DP)),
    )

/** What a habit card this tall leaves the grid. */
private fun habitGridHeight(cardHeightDp: Float): Float =
    cardHeightDp - CARD_PADDING.value * 2 - BADGE_SIZE.value - HEADER_GAP.value

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

/** A 6dp dot gets a 2dp gap — the reference's own proportion, held at every size. */
private const val GAP_RATIO = 3f
private const val MIN_DOT_DP = 2.5f

/**
 * ⚠⚠ **The cap the user chose to keep, knowingly (2026-09-08).** Removing it would let a large
 * widget fill its height exactly — at 492x225dp the dot wants 25dp — but a 7-row grid can only do
 * that by showing **fewer weeks**: 15 instead of ~31. They kept the longer view and asked for the
 * spare height to be **centred** instead, which `HabitGridWidget` does with a weighted `Box`.
 *
 * `internal` so the test can assert the trade-off rather than restate the number.
 */
internal const val MAX_DOT_DP = 12f

/** The user's 2x2 habit widgets are 148x148dp cards, read off their phone (ADR-153 §5). */
internal const val TWO_BY_TWO_CARD_DP = 148f

/**
 * The dot that card draws, ~8.67dp: its grid's height filled. Computed rather than typed, so it is the
 * same float the 2x2 itself gets, and follows the card's padding and header if they ever change.
 */
internal val TWO_BY_TWO_DOT_DP: Float = dotFilling(habitGridHeight(TWO_BY_TWO_CARD_DP))
private const val MIN_GAP_DP = 0.75f

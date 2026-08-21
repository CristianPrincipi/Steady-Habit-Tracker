package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.DayOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsCorners
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import kotlin.math.ceil

/** Monday-first, like every grid in this app (`docs/design-spec.md §2`). */
internal const val DAYS_PER_WEEK = 7

/**
 * One square per day of a month, seven across (`docs/design-spec.md §2`).
 *
 * **Read-only, and staying that way.** At a typical width each cell is about 14dp — far below a
 * usable touch target, and the reason check-off lives on the Week pills instead (ADR-009). Semantics
 * are cleared for the same reason: 31 undifferentiated cells would be noise to a screen reader, and
 * the card's completion figures carry the same information in a form worth reading aloud.
 *
 * Drawn on a `Canvas` rather than as 31 composables. The Year grid has 365 cells and must be a
 * canvas; drawing both the same way keeps one set of rules for how a day is coloured.
 *
 * Cells are square and the width is whatever the card gives, so the height follows from the width
 * and cannot be known before measurement — hence `BoxWithConstraints`. One subcomposition per card
 * is cheap; guessing the cell size would be wrong on every screen but the one it was guessed for.
 *
 * @param days one entry per cell in order, [DayStatus.PADDING] for the leading blanks that push the
 *   first of the month onto its real weekday. There is **no trailing padding** — the design leaves
 *   the last row ragged, and adding any would change the shape of every month.
 */
@Composable
fun MonthDotGrid(
    days: List<DayStatus>,
    habitColor: Color,
    modifier: Modifier = Modifier,
) {
    val rows = ceil(days.size / DAYS_PER_WEEK.toFloat()).toInt().coerceAtLeast(1)
    val tint = HabitsTheme.colors.tint.monthDotIncomplete

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = Spacing.monthDotGap
        val cell = (maxWidth - gap * (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK
        val gapPx = with(LocalDensity.current) { gap.toPx() }
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        val radiusPx = with(LocalDensity.current) { HabitsCorners.monthDot.toPx() }

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(cell * rows + gap * (rows - 1))
                    .clearAndSetSemantics { },
        ) {
            days.forEachIndexed { index, status ->
                if (status == DayStatus.PADDING) return@forEachIndexed
                val column = index % DAYS_PER_WEEK
                val row = index / DAYS_PER_WEEK
                drawRoundRect(
                    color = habitColor.monthDayColor(status, tint),
                    topLeft = Offset(column * (cellPx + gapPx), row * (cellPx + gapPx)),
                    size = Size(cellPx, cellPx),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                )
            }
        }
    }
}

/**
 * The design's three fills, in one place.
 *
 * The two dimmed states multiply a *tint* by an *opacity* rather than picking a fourth colour —
 * that is how the prototype expresses it (a hex alpha byte plus a CSS `opacity`), and collapsing
 * the two into a single alpha here would lose the trail back to the source.
 *
 * `FUTURE` and `BEFORE_START` are drawn identically, which is the design's choice: a Month grid
 * says nothing about when a habit began. Only the Year grid tells the two apart.
 */
private fun Color.monthDayColor(
    status: DayStatus,
    incompleteTint: Float,
): Color =
    when (status) {
        DayStatus.COMPLETED -> this
        DayStatus.INCOMPLETE -> copy(alpha = incompleteTint * DayOpacity.MONTH_INCOMPLETE)
        DayStatus.FUTURE, DayStatus.BEFORE_START -> copy(alpha = incompleteTint * DayOpacity.MONTH_INACTIVE)
        DayStatus.PADDING -> Color.Transparent
    }

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 200)
@Composable
private fun MonthDotGridPreview() {
    HabitsTheme {
        MonthDotGrid(
            days = previewMonth(),
            habitColor = HabitsTheme.colors.habit.blue,
            modifier = Modifier.padding(Spacing.cardPaddingCompact),
        )
    }
}

/** August 2026: starts on a Saturday, 31 days, "today" the 21st. */
internal fun previewMonth(): List<DayStatus> =
    List(FIVE_LEADING_BLANKS) { DayStatus.PADDING } +
        List(DAYS_IN_AUGUST) { index ->
            val day = index + 1
            when {
                day > PREVIEW_TODAY -> DayStatus.FUTURE
                day % PREVIEW_MISS_EVERY == 0 -> DayStatus.INCOMPLETE
                else -> DayStatus.COMPLETED
            }
        }

private const val FIVE_LEADING_BLANKS = 5
private const val DAYS_IN_AUGUST = 31
private const val PREVIEW_TODAY = 21
private const val PREVIEW_MISS_EVERY = 3

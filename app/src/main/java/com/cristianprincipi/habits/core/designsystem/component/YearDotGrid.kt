package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.cristianprincipi.habits.core.designsystem.theme.DayOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsCorners
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import kotlin.math.ceil

/**
 * A year of days as a GitHub-style density grid — seven rows, one column per week
 * (`docs/design-spec.md §4`).
 *
 * Three destinations share this: the Year card at [Sizing.yearDotSmall], the Stats "Year overview"
 * card at [Sizing.yearDotLarge], and [Sizing.yearDot] as the default. That is why it lives in the
 * design system rather than in `feature/history/`.
 *
 * A `Canvas` is not an optimisation here, it is the only option — 365 cells emitted as composables
 * would cost 365 layout nodes per habit card.
 *
 * **Scrolled to the end on first composition**, unlike the web version, which starts at the oldest
 * day. On a phone the visible slice is a few weeks wide, and the interesting end of a year of
 * habits is the recent one.
 *
 * @param days one entry per cell, column-major: index 0 is the top-left, index 6 the bottom of the
 *   first column, index 7 the top of the second. [DayStatus.PADDING] fills the leading blanks that
 *   put row one on a Monday and the trailing blanks that complete the final column.
 */
@Composable
fun YearDotGrid(
    days: List<DayStatus>,
    habitColor: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = Sizing.yearDot,
) {
    val scrollState = rememberScrollState()
    val columns = ceil(days.size / DAYS_PER_WEEK.toFloat()).toInt().coerceAtLeast(1)
    val gap = Spacing.yearDotGap
    val tint = HabitsTheme.colors.tint.yearDotIncomplete

    val dotPx = with(LocalDensity.current) { dotSize.toPx() }
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val radiusPx = with(LocalDensity.current) { HabitsCorners.yearDot.toPx() }

    LaunchedEffect(scrollState.maxValue) { scrollState.scrollTo(scrollState.maxValue) }

    Box(modifier = modifier.horizontalScroll(scrollState)) {
        Canvas(
            modifier =
                Modifier
                    .size(
                        width = dotSize * columns + gap * (columns - 1),
                        height = dotSize * DAYS_PER_WEEK + gap * (DAYS_PER_WEEK - 1),
                    ).clearAndSetSemantics { },
        ) {
            days.forEachIndexed { index, status ->
                if (status == DayStatus.PADDING) return@forEachIndexed
                val column = index / DAYS_PER_WEEK
                val row = index % DAYS_PER_WEEK
                drawRoundRect(
                    color = habitColor.yearDayColor(status, tint),
                    topLeft = Offset(column * (dotPx + gapPx), row * (dotPx + gapPx)),
                    size = Size(dotPx, dotPx),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                )
            }
        }
    }
}

/**
 * The Year grid's fills.
 *
 * Unlike the Month grid, this one *does* separate "before the habit started" from an ordinary
 * incomplete day — 6% against 28%. A year is long enough that a habit's start date is a real
 * feature of the picture rather than noise.
 */
private fun Color.yearDayColor(
    status: DayStatus,
    incompleteTint: Float,
): Color =
    when (status) {
        DayStatus.COMPLETED -> this
        DayStatus.INCOMPLETE, DayStatus.FUTURE -> copy(alpha = incompleteTint * DayOpacity.YEAR_INCOMPLETE)
        DayStatus.BEFORE_START -> copy(alpha = incompleteTint * DayOpacity.YEAR_INACTIVE)
        DayStatus.PADDING -> Color.Transparent
    }

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun YearDotGridPreview() {
    HabitsTheme {
        YearDotGrid(
            days = previewYear(),
            habitColor = HabitsTheme.colors.habit.orange,
            modifier = Modifier.padding(Spacing.cardPadding),
            dotSize = Sizing.yearDotSmall,
        )
    }
}

/** A trailing year that starts before the habit did, so all four states appear. */
internal fun previewYear(): List<DayStatus> =
    List(DAYS_IN_YEAR) { index ->
        when {
            index < HABIT_STARTED_ON_DAY -> DayStatus.BEFORE_START
            index % PREVIEW_MISS_EVERY == 0 -> DayStatus.INCOMPLETE
            else -> DayStatus.COMPLETED
        }
    }

private const val DAYS_IN_YEAR = 365
private const val HABIT_STARTED_ON_DAY = 60
private const val PREVIEW_MISS_EVERY = 4

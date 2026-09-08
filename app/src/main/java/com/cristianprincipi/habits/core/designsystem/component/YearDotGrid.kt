package com.cristianprincipi.habits.core.designsystem.component

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.cristianprincipi.habits.core.designsystem.theme.DayOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsCorners
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

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
    val columns = dotGridColumns(days.size)
    val gap = Spacing.yearDotGap
    val tint = HabitsTheme.colors.tint.yearDotIncomplete

    val metrics =
        with(LocalDensity.current) {
            DotGridMetrics(
                dotPx = dotSize.toPx(),
                gapPx = gap.toPx(),
                radiusPx = HabitsCorners.yearDot.toPx(),
            )
        }
    // Remembered rather than allocated per draw: this recomposes on every scroll frame.
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

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
            // The geometry moved to `drawDotGrid` in M15.6 so the home-screen widget draws this
            // exact picture — Glance has no Canvas, so its grid is a Bitmap fed by the same code.
            drawIntoCanvas { canvas ->
                drawDotGrid(
                    canvas = canvas.nativeCanvas,
                    days = days,
                    metrics = metrics,
                    paint = paint,
                    colorOf = { status -> habitColor.yearDayColor(status, tint).toArgb() },
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
internal fun Color.yearDayColor(
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

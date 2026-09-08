package com.cristianprincipi.habits.core.designsystem.component

import kotlin.math.ceil

/**
 * The three lengths that define a dot grid, in pixels, travelling together.
 *
 * ⚠ **One type rather than three parameters, because they are only ever correct as a set.** A dot
 * size from one caller and a gap from another draws a grid that is subtly the wrong width, and the
 * width is what the widget's `Bitmap` is allocated from — so the mistake surfaces as silent
 * clipping rather than as anything that looks wrong on screen.
 *
 * Pixels, not `Dp`, because the widget has no `LocalDensity` to resolve them against: it draws on
 * a background thread into a `Bitmap` whose size it must know before there is a view anywhere.
 */
internal data class DotGridMetrics(
    val dotPx: Float,
    val gapPx: Float,
    val radiusPx: Float,
) {
    /** Gaps sit *between* columns, so there is one fewer of them than there are columns. */
    fun widthPx(columns: Int): Float = dotPx * columns + gapPx * (columns - 1)

    /** Always seven rows, whatever the day count — that is what makes a column read as a week. */
    val heightPx: Float get() = dotPx * DAYS_PER_WEEK + gapPx * (DAYS_PER_WEEK - 1)

    /** Centre-to-centre spacing. The one derived value both the drawing and its tests need. */
    val pitchPx: Float get() = dotPx + gapPx
}

/** Weeks are columns, so a partial final week still occupies a whole one. */
internal fun dotGridColumns(dayCount: Int): Int = ceil(dayCount / DAYS_PER_WEEK.toFloat()).toInt().coerceAtLeast(1)

package com.cristianprincipi.habits.core.designsystem.component

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Draws a GitHub-style dot grid onto a **plain [Canvas]** rather than a Compose one, column-major:
 * index 0 is top-left, index 6 the bottom of the first column, index 7 the top of the second.
 *
 * ⭐⭐ **This exists so the home-screen widget and [YearDotGrid] draw the same picture from the same
 * code (M15.6).** Glance compiles to `RemoteViews` — a fixed set of platform views — so it has no
 * `Canvas` of any kind, and emitting ~200 cells as Glance `Box`es is the approach Compose already
 * rejected at a smaller count, with a Binder transaction limit waiting behind it. The widget's grid
 * is therefore a `Bitmap` shown through an `Image`, and the alternative to sharing this function is
 * drawing one design twice and letting the two drift.
 *
 * ⚠ **Compose is not the odd caller here — it is the one that adapts.** A `DrawScope` hands out the
 * platform canvas underneath it (`drawContext.canvas.nativeCanvas`), so the composable gives up
 * nothing by routing through this; a `Bitmap`, by contrast, cannot be handed a `DrawScope` without
 * building one. That asymmetry is why the shared type is `android.graphics.Canvas`.
 *
 * ⚠ **Colour policy is deliberately NOT here.** It stays on `YearDotGrid.yearDayColor`, in Compose
 * `Color`, and arrives as [colorOf]. A renderer that knew about `DayOpacity` would be a second
 * place the design could be changed, and the widget must reach the same answer as the screen or the
 * two pictures disagree about what an unfinished day looks like.
 *
 * [DayStatus.PADDING] draws **nothing at all** rather than drawing transparent — the caller's
 * background must show through, and on a widget that background is the user's wallpaper.
 *
 * ⭐ **[colorOf] is evaluated once per [DayStatus], not once per cell.** A year is 365 cells and
 * there are five statuses; asking the caller's lambda — which allocates a Compose `Color` and
 * converts it — 365 times to get five answers is work this function is in a position to not do.
 *
 * @param paint reused across every cell and mutated as it goes. Passed in rather than allocated
 *   here because the widget draws this for several habits in a row on a background thread, and a
 *   `Paint` per call is the kind of allocation a `Bitmap` pipeline should not be making.
 */
internal fun drawDotGrid(
    canvas: Canvas,
    days: List<DayStatus>,
    metrics: DotGridMetrics,
    paint: Paint,
    colorOf: (DayStatus) -> Int,
) {
    val colors = IntArray(DayStatus.entries.size) { ordinal -> colorOf(DayStatus.entries[ordinal]) }
    days.forEachIndexed { index, status ->
        if (status == DayStatus.PADDING) return@forEachIndexed
        val left = (index / DAYS_PER_WEEK) * metrics.pitchPx
        val top = (index % DAYS_PER_WEEK) * metrics.pitchPx
        paint.color = colors[status.ordinal]
        canvas.drawRoundRect(
            left,
            top,
            left + metrics.dotPx,
            top + metrics.dotPx,
            metrics.radiusPx,
            metrics.radiusPx,
            paint,
        )
    }
}

/**
 * The same grid, as a [android.graphics.Bitmap] sized to fit it exactly.
 *
 * ⭐ **The widget's only way to have a dot grid at all** — Glance shows this through an `Image`.
 * Sized from [DotGridMetrics] rather than from a caller's guess, because `RemoteViews` clips an
 * oversized bitmap silently and a short one leaves a gap nothing reports.
 *
 * ⚠ **`ARGB_8888` and not `RGB_565`**: [DayStatus.PADDING] must leave the wallpaper showing, which
 * needs a real alpha channel. A year at widget scale is well inside the ~1MB the `RemoteViews`
 * Binder transaction allows, so the cheaper config would buy a transparency bug for nothing.
 */
internal fun dotGridBitmap(
    days: List<DayStatus>,
    metrics: DotGridMetrics,
    colorOf: (DayStatus) -> Int,
): android.graphics.Bitmap {
    val columns = dotGridColumns(days.size)
    val bitmap =
        android.graphics.Bitmap.createBitmap(
            kotlin.math
                .ceil(metrics.widthPx(columns))
                .toInt()
                .coerceAtLeast(1),
            kotlin.math
                .ceil(metrics.heightPx)
                .toInt()
                .coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
    drawDotGrid(
        canvas = Canvas(bitmap),
        days = days,
        metrics = metrics,
        paint = Paint(Paint.ANTI_ALIAS_FLAG),
        colorOf = colorOf,
    )
    return bitmap
}

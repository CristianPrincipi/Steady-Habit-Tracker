package com.cristianprincipi.habits.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/**
 * M15.6a — the dot grid drawn onto a plain `Canvas`, which is the only way the home-screen widget
 * can have one at all.
 *
 * ### Why this is instrumented when [DotGridGeometryTest] is not
 *
 * The arithmetic runs anywhere; `Bitmap`, `Canvas` and `Paint` do not. And the thing worth
 * asserting here is genuinely the **pixels** — this function is about to become the only source of
 * the widget's picture, where there is no Compose tree to query, no semantics, and no way to ask
 * "what did you draw" other than to look.
 *
 * ⚠ **Method names are camelCase here and backticked in the JVM tests, and that is forced rather
 * than sloppy.** D8 rejects spaces in a method name below DEX version 040, which tracks `minSdk` —
 * ours is 26, so a backticked name compiles on the JVM and fails the *build* of the androidTest
 * APK. Every instrumented test in this project follows the same rule.
 *
 * ⚠ **Anti-aliasing is why every assertion samples a dot's centre.** An edge pixel is a blend of
 * the dot and whatever is behind it, so an assertion on one would be testing the rasteriser.
 */
@RunWith(AndroidJUnit4::class)
class DotGridRendererTest {
    @Test
    fun cellsRunColumnMajorSoTheEighthIsTheTopOfTheSecondWeek() {
        // The single most consequential thing this function does. Row-major would produce a grid
        // that looks entirely plausible — same dots, same density — while every column meant a
        // day-of-week instead of a week, which no screenshot review would catch.
        val days = MutableList(DAYS_PER_WEEK * 2) { DayStatus.INCOMPLETE }
        days[DAYS_PER_WEEK] = DayStatus.COMPLETED

        val bitmap = render(days)

        assertEquals("second column, top row", COMPLETED_COLOR, bitmap.centreOf(column = 1, row = 0))
        assertEquals("first column, second row", INCOMPLETE_COLOR, bitmap.centreOf(column = 0, row = 1))
    }

    @Test
    fun paddingDrawsNothingAtAllSoTheWallpaperShowsThrough() {
        // Not "draws transparent" — draws *nothing*. On a widget the background is the user's
        // wallpaper, and a padding cell filled with the theme's background colour would punch a
        // dark rectangle into it. The leading blanks that put row one on a Monday are padding, so
        // this is every widget's top-left corner, not an edge case.
        val days = MutableList(DAYS_PER_WEEK) { DayStatus.COMPLETED }
        days[0] = DayStatus.PADDING

        val bitmap = render(days)

        assertEquals("padding cell", 0, Color.alpha(bitmap.centreOf(column = 0, row = 0)))
        assertEquals("its neighbour", COMPLETED_COLOR, bitmap.centreOf(column = 0, row = 1))
    }

    @Test
    fun theBitmapTheGeometrySizesHoldsTheWholeGridLastDotIncluded() {
        // The clipping failure this guards against is silent: RemoteViews scales or crops an
        // oversized Bitmap without reporting anything, so a one-pixel error would just quietly
        // shave the most recent week — the end of the grid the user actually reads.
        //
        // ⚠ A FULL grid, and the first draft of this test got that wrong in a way worth keeping:
        // 365 is not a multiple of seven, so a trailing year's last column holds ONE day, and an
        // assertion on its bottom row reads empty space. That is what YearDotGrid's contract means
        // by padding "the trailing blanks that complete the final column" — the caller pads, and a
        // test that does not pad is not testing the corner it thinks it is.
        val days = List(FULL_WEEKS * DAYS_PER_WEEK) { DayStatus.COMPLETED }
        val lastColumn = dotGridColumns(days.size) - 1

        val bitmap = render(days)

        assertEquals(FULL_WEEKS - 1, lastColumn)
        assertEquals(COMPLETED_COLOR, bitmap.centreOf(column = lastColumn, row = DAYS_PER_WEEK - 1))
    }

    @Test
    fun anUnpaddedFinalColumnDrawsShortRatherThanDrawingOutsideTheBitmap() {
        // The other half of the finding above. A caller that forgets to pad must get a short last
        // column — not an exception, and not a dot drawn past the edge of the Bitmap it was given.
        val days = List(YEAR) { DayStatus.COMPLETED }
        val lastColumn = dotGridColumns(YEAR) - 1

        val bitmap = render(days)

        assertEquals(COMPLETED_COLOR, bitmap.centreOf(column = lastColumn, row = 0))
        assertEquals(0, Color.alpha(bitmap.centreOf(column = lastColumn, row = DAYS_PER_WEEK - 1)))
    }

    @Test
    fun theColourLambdaIsAskedOncePerStatusNotOncePerCell() {
        // Asserting a documented claim rather than trusting it. The caller's lambda allocates a
        // Compose Color and converts it; at 365 cells and five statuses the difference is 360
        // allocations per habit, on a background thread that draws several in a row.
        val calls = AtomicInteger()
        render(List(YEAR) { DayStatus.COMPLETED }, countingInto = calls)

        assertEquals(DayStatus.entries.size, calls.get())
    }

    // --- harness -----------------------------------------------------------------------------

    private fun render(
        days: List<DayStatus>,
        countingInto: AtomicInteger? = null,
    ): Bitmap {
        val columns = dotGridColumns(days.size)
        val bitmap =
            Bitmap.createBitmap(
                ceil(METRICS.widthPx(columns)).toInt(),
                ceil(METRICS.heightPx).toInt(),
                Bitmap.Config.ARGB_8888,
            )
        drawDotGrid(
            canvas = Canvas(bitmap),
            days = days,
            metrics = METRICS,
            paint = Paint(Paint.ANTI_ALIAS_FLAG),
        ) { status ->
            countingInto?.incrementAndGet()
            if (status == DayStatus.COMPLETED) COMPLETED_COLOR else INCOMPLETE_COLOR
        }
        return bitmap
    }

    private fun Bitmap.centreOf(
        column: Int,
        row: Int,
    ): Int =
        getPixel(
            (column * METRICS.pitchPx + METRICS.dotPx / 2).toInt(),
            (row * METRICS.pitchPx + METRICS.dotPx / 2).toInt(),
        )

    private companion object {
        // Square corners so a centre sample cannot be confused by a rounded one.
        val METRICS = DotGridMetrics(dotPx = 10f, gapPx = 2f, radiusPx = 0f)
        const val YEAR = 365
        const val FULL_WEEKS = 53
        const val COMPLETED_COLOR = Color.RED
        const val INCOMPLETE_COLOR = Color.BLUE
    }
}

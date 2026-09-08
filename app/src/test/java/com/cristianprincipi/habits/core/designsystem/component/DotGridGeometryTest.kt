package com.cristianprincipi.habits.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dot grid's arithmetic, extracted in M15.6 so the home-screen widget and [YearDotGrid] size
 * the same picture the same way.
 *
 * ### Why the geometry is tested apart from the drawing
 *
 * Because the drawing needs a device and this does not, and because the two fail differently. A
 * wrong colour is visible in a screenshot; a wrong **column count** is not — it shows up as a grid
 * a few pixels too wide for its widget, which `RemoteViews` clips while reporting nothing. The
 * `Bitmap` the widget allocates is sized from exactly these functions, so an off-by-one here is an
 * off-by-one in a picture nobody is looking at closely.
 */
class DotGridGeometryTest {
    private val metrics = DotGridMetrics(dotPx = 5f, gapPx = 2f, radiusPx = 1f)

    @Test
    fun `a year is 53 columns, because a partial final week still occupies one`() {
        assertEquals(53, dotGridColumns(365))
        assertEquals(1, dotGridColumns(7))
        assertEquals(2, dotGridColumns(8))
    }

    @Test
    fun `no days is still one column, so the width can never come out negative`() {
        // The coerce is load-bearing rather than defensive: width subtracts one gap per column, so
        // zero columns would return -gap, and a Bitmap cannot be allocated at a negative width.
        assertEquals(1, dotGridColumns(0))
        assertTrue(metrics.widthPx(dotGridColumns(0)) > 0f)
    }

    @Test
    fun `gaps sit between columns, so a single column has none`() {
        assertEquals(5f, metrics.widthPx(columns = 1), TOLERANCE)
        assertEquals(19f, metrics.widthPx(columns = 3), TOLERANCE)
    }

    @Test
    fun `height is seven rows and does not move with the day count`() {
        // The invariant that makes a column read as a week. If height ever varied with the data,
        // the grid would stop being a calendar and start being a bar chart.
        assertEquals(5f * 7 + 2f * 6, metrics.heightPx, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}

package com.cristianprincipi.habits.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M15.6d — the sparkline as actual pixels, which is the only thing the measure widget shows.
 *
 * ### ⭐⭐ Why this exists at all: the widget's drawing had no machine check of any kind
 *
 * ADR-130 §7 is the argument. A missing `remember` key left the habit widget handing back a
 * stale-coloured bitmap, and **every gate in this project passed** — the arithmetic was right, the
 * composable compiled, the JVM suite was green, and the defect was visible only to a human looking
 * at a home screen. A widget has no Compose tree to query, no semantics and no way to be asked what
 * it drew other than to read its bitmap back. [DotGridRendererTest] made that argument for the dot
 * grid; this is its twin for the curve.
 *
 * ⚠ **Instrumented because `Bitmap`, `Canvas`, `Path` and `LinearGradient` are**, exactly as the dot
 * grid's are. The arithmetic that can be *wrong* rather than merely ugly lives in
 * [SparklineGeometryTest] and runs anywhere.
 *
 * ⚠ **Method names are camelCase, not backticked**: D8 rejects spaces in a method name below DEX
 * version 040, which tracks `minSdk 26`, so a backticked name fails the androidTest APK's *build*.
 *
 * ⚠ **Every assertion samples a whole column rather than one pixel.** The curve is anti-aliased and
 * its exact x is a cubic's business, so asking "is anything painted in this column, and how high"
 * is a claim about the drawing; asking about one pixel would be a claim about the rasteriser.
 */
@RunWith(AndroidJUnit4::class)
class SparklineRendererTest {
    private val metrics = SparklineMetrics(widthPx = 400f, heightPx = 100f, strokePx = 5f)
    private val colors =
        SparklineColors(
            line = Color.rgb(LINE_R, LINE_G, LINE_B),
            fillTop = Color.argb(FILL_TOP_ALPHA, LINE_R, LINE_G, LINE_B),
            fillBottom = Color.argb(0, LINE_R, LINE_G, LINE_B),
        )

    @Test
    fun theDrawingLooksLikeThis() {
        // ⚠⚠ TEMPORARY, and it asserts nothing — it prints the curve so a human can see the shape
        // before the widget reaches a phone. Removed with `asText`.
        println("sparkline, rising:\n" + render(rising()).asText())
        println("sparkline, one reading:\n" + render(listOf(Offset(HALFWAY, 84f))).asText())
    }

    @Test
    fun theBitmapIsExactlyTheBoxItWasAskedFor() {
        // RemoteViews clips an oversized bitmap silently and leaves a gap for a short one, and
        // neither reports anything — so the size is worth asserting rather than eyeballing.
        val bitmap = render(rising())

        assertEquals(400, bitmap.width)
        assertEquals(100, bitmap.height)
    }

    @Test
    fun aRisingSeriesDrawsACurveThatActuallyRises() {
        // The whole subject of the widget. A y axis inverted by one sign error draws a perfectly
        // plausible line that says the opposite of the data, and nothing but a pixel would notice.
        val bitmap = render(rising())

        val left = requireNotNull(bitmap.topPaintedRow(x = 20))
        val right = requireNotNull(bitmap.topPaintedRow(x = 380))

        assertTrue("left=$left right=$right; the newest reading should sit higher", right < left)
    }

    @Test
    fun theHighestReadingReachesTheTopOfTheBandAndTheLowestTheFoot() {
        // Scaled to the data's own range, so the extremes touch the inset band — this is what makes
        // half a kilo of movement legible at all (SparklineGeometryTest covers the arithmetic).
        val bitmap = render(rising())

        assertTrue("the newest reading should reach the top inset", bitmap.topPaintedRow(x = 380)!! <= TOP_BAND)
        assertTrue("the oldest should sit near the foot", bitmap.topPaintedRow(x = 20)!! >= FOOT_BAND)
    }

    @Test
    fun nothingIsPaintedAboveTheCurve() {
        // The fill closes DOWNWARD to the foot of the box. A path closed the other way would flood
        // the empty half of the card, which on a widget is the user's wallpaper.
        val bitmap = render(rising())

        assertEquals(Color.TRANSPARENT, bitmap.getPixel(20, 2))
    }

    @Test
    fun theFillFadesOutTowardsTheFootOfTheBox() {
        // The gradient runs from the curve's own peak, not from the top of the box — so a line that
        // stays low is shaded as fully as one that climbs.
        val bitmap = render(rising())

        val underCurve = Color.alpha(bitmap.getPixel(380, TOP_BAND + FADE_PROBE))
        val nearFoot = Color.alpha(bitmap.getPixel(380, 100 - FADE_PROBE))

        assertTrue("under=$underCurve foot=$nearFoot", underCurve > nearFoot)
    }

    @Test
    fun oneReadingDrawsADotRatherThanNothing() {
        // A single reading has no segment for a curve, and drawing nothing would make a widget that
        // has data look exactly like one that has none.
        val bitmap = render(listOf(Offset(HALFWAY, 84f)))

        assertNotNull("a lone reading should still be drawn", bitmap.topPaintedRow(x = 200))
        assertNull("and only where it is", bitmap.topPaintedRow(x = 20))
    }

    @Test
    fun noReadingsDrawNothingAtAll() {
        val bitmap = render(emptyList())

        assertNull(bitmap.topPaintedRow(x = 200))
        assertNull(bitmap.topPaintedRow(x = 20))
    }

    /** A month of weigh-ins that climb — four points spread across the window, not four days. */
    private fun rising(): List<Offset> =
        listOf(
            Offset(0f, 83.0f),
            Offset(0.33f, 83.4f),
            Offset(0.66f, 84.1f),
            Offset(1f, 84.6f),
        )

    private fun render(points: List<Offset>): Bitmap =
        sparklineBitmap(points = points, metrics = metrics, colors = colors)

    /**
     * ⚠⚠ **TEMPORARY — M15.6d.** The drawing as text, so a human can look at a shape nobody has
     * seen yet. It comes out once the user has judged the real widget on their phone, exactly as
     * `WidgetProbe` did at M15.6b.
     *
     * ⚠ **Printed rather than written to a file**, and that is not a preference: AGP uninstalls the
     * app at the end of an instrumented run (`CLAUDE.md` §7), which takes its external cache and any
     * PNG in it. Standard output survives into the test report.
     */
    private fun Bitmap.asText(): String =
        (0 until ART_ROWS).joinToString("\n") { row ->
            (0 until ART_COLUMNS).joinToString("") { column ->
                val alpha =
                    Color.alpha(
                        getPixel(
                            (column * (width - 1)) / (ART_COLUMNS - 1),
                            (row * (height - 1)) / (ART_ROWS - 1),
                        ),
                    )
                when {
                    alpha > INK_SOLID -> "#"
                    alpha > INK_FAINT -> "+"
                    alpha > 0 -> "."
                    else -> " "
                }
            }
        }

    /** The highest painted row in a column, or null when the column is untouched. */
    private fun Bitmap.topPaintedRow(x: Int): Int? = (0 until height).firstOrNull { Color.alpha(getPixel(x, it)) > 0 }

    private companion object {
        const val LINE_R = 34
        const val LINE_G = 197
        const val LINE_B = 94
        const val FILL_TOP_ALPHA = 102

        /** Half a stroke plus a pixel of anti-aliasing at each end of the band. */
        const val TOP_BAND = 6
        const val FOOT_BAND = 90

        /** Far enough below the curve to be fill rather than line. */
        const val FADE_PROBE = 12
        const val HALFWAY = 0.5f

        // TEMPORARY, with `asText`.
        const val ART_ROWS = 14
        const val ART_COLUMNS = 72
        const val INK_SOLID = 170
        const val INK_FAINT = 60
    }
}

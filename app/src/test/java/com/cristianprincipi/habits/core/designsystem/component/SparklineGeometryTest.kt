package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a sparkline's points land — M15.6d.
 *
 * ⚠ **Only the arithmetic, never the drawing.** `drawSparkline` needs a real `android.graphics`
 * canvas, so it belongs on a device; `sparklinePoints` is the half that can be *wrong* rather than
 * merely ugly, which is `AxisScale`'s split and for its reason.
 */
class SparklineGeometryTest {
    private val metrics = SparklineMetrics(widthPx = 100f, heightPx = 50f, strokePx = 2f)

    @Test
    fun theBandIsInsetByHalfAStrokeAtEachEnd() {
        // Without the inset a reading at the maximum is drawn with half its line off the bitmap and
        // silently shaved — a defect that looks like a design choice.
        assertEquals(1f, metrics.plotTopPx, 0.0001f)
        assertEquals(49f, metrics.plotBottomPx, 0.0001f)
    }

    @Test
    fun xIsTakenAsGivenSoTheGapsStayHonest() {
        // The caller has already placed each reading in time. Re-spacing here is exactly the lie
        // this design is shaped against: four weigh-ins drawn as a month of steady change.
        val placed = sparklinePoints(listOf(point(0f, 84f), point(0.25f, 85f), point(1f, 86f)), metrics)

        assertEquals(listOf(0f, 25f, 100f), placed.map { it.x })
    }

    @Test
    fun theHighestValueTakesTheTopOfTheBandAndTheLowestTheFoot() {
        val placed = sparklinePoints(listOf(point(0f, 80f), point(0.5f, 90f), point(1f, 85f)), metrics)

        assertEquals(49f, placed[0].y, 0.0001f)
        assertEquals(1f, placed[1].y, 0.0001f)
        // Halfway between the two, because y is a straight scale of the value and not of its rank.
        assertEquals(25f, placed[2].y, 0.0001f)
    }

    @Test
    fun theScaleIsTheDataSOwnRangeAndNotZeroBased() {
        // A body weight moving half a kilo is the case this exists for: against a zero baseline it
        // is a dead-flat line, which is true of the numbers and useless as a picture.
        val placed = sparklinePoints(listOf(point(0f, 84.1f), point(1f, 84.6f)), metrics)

        assertEquals(49f, placed.first().y, 0.0001f)
        assertEquals(1f, placed.last().y, 0.0001f)
    }

    @Test
    fun aFlatSeriesDrawsDownTheMiddle() {
        // min == max leaves nothing to divide by, and the middle is also the honest answer: a
        // number that has not moved has no trend, and the top or the foot would invent one.
        val placed = sparklinePoints(listOf(point(0f, 84f), point(0.5f, 84f), point(1f, 84f)), metrics)

        assertTrue(placed.all { it.y == 25f })
    }

    @Test
    fun oneReadingIsPlacedRatherThanDropped() {
        // It draws as a dot. A widget with a single reading must not look like one with none.
        val placed = sparklinePoints(listOf(point(0.4f, 84f)), metrics)

        assertEquals(1, placed.size)
        assertEquals(40f, placed.single().x, 0.0001f)
        assertEquals(25f, placed.single().y, 0.0001f)
    }

    @Test
    fun noReadingsPlaceNothing() {
        assertTrue(sparklinePoints(emptyList(), metrics).isEmpty())
    }

    @Test
    fun aBoxWithNoRoomInItIsRefusedRatherThanDrawnWrong() {
        // A widget can be resized until the header leaves nothing underneath. The invariant is what
        // turns that into a caught mistake instead of a bitmap with a negative dimension.
        assertThrows(IllegalArgumentException::class.java) {
            SparklineMetrics(widthPx = 100f, heightPx = 0f, strokePx = 2f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SparklineMetrics(widthPx = 100f, heightPx = 50f, strokePx = 0f)
        }
    }

    private fun point(
        x: Float,
        value: Float,
    ) = Offset(x, value)
}

package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.designsystem.component.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.core.designsystem.component.MINIMUM_CHART_POINTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invented history the widget picker draws over — M15.9.
 *
 * ⚠ **What is worth testing here is not "does it look nice".** It is the three properties a sample
 * can quietly lose and still render: that it is the **shape the drawing expects** (a whole number of
 * weeks, enough points for a curve), that it is **stable** across launches, and that it never
 * degenerates into a picture that says nothing — an empty grid, a flat line.
 */
class WidgetPreviewSamplesTest {
    @Test
    fun theSampleYearIsAWholeNumberOfWeeks() {
        // GridFit.lastWeeks takes a multiple of seven off the END. A list that was not one would
        // shift every day into the wrong row — and the result would still look like a perfectly
        // plausible dot grid, which is exactly why this is asserted rather than eyeballed.
        val year = sampleYear(futureDays = 0)

        assertEquals(0, year.size % DAYS_PER_WEEK)
        assertEquals(WEEKS_IN_YEAR * DAYS_PER_WEEK, year.size)
    }

    @Test
    fun theRestOfThisWeekIsStillToCome() {
        val futureDays = 3
        val year = sampleYear(futureDays)

        assertEquals(
            "The trailing year ends on the CURRENT week, so the last entries are the days after today",
            List(futureDays) { DayStatus.FUTURE },
            year.takeLast(futureDays),
        )
        assertNotEquals(
            "Today itself has happened, so it must not be drawn as future",
            DayStatus.FUTURE,
            year[year.size - futureDays - 1],
        )
    }

    @Test
    fun theSameSampleIsDrawnEveryLaunch() {
        // A preview reshuffled on every cold start reads as data changing rather than as a sample.
        assertEquals(sampleYear(futureDays = 2), sampleYear(futureDays = 2))
    }

    @Test
    fun whatDayItIsDoesNotDisturbTheHistoryBehindIt() {
        // The future branch is taken only for trailing indices, after every random draw the earlier
        // ones make — so opening the app on a Tuesday and on a Friday shows the same year.
        val tuesday = sampleYear(futureDays = 5)
        val friday = sampleYear(futureDays = 2)
        val shared = tuesday.size - 5

        assertEquals(tuesday.take(shared), friday.take(shared))
    }

    @Test
    fun theSampleYearReadsAsAHabitRatherThanAWallOrAVoid() {
        val year = sampleYear(futureDays = 0)
        val kept = year.count { it == DayStatus.COMPLETED }.toFloat() / year.size

        assertTrue("A sample year that is nearly empty shows nothing; was $kept", kept > LEAN)
        assertTrue("A solid wall of dots has no habit in it; was $kept", kept < GENEROUS)
        assertEquals(
            "The sample is a running habit, so there are no padding or pre-start cells in it",
            emptySet<DayStatus>(),
            year.toSet() - setOf(DayStatus.COMPLETED, DayStatus.INCOMPLETE),
        )
    }

    @Test
    fun theSampleSeriesHasEnoughPointsToBeACurve() {
        // Below MINIMUM_CHART_POINTS drawSparkline draws a single dot instead of a line, which in a
        // picker would read as a broken widget rather than as a trend.
        assertTrue(samplePoints(anchor = 84.0).size > MINIMUM_CHART_POINTS)
    }

    @Test
    fun theSampleSeriesRunsTheWholeWindowInOrder() {
        val points = samplePoints(anchor = 84.0)

        assertEquals(0f, points.first().x, TOLERANCE)
        assertEquals(1f, points.last().x, TOLERANCE)
        assertEquals(points.map { it.x }.sorted(), points.map { it.x })
    }

    @Test
    fun theSampleSeriesActuallyMoves() {
        // sparklinePoints draws a flat series down the middle of the box. True for a weight that has
        // not changed; useless as the one picture of what this widget is for.
        val y = samplePoints(anchor = 84.0).map { it.y }

        assertNotEquals(y.min(), y.max())
    }

    @Test
    fun theSampleSeriesIsATrendRatherThanAComb() {
        // ⚠⚠ A regression caught on the phone, not by a gate. The first deviation table alternated
        // up and down at EVERY step, which drew fourteen even teeth across 108dp — a sawtooth is a
        // hairball with better manners, and says just as little. This is the property that was
        // missing: a trend changes direction occasionally, a comb changes it constantly.
        val y = samplePoints(anchor = 84.0).map { it.y }
        val steps = y.zipWithNext { from, to -> to - from }
        val reversals = steps.zipWithNext { before, after -> (before > 0) != (after > 0) }.count { it }

        assertTrue(
            "The sample changes direction at $reversals of ${steps.size - 1} turns, which draws a " +
                "comb rather than a trend",
            reversals * 2 < steps.size,
        )
    }

    @Test
    fun theSampleSeriesStaysPlausibleForWhateverIsBeingMeasured() {
        // The badge prints the mean of these, so a sample that wandered far from the anchor would
        // print a figure that is not a weight, or not a night's sleep. Both ends are checked,
        // because the anchor's magnitude is the only thing making the number believable at all.
        for (anchor in listOf(SLEEP_HOURS, BODY_WEIGHT_KG, STEP_COUNT)) {
            val y = samplePoints(anchor).map { it.y }

            assertTrue("$anchor drifted low: ${y.min()}", y.min() > anchor * (1 - DRIFT))
            assertTrue("$anchor drifted high: ${y.max()}", y.max() < anchor * (1 + DRIFT))
        }
    }

    private companion object {
        const val TOLERANCE = 0.0001f
        const val LEAN = 0.4f
        const val GENEROUS = 0.85f

        /** Three magnitudes a real Notion number column plausibly holds. */
        const val SLEEP_HOURS = 7.0
        const val BODY_WEIGHT_KG = 84.0
        const val STEP_COUNT = 9000.0

        /** How far from the anchor the wobble may take a reading before the figure stops convincing. */
        const val DRIFT = 0.05
    }
}

package com.cristianprincipi.habits.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * The window a sparkline is placed against — M15.6d.
 *
 * ⭐ **`positionOf` is the whole reason the window travels with the readings.** A drawing that
 * normalised against its own idea of "thirty days ago" could disagree with the query by a day at
 * midnight, and nothing would ever show it.
 */
class MeasureSeriesTest {
    private val to = LocalDate.of(2026, 9, 9)
    private val from = to.minusDays(29)

    private val weight =
        Measure(
            id = MeasureId("w"),
            name = "Weight",
            emoji = "⚖️",
            unit = "kg",
            color = HabitColor.GREEN,
        )

    private fun series(readings: List<MeasureReading>) =
        MeasureSeries(measure = weight, from = from, to = to, readings = readings)

    private fun reading(
        date: LocalDate,
        value: Double = 84.0,
    ) = MeasureReading(weight.id, date, value)

    @Test
    fun theWindowRunsFromZeroToOne() {
        val subject = series(emptyList())

        assertEquals(0f, subject.positionOf(from), 0.0001f)
        assertEquals(1f, subject.positionOf(to), 0.0001f)
        assertEquals(14f / 29f, subject.positionOf(from.plusDays(14)), 0.0001f)
    }

    @Test
    fun aReadingSitsWhereItsDayFallsAndNotWhereItsTurnDoes() {
        // Two readings a day apart at the far end of the window belong at the far end of the
        // window. Placing them by index would spread them across the whole box.
        val subject = series(listOf(reading(to.minusDays(1)), reading(to)))

        assertEquals(28f / 29f, subject.positionOf(to.minusDays(1)), 0.0001f)
        assertEquals(1f, subject.positionOf(to), 0.0001f)
    }

    @Test
    fun aOneDayWindowHasNowhereToSpreadAndDoesNotDivideByZero() {
        val single = MeasureSeries(measure = weight, from = to, to = to, readings = listOf(reading(to)))

        assertEquals(0f, single.positionOf(to), 0.0001f)
    }

    @Test
    fun theAverageIsPairedWithTheReadings() {
        assertNull(series(emptyList()).average.value)
        assertEquals(
            85.0,
            requireNotNull(series(listOf(reading(from, 84.0), reading(to, 86.0))).average.value),
            0.0001,
        )
    }

    @Test
    fun aSeriesRefusesReadingsThatWouldDrawAPlausibleLie() {
        // Each of these produces a picture rather than a failure if it is let through: out of order
        // draws a curve that doubles back, outside the window draws off the end of the box, and
        // another measure's readings draw someone else's data under this one's name.
        assertThrows(IllegalArgumentException::class.java) {
            series(listOf(reading(to), reading(from)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            series(listOf(reading(from.minusDays(1))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            series(listOf(MeasureReading(MeasureId("other"), to, 84.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeasureSeries(measure = weight, from = to, to = from, readings = emptyList())
        }
    }

    @Test
    fun twoReadingsOnOneDayAreRefused() {
        // The database cannot produce this — (measureId, epochDay) is the primary key — but a
        // caller assembling a series by hand can, and evenly spacing a duplicate is a curve with a
        // vertical segment in it, which `monotoneCurve` divides by zero on.
        assertThrows(IllegalArgumentException::class.java) {
            series(listOf(reading(to, 84.0), reading(to, 85.0)))
        }
    }
}

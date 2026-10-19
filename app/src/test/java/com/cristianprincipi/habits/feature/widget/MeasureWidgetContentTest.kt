package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import com.cristianprincipi.habits.domain.model.MeasureSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * What the number widget's corner square is drawn from — M16.3: filled once today has a value, an
 * outline with a "+" while it has none.
 */
class MeasureWidgetContentTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val weight = Measure(MeasureId("w"), name = "Weight", emoji = "", unit = "kg", color = HabitColor.GREEN)

    private fun series(vararg readings: Pair<LocalDate, Double>) =
        MeasureSeries(
            measure = weight,
            from = today.minusDays(29),
            to = today,
            readings = readings.map { (date, value) -> MeasureReading(weight.id, date, value) },
        )

    @Test
    fun todaysReadingFillsTheSquare() {
        val content = series(today.minusDays(1) to 83.1, today to 82.4).toContent()

        assertEquals(82.4, content.today!!, 0.0)
        assertEquals("the dialog writes to the widget's own number", weight.id, content.id)
    }

    @Test
    fun yesterdaysReadingIsNotToday() {
        // The line still ends on 83.1; the square must not read that as today being logged.
        assertNull(series(today.minusDays(1) to 83.1).toContent().today)
    }

    @Test
    fun anEmptyWindowLeavesTheSquareEmpty() {
        assertNull(series().toContent().today)
    }
}

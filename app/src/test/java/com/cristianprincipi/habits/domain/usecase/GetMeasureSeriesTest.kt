package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeMeasureRepository
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The readings a sparkline is drawn from — M15.6d.
 *
 * ⭐ **The two that carry design decisions are [readingsComeBackInDateOrder] and
 * [aGapInTheReadingsStaysAGap].** The rest is arithmetic that fails loudly; those two fail by
 * producing a plausible-looking picture, which is the only way this can mislead anyone.
 */
class GetMeasureSeriesTest {
    private val today = LocalDate.of(2026, 9, 9)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-09T09:00:00Z"), ZoneOffset.UTC)

    private val weight =
        Measure(
            id = MeasureId("w"),
            name = "Weight",
            emoji = "⚖️",
            unit = "kg",
            color = HabitColor.GREEN,
        )

    private fun series(
        measures: List<Measure> = listOf(weight),
        readings: List<MeasureReading> = emptyList(),
        repository: FakeMeasureRepository = FakeMeasureRepository(measures, readings),
    ) = GetMeasureSeries(repository, clock)

    private fun reading(
        daysAgo: Long,
        value: Double,
    ) = MeasureReading(weight.id, today.minusDays(daysAgo), value)

    @Test
    fun theWindowIsTheSameThirtyDaysStatsAverages() =
        runTest {
            val repository = FakeMeasureRepository(listOf(weight))
            series(repository = repository)().first()

            // ⚠ The whole point of sharing the constant: a line covering a different span from the
            // number printed beside it would be two claims about different data, inches apart.
            assertEquals(
                listOf(today.minusDays(GetMeasureAverages.MEASURE_WINDOW_DAYS - 1L) to today),
                repository.requestedRanges,
            )
        }

    @Test
    fun readingsComeBackInDateOrder() =
        runTest {
            // The DAO's range query has no ORDER BY, so an unsorted answer is what the real one can
            // hand over. MeasureSeries requires increasing dates, so this would throw, not mis-draw
            // — but the throw would happen inside a widget's flow, which is a worse place to find it.
            val jumbled = listOf(reading(daysAgo = 2, value = 84.0), reading(daysAgo = 9, value = 85.0))
            val result = series(readings = jumbled)().first().single()

            assertEquals(
                listOf(today.minusDays(9), today.minusDays(2)),
                result.readings.map { it.date },
            )
        }

    @Test
    fun aGapInTheReadingsStaysAGap() =
        runTest {
            // A weekly weigh-in: four readings across the window, not four consecutive days.
            val weekly = (0..3).map { week -> reading(daysAgo = week * 7L, value = 84.0 - week) }
            val result = series(readings = weekly)().first().single()

            assertEquals(4, result.readings.size)
            // The oldest sits three weeks back through a thirty-day window, not at its start.
            assertEquals(0.27f, result.positionOf(today.minusDays(21)), 0.01f)
            assertEquals(1f, result.positionOf(today), 0.001f)
        }

    @Test
    fun anEmptyWindowHasNoAverageRatherThanZero() =
        runTest {
            val result = series()().first().single()

            assertTrue(result.readings.isEmpty())
            assertNull(result.average.value)
            assertEquals(0, result.average.readings)
        }

    @Test
    fun theAverageIsOverTheDaysThatHadAReadingNotOverTheWindow() =
        runTest {
            val two = listOf(reading(daysAgo = 1, value = 84.0), reading(daysAgo = 5, value = 86.0))
            val result = series(readings = two)().first().single()

            // Not 170 / 30. Dividing by the window would be the zero mistake wearing a denominator.
            assertEquals(85.0, requireNotNull(result.average.value), 0.0001)
            assertEquals(2, result.average.readings)
        }

    @Test
    fun readingsOlderThanTheWindowAreNotIncluded() =
        runTest {
            val old = reading(daysAgo = GetMeasureAverages.MEASURE_WINDOW_DAYS.toLong(), value = 90.0)
            val inside = reading(daysAgo = 1, value = 84.0)
            val result = series(readings = listOf(old, inside))().first().single()

            assertEquals(listOf(84.0), result.readings.map { it.value })
        }

    @Test
    fun everyMeasureGetsASeriesIncludingOneWithNothingInTheWindow() =
        runTest {
            val sleep =
                Measure(
                    id = MeasureId("s"),
                    name = "Sleep",
                    emoji = "😴",
                    unit = "h",
                    color = HabitColor.PURPLE,
                )
            val result =
                series(
                    measures = listOf(weight, sleep),
                    readings = listOf(reading(daysAgo = 1, value = 84.0)),
                )().first()

            // A widget pointed at Sleep must get "no readings", not "this measure is gone" — the
            // two are different states and only one of them tells the user to remove the widget.
            assertEquals(listOf(weight.id, sleep.id), result.map { it.measure.id })
            assertNull(result.last().average.value)
        }
}

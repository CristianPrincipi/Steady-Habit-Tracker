package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeMeasureRepository
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureAverage
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The "Daily averages" figures — M15.4.
 *
 * ⭐ **The two that carry the design decisions are [anEmptyWindowHasNoAverageRatherThanZero] and
 * [theWindowIsThirtyDaysEndingToday].** Everything else here is arithmetic that would fail loudly;
 * those two fail by producing a plausible number, which is the only way this feature can hurt
 * anyone.
 */
class GetMeasureAveragesTest {
    private val today = LocalDate.of(2026, 9, 8)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-08T09:00:00Z"), ZoneOffset.UTC)

    private val weight =
        Measure(
            id = MeasureId("w"),
            name = "Weight",
            emoji = "⚖️",
            unit = "kg",
            color = HabitColor.GREEN,
        )
    private val sleep =
        Measure(
            id = MeasureId("s"),
            name = "Sleep",
            emoji = "😴",
            unit = "h",
            color = HabitColor.PURPLE,
        )

    private fun averages(
        measures: List<Measure> = listOf(weight),
        readings: List<MeasureReading> = emptyList(),
    ) = GetMeasureAverages(FakeMeasureRepository(measures, readings), clock)

    private fun reading(
        measure: Measure,
        daysAgo: Long,
        value: Double,
    ) = MeasureReading(measure.id, today.minusDays(daysAgo), value)

    @Test
    fun theMeanIsOverTheDaysThatHaveAReading() =
        runTest {
            // ⚠ Not over the window. Three weigh-ins in thirty days average those three; dividing by
            // thirty would make a user who weighs weekly look like they had lost four fifths of
            // their body mass.
            val result =
                averages(
                    readings =
                        listOf(
                            reading(weight, 0, 84.0),
                            reading(weight, 1, 85.0),
                            reading(weight, 2, 86.0),
                        ),
                )().first()

            assertEquals(85.0, result.single().value!!, 0.0001)
            assertEquals(3, result.single().readings)
        }

    @Test
    fun anEmptyWindowHasNoAverageRatherThanZero() =
        runTest {
            // ⚠⚠ **The failure the whole feature is shaped against, at its last layer.** `0.0` would
            // draw "0.0 kg" — confident, specific and wrong — where the honest answer is that the
            // user has not weighed themselves this month. Note also that `List<Double>.average()`
            // returns NaN here, not zero, and "NaN kg" is no better.
            val result = averages(readings = emptyList())().first()

            assertNull(result.single().value)
            assertEquals(0, result.single().readings)
        }

    @Test
    fun theWindowIsThirtyDaysEndingToday() =
        runTest {
            // ⭐ The user's answer, over the trailing year and over all time. Inclusive of today, so
            // the oldest day it reaches is 29 days back — a reading on day 30 is outside it.
            val repository =
                FakeMeasureRepository(
                    listOf(weight),
                    listOf(reading(weight, 29, 90.0), reading(weight, 30, 10.0)),
                )

            val result = GetMeasureAverages(repository, clock)().first()

            assertEquals(90.0, result.single().value!!, 0.0001)
            assertEquals(1, result.single().readings)
            assertEquals(today.minusDays(29) to today, repository.requestedRanges.single())
        }

    @Test
    fun theWindowConstantIsWhatTheLabelWillSay() =
        runTest {
            // The screen prints this same constant, so a label reading "30 days" beside a use case
            // computing 60 is not expressible. Pinned because it is the one number in this feature
            // that appears in two places.
            assertEquals(30, GetMeasureAverages.MEASURE_WINDOW_DAYS)
        }

    @Test
    fun everyMeasureGetsARowEvenWithNothingToAverage() =
        runTest {
            // ⚠ A measure with no readings must still appear, with a dash. Dropping it would make a
            // mapped column vanish from the screen that exists to show it, which reads as the
            // mapping having failed.
            val result =
                averages(
                    measures = listOf(weight, sleep),
                    readings = listOf(reading(weight, 1, 84.0)),
                )().first()

            assertEquals(listOf("Weight", "Sleep"), result.map { it.measure.name })
            assertEquals(84.0, result[0].value!!, 0.0001)
            assertNull(result[1].value)
        }

    @Test
    fun measuresKeepTheRepositorysOrder() =
        runTest {
            // Which is the user's order, assigned from the mapping at import.
            val result = averages(measures = listOf(sleep, weight))().first()

            assertEquals(listOf(MeasureId("s"), MeasureId("w")), result.map { it.measure.id })
        }

    @Test
    fun readingsAreGroupedByTheirOwnMeasure() =
        runTest {
            // A grouping bug here would average one column into another and produce a number that
            // is wrong without being absurd — 45.9 for a weight, say, out of weights and hours.
            val result =
                averages(
                    measures = listOf(weight, sleep),
                    readings =
                        listOf(
                            reading(weight, 0, 84.0),
                            reading(weight, 1, 86.0),
                            reading(sleep, 0, 7.0),
                            reading(sleep, 1, 8.0),
                        ),
                )().first()

            assertEquals(85.0, result[0].value!!, 0.0001)
            assertEquals(7.5, result[1].value!!, 0.0001)
        }

    @Test
    fun noMeasuresMeansNoRows() =
        runTest {
            // The section is omitted on screen rather than drawn empty, and this is what makes that
            // decidable: a user who mapped no number columns gets an empty list, not a placeholder.
            assertEquals(emptyList<Any>(), averages(measures = emptyList())().first())
        }

    @Test
    fun anAverageCannotExistWithoutSomethingToAverage() {
        // The pairing is an invariant rather than a convention, so no future caller can construct
        // the state the screen has no way to draw: a value with no readings, or readings with no
        // value.
        assertThrows(IllegalArgumentException::class.java) {
            MeasureAverage(weight, value = 84.0, readings = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeasureAverage(weight, value = null, readings = 3)
        }
    }
}

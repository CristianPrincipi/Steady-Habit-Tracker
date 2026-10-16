package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeMeasureRepository
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Week screen's number rows — M16.1.
 *
 * ⭐ **[aDayWithoutAReadingIsNullNotZero] is the one that carries the user's ruling.** The rest is
 * calendar arithmetic that fails loudly; a zero fails quietly, as a cliff in a weight row.
 */
class GetMeasuresForWeekTest {
    /** Wednesday; its week runs Mon 12 – Sun 18 October 2026. */
    private val today = LocalDate.of(2026, 10, 14)
    private val monday = LocalDate.of(2026, 10, 12)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-14T09:00:00Z"), ZoneOffset.UTC)

    private val weight = Measure(MeasureId("w"), name = "Weight", emoji = "", unit = "kg", color = HabitColor.GREEN)
    private val sleep = Measure(MeasureId("s"), name = "Sleep", emoji = "", unit = "h", color = HabitColor.PURPLE)

    private fun weekOf(
        date: LocalDate,
        repository: FakeMeasureRepository,
    ) = GetMeasuresForWeek(repository, clock)(date)

    @Test
    fun anyDayOfTheWeekAsksForMondayToSunday() =
        runTest {
            val repository = FakeMeasureRepository(listOf(weight))

            val row = weekOf(LocalDate.of(2026, 10, 17), repository).first().single()

            assertEquals(listOf(monday to monday.plusDays(6)), repository.requestedRanges)
            assertEquals((0L..6L).map { monday.plusDays(it) }, row.days.map { it.date })
        }

    @Test
    fun aReadingLandsOnItsOwnDay() =
        runTest {
            val repository = FakeMeasureRepository(listOf(weight), listOf(MeasureReading(weight.id, monday, 82.9)))

            val days = weekOf(today, repository).first().single().days

            assertEquals(82.9, requireNotNull(days[0].value), 0.0)
            assertEquals(listOf(null, null, null, null, null, null), days.drop(1).map { it.value })
        }

    @Test
    fun aDayWithoutAReadingIsNullNotZero() =
        runTest {
            val repository = FakeMeasureRepository(listOf(weight))

            val row = weekOf(today, repository).first().single()

            // ⚠⚠ The UI draws a dash from this null (the user's ruling, 2026-10-16). A 0.0 here would
            // draw "0" in a weight row and drag the week's average down with it.
            row.days.forEach { day -> assertNull(day.value) }
            assertNull(row.average.value)
            assertEquals(0, row.average.readings)
        }

    @Test
    fun theDaysAfterTodayAreFutureAndTodayIsNot() =
        runTest {
            val row = weekOf(today, FakeMeasureRepository(listOf(weight))).first().single()

            assertEquals(listOf(false, false, false, true, true, true, true), row.days.map { it.isFuture })
            assertEquals(2, row.todayIndex)
        }

    @Test
    fun anotherWeekHasNoToday() =
        runTest {
            val row = weekOf(monday.minusDays(7), FakeMeasureRepository(listOf(weight))).first().single()

            assertEquals(HabitPeriod.TODAY_ABSENT, row.todayIndex)
            assertFalse(row.days.any { it.isFuture })
        }

    @Test
    fun theAverageIsOverTheDaysThatHadOne() =
        runTest {
            val readings =
                listOf(MeasureReading(sleep.id, monday, 7.0), MeasureReading(sleep.id, monday.plusDays(1), 8.0))

            val row = weekOf(today, FakeMeasureRepository(listOf(sleep), readings)).first().single()

            // Not 15 / 7: dividing by the week would be the zero mistake wearing a denominator.
            assertEquals(7.5, requireNotNull(row.average.value), 0.0)
            assertEquals(2, row.average.readings)
        }

    @Test
    fun eachMeasureGetsOnlyItsOwnReadingsInTheUsersOrder() =
        runTest {
            val readings =
                listOf(MeasureReading(weight.id, monday, 82.9), MeasureReading(sleep.id, monday, 7.5))

            val rows = weekOf(today, FakeMeasureRepository(listOf(sleep, weight), readings)).first()

            assertEquals(listOf(sleep, weight), rows.map { it.measure })
            assertEquals(7.5, requireNotNull(rows[0].days[0].value), 0.0)
            assertEquals(82.9, requireNotNull(rows[1].days[0].value), 0.0)
        }

    @Test
    fun aReadingOnAFutureDayIsStillDrawn() =
        runTest {
            // A clock or a sync disagreeing with us is the only way to get one, and a value that
            // exists is a fact — the same precedence a completion has over DayState.FUTURE.
            val saturday = monday.plusDays(5)
            val repository = FakeMeasureRepository(listOf(weight), listOf(MeasureReading(weight.id, saturday, 82.0)))

            val day = weekOf(today, repository).first().single().days[5]

            assertTrue(day.isFuture)
            assertEquals(82.0, requireNotNull(day.value), 0.0)
        }
}

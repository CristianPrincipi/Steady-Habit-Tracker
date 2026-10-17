package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.testing.FakeMeasureRepository
import com.cristianprincipi.habits.domain.model.MeasureId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** [SetMeasureReading] — `ToggleCompletion`'s rule, for a number: no writing into tomorrow. */
class SetMeasureReadingTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val weight = MeasureId("weight")
    private val measures = FakeMeasureRepository()
    private val setReading = SetMeasureReading(measures, clock)

    @Test
    fun todayAndThePastAreWritten() =
        runTest {
            assertTrue(setReading(weight, today, 82.9))
            assertTrue(setReading(weight, today.minusDays(30), 83.4))

            assertEquals(
                listOf(Triple(weight, today, 82.9), Triple(weight, today.minusDays(30), 83.4)),
                measures.writtenReadings,
            )
        }

    @Test
    fun aClearIsPassedOnAsNull() =
        runTest {
            setReading(weight, today, null)

            assertEquals(listOf(Triple(weight, today, null)), measures.writtenReadings)
        }

    @Test
    fun aDayThatHasNotHappenedIsRefusedAndNothingIsWritten() =
        runTest {
            assertFalse(setReading(weight, today.plusDays(1), 82.9))
            assertTrue(measures.writtenReadings.isEmpty())
        }
}

package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureReading
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * [MeasureRepository] in memory — `FakeHabitRepository`'s much smaller twin.
 *
 * ⭐ **It has no write methods because the interface has none**, which is the read-only decision
 * showing up in the test layer: there is no `setReading` to fake, so no test can accidentally
 * establish that writing a measure works.
 *
 * ⚠ [observeReadings] filters by the range it is given rather than returning everything, and that is
 * not a nicety. `GetMeasureAverages` asks for exactly its 30-day window and then averages whatever
 * comes back — so a fake that ignored the bounds would make a use case that ignored them pass too,
 * and the window is the whole of what that use case decides.
 */
class FakeMeasureRepository(
    measures: List<Measure> = emptyList(),
    readings: List<MeasureReading> = emptyList(),
) : MeasureRepository {
    private val measureFlow = MutableStateFlow(measures)
    private val readingFlow = MutableStateFlow(readings)

    /** The bounds of every range asked for, so a test can assert on the window itself. */
    val requestedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    override fun observeMeasures(): Flow<List<Measure>> = measureFlow

    override fun observeReadings(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<MeasureReading>> {
        requestedRanges += from to to
        // Inclusive at both ends, as the interface promises and as SQLite's BETWEEN does.
        return readingFlow.map { all -> all.filter { it.date >= from && it.date <= to } }
    }

    fun setMeasures(measures: List<Measure>) {
        measureFlow.value = measures
    }

    fun setReadings(readings: List<MeasureReading>) {
        readingFlow.value = readings
    }
}

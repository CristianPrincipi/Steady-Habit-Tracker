package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * [MeasureRepository] in memory — `FakeHabitRepository`'s much smaller twin.
 *
 * [setReading] (M16.2) only edits the readings flow and records the call. What the real write owes
 * Notion is `MeasureRepositoryImplTest`'s and the sync tests' business, not a ViewModel's.
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

    /** Every [setReading] call, in order. */
    val writtenReadings = mutableListOf<Triple<MeasureId, LocalDate, Double?>>()

    override fun observeMeasures(): Flow<List<Measure>> = measureFlow

    override fun observeReadings(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<MeasureReading>> {
        requestedRanges += from to to
        // Inclusive at both ends, as the interface promises and as SQLite's BETWEEN does.
        return readingFlow.map { all -> all.filter { it.date >= from && it.date <= to } }
    }

    override suspend fun setReading(
        measureId: MeasureId,
        date: LocalDate,
        value: Double?,
    ) {
        writtenReadings += Triple(measureId, date, value)
        val others = readingFlow.value.filterNot { it.measureId == measureId && it.date == date }
        readingFlow.value = others + listOfNotNull(value?.let { MeasureReading(measureId, date, it) })
    }

    override suspend fun setColor(
        measureId: MeasureId,
        color: HabitColor,
    ) {
        measureFlow.value =
            measureFlow.value.map { measure ->
                if (measure.id == measureId) measure.copy(color = color) else measure
            }
    }

    fun setMeasures(measures: List<Measure>) {
        measureFlow.value = measures
    }

    fun setReadings(readings: List<MeasureReading>) {
        readingFlow.value = readings
    }
}

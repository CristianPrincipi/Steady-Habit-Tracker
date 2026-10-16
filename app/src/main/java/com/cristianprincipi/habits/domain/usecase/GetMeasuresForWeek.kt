package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.weekWindow
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureDay
import com.cristianprincipi.habits.domain.model.MeasureWeek
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Every tracked number over one week — the rows under the habit cards on Week (M16.1).
 *
 * [GetHabitsForWeek]'s twin, and built the same way on purpose: the same `weekWindow`, so the
 * seven values sit under the seven pills; one range query for every measure; and "today" read
 * inside the combine, so a re-emission after midnight moves the ring with the habits'.
 *
 * ⚠ **Read-only.** This step draws numbers and writes none — ADR-126 still stands until M16.2.
 *
 * @param dateInWeek any day of the wanted week; normalised to its start here, as the habits' is.
 */
class GetMeasuresForWeek
    @Inject
    constructor(
        private val measures: MeasureRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(dateInWeek: LocalDate): Flow<List<MeasureWeek>> {
            val week = weekWindow(dateInWeek).filterNotNull()
            return combine(
                measures.observeMeasures(),
                measures.observeReadings(from = week.first(), to = week.last()),
            ) { allMeasures, readings ->
                val today = LocalDate.now(clock)
                // The `(measureId, epochDay)` primary key allows one reading per measure per day, so
                // this lookup has no duplicate to choose between.
                val byMeasure =
                    readings
                        .groupBy { it.measureId }
                        .mapValues { (_, list) -> list.associate { it.date to it.value } }
                allMeasures.map { measure -> buildMeasureWeek(measure, week, byMeasure[measure.id].orEmpty(), today) }
            }
        }
    }

/**
 * One measure's seven days — `buildPeriod`'s twin, and shared for its reason: `SampleHabits` builds
 * the preview rows through this too, so a preview cannot keep drawing after the real path breaks.
 *
 * @param values the readings by day; a day absent from the map had none, and stays null.
 */
internal fun buildMeasureWeek(
    measure: Measure,
    week: List<LocalDate>,
    values: Map<LocalDate, Double>,
    today: LocalDate,
): MeasureWeek =
    MeasureWeek(
        measure = measure,
        days = week.map { date -> MeasureDay(date, values[date], isFuture = date > today) },
        todayIndex = week.indexOf(today).takeIf { it >= 0 } ?: HabitPeriod.TODAY_ABSENT,
    )

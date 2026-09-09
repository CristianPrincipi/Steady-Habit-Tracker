package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.MeasureSeries
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Each measure's readings over the last [GetMeasureAverages.MEASURE_WINDOW_DAYS] days, in order,
 * with the mean of them — the home-screen measure widget's whole subject (M15.6d).
 *
 * ### ⭐ Why this exists beside [GetMeasureAverages] rather than inside it
 *
 * They answer different questions over the same data. Stats prints a figure and has no use for the
 * points behind it; a sparkline is the points and prints the figure beside them. Widening
 * `GetMeasureAverages` to carry a list its only caller would drop is the cheaper-looking option and
 * the worse one — every Stats test would then be asserting against readings the screen never draws.
 *
 * ⚠⚠ **The window is [GetMeasureAverages]'s constant and not a second one.** The widget shows the
 * line and the average together, so a line covering ninety days beside a thirty-day mean would be
 * two claims about different spans printed a few pixels apart. One constant means that cannot drift
 * — and if the window is ever reconsidered, it moves for both surfaces at once.
 *
 * ### ⚠ It reads the clock, for [GetMeasureAverages]'s reason
 *
 * The window ends today, `CLAUDE.md` §6 forbids `LocalDate.now()` in domain code, and a widget left
 * on a home screen across midnight would otherwise keep yesterday's window for ever. The Glance
 * session that collects this lives about 45 seconds, so the flow is rebuilt far more often than a
 * day — but the constraint is the same one, and an injected clock is what makes it testable at all.
 */
class GetMeasureSeries
    @Inject
    constructor(
        private val measures: MeasureRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(): Flow<List<MeasureSeries>> {
            val today = LocalDate.now(clock)
            // Inclusive of both ends, exactly as GetMeasureAverages': today and the 29 before it.
            val from = today.minusDays(GetMeasureAverages.MEASURE_WINDOW_DAYS - 1L)

            return combine(
                measures.observeMeasures(),
                measures.observeReadings(from = from, to = today),
            ) { allMeasures, readings ->
                val byMeasure = readings.groupBy { it.measureId }
                allMeasures.map { measure ->
                    MeasureSeries(
                        measure = measure,
                        from = from,
                        to = today,
                        // ⚠ Sorted HERE because the range query is not ordered — `observeBetween`
                        // is a plain `BETWEEN` with no `ORDER BY`, so SQLite's row order is its own
                        // business. `MeasureSeries` requires strictly increasing dates, which the
                        // `(measureId, epochDay)` primary key makes achievable: there cannot be two
                        // readings for one measure on one day for a sort to leave ambiguous.
                        readings = byMeasure[measure.id].orEmpty().sortedBy { it.date },
                    )
                }
            }
        }
    }

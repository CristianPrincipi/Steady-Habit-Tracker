package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.MeasureAverage
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Each measure's mean reading over the last [MEASURE_WINDOW_DAYS] days — the Stats screen's
 * "Daily averages" section (M15.4).
 *
 * ### ⭐ Thirty days, and it is the one span on Stats that is not the trailing year
 *
 * The user's answer, 2026-09-08, chosen over "the trailing year" and "all time" with the trade-off
 * on the table. The year would have matched the dot grid and the monthly chart directly above it,
 * and a year-mean body weight is a number nobody acts on: it moves a few grams a month and never
 * says where you are now.
 *
 * ⚠ **The cost is real and is paid in the label, not here.** Because this window is the only one of
 * its kind on the screen, the tile has to say "30 days" rather than just "average" — otherwise the
 * reader carries the year over from the section above and misreads the number.
 *
 * ### ⚠⚠ No readings is not an average of zero
 *
 * A window with nothing in it produces `MeasureAverage(value = null)`, which the screen draws as a
 * dash. `0.0` would be a confident, specific, wrong number where the honest answer is that the user
 * has not weighed themselves this month — and it is the failure this whole feature is shaped
 * against, running from `MeasurementEntity` storing no row for a day with no reading all the way up
 * to here. [MeasureAverage] enforces the pairing by invariant so it cannot be got wrong in between.
 *
 * ### It is a mean over the days that have a reading, not over the window
 *
 * Twelve weigh-ins in thirty days average those twelve. Dividing by thirty would be the same zero
 * mistake wearing a denominator, and it would make a user who weighs themselves weekly look like
 * they had lost four fifths of their body mass. The count travels with the number for exactly this
 * reason: an average over three days and one over thirty are different claims, and a figure with no
 * denominator invites the reader to assume the larger.
 *
 * ### Why it holds a clock
 *
 * `CLAUDE.md` §6 forbids `LocalDate.now()` in domain code, and the window ends today, so this reads
 * the clock once per invocation. Left open past midnight the section would keep yesterday's window
 * — the same day-rollover case `GetHabitStats` handles, and the reason both are re-invoked when
 * `TodayFlow` ticks rather than trusted to be right for ever.
 */
class GetMeasureAverages
    @Inject
    constructor(
        private val measures: MeasureRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(): Flow<List<MeasureAverage>> {
            val today = LocalDate.now(clock)
            // Inclusive of both ends, so a 30-day window is today and the 29 days before it.
            val from = today.minusDays(MEASURE_WINDOW_DAYS - 1L)

            return combine(
                measures.observeMeasures(),
                measures.observeReadings(from = from, to = today),
            ) { allMeasures, readings ->
                val byMeasure = readings.groupBy { it.measureId }
                allMeasures.map { measure ->
                    val values = byMeasure[measure.id].orEmpty().map { it.value }
                    MeasureAverage(
                        measure = measure,
                        // `average()` on an empty list is NaN, not zero, and NaN would render as
                        // "NaN kg". Neither is the answer: there is no average, so there is none.
                        value = if (values.isEmpty()) null else values.average(),
                        readings = values.size,
                    )
                }
            }
        }

        companion object {
            /**
             * The window, in days, **inclusive of today**.
             *
             * Public because the Stats screen's label has to name it, and a screen that hardcoded
             * "30" beside a use case that computed 60 would be a lie no test would catch.
             */
            const val MEASURE_WINDOW_DAYS = 30
        }
    }

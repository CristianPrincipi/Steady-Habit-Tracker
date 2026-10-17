package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Sets, changes or clears one number on one day — M16.2b, the Week cell's write.
 *
 * [ToggleCompletion]'s twin, with its one rule: **a day that has not happened yet is refused.** The
 * cell for tomorrow is inert, so no tap reaches this with a future date; the guard stays here
 * because it is the domain's rule and the cell only mirrors it — if the two ever disagree, the cell
 * is the bug. Everything else — what Notion is owed, the base value, the widget — is
 * `MeasureRepository.setReading`'s.
 */
class SetMeasureReading
    @Inject
    constructor(
        private val measures: MeasureRepository,
        private val clock: Clock,
    ) {
        /**
         * @param value the reading, or `null` to clear it.
         * @return `false` when [date] is in the future and nothing was written.
         */
        suspend operator fun invoke(
            measureId: MeasureId,
            date: LocalDate,
            value: Double?,
        ): Boolean {
            if (date > LocalDate.now(clock)) return false
            measures.setReading(measureId = measureId, date = date, value = value)
            return true
        }
    }

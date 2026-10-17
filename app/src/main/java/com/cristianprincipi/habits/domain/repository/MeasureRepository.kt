package com.cristianprincipi.habits.domain.repository

import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The numbers the user tracks alongside their habits (M15.4).
 *
 * ### ⭐⭐ A number can be written since M16.2 (ADR-156, superseding ADR-126 §3)
 *
 * Until v1.1 the data here was read-only, held so in four places. The user asked to type a weight
 * or a night's sleep in the app and see it in Notion, so [setReading] exists now — and the four
 * places became one rule: **only a number this device has an edit for is ever sent**, and sending it
 * never touches the day's checkboxes (`MeasurementEditEntity` says why that has to be).
 *
 * ⭐ **A colour is still not data**, for the reason a habit's is (`SetHabitColor`, ADR-010): the
 * user's database has no colour property, so a colour is not in the schema, not on a page, and not
 * something a pull can overwrite.
 */
interface MeasureRepository {
    /**
     * The measures to show, in the user's order.
     *
     * **Only the ones still in the mapped Notion set**, exactly as [HabitRepository.observeHabits]
     * is: a measure whose column left that set keeps its row and every reading, and is hidden
     * everywhere until the column returns (ADR-081). There is no way to ask for the hidden ones.
     */
    fun observeMeasures(): Flow<List<Measure>>

    /**
     * Every reading in `[from, to]`, **both bounds inclusive**, across all measures.
     *
     * One range query, shaped after `observeCompletions` and for its reason — there is no per-measure
     * overload because nothing needs one, and an unused query is an untested one.
     *
     * ⚠ **Absence is not zero.** A day with no [MeasureReading] had no reading at all, which is what
     * a skipped weigh-in is. Nothing here invents a value for it, and nothing above may either.
     */
    fun observeReadings(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<MeasureReading>>

    /**
     * Sets, changes or clears one number on one day — M16.2, ADR-156.
     *
     * It lands in Room at once and every screen and widget shows it; Notion gets it on the next
     * sync, exactly as a check-off does. **The day's checkbox sync state is not touched.**
     *
     * @param value the reading, or `null` to clear it — which Notion is sent as an empty number,
     *   **never a zero** (a skipped weigh-in is not a weight of nothing). Must be finite.
     */
    suspend fun setReading(
        measureId: MeasureId,
        date: LocalDate,
        value: Double?,
    )

    /**
     * Gives a measure the colour the user picked — M15.6f, the user's own request after seeing the
     * widget: *"I would add the possibility to change sleep and Weight colour."*
     *
     * ⚠ **App-local: it reaches no reading and no Notion page.** See the class KDoc.
     */
    suspend fun setColor(
        measureId: MeasureId,
        color: HabitColor,
    )
}

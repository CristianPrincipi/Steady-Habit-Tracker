package com.cristianprincipi.habits.domain.repository

import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureReading
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The numbers the user tracks alongside their habits (M15.4).
 *
 * ### ⚠ Read-only, and there is deliberately no write on this interface
 *
 * [HabitRepository] has `setCompleted` and `setColor` because a habit is something the user does.
 * A measure is something the user records **in Notion**, and this app mirrors it: the scope was
 * *"read only, just the average on stats"*. There is no `setReading` here, no `setUnit` — the unit
 * is edited on the mapping screen, which owns the mapping — and no way for anything above the data
 * layer to change a number.
 *
 * That is the last of four places the read-only decision is held. The others: `NotionPropertyValue`
 * has no writable `Number`, `SyncEngine.pushDay` builds its body from habits only, and measure
 * property ids live outside `NotionPropertyMapping.habitPropertyIds`. Any one of them alone would
 * be a rule; four of them is a shape.
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
}

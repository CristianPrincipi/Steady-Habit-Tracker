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
 * ### ⚠⚠ The DATA is read-only. [setColor] is not data.
 *
 * A measure is something the user records **in Notion**, and this app mirrors it: the scope was
 * *"read only, just the average on stats"*. There is no `setReading` here, no `setUnit` — the unit
 * is edited on the mapping screen, which owns the mapping — and **no way for anything above the
 * data layer to change a number**. That claim is unchanged and is the one worth defending.
 *
 * It is still held in four places: `NotionPropertyValue` has no writable `Number`,
 * `SyncEngine.pushDay` builds its body from habits only, measure property ids live outside
 * `NotionPropertyMapping.habitPropertyIds`, and there is no write here that touches a reading. Any
 * one of them alone would be a rule; four of them is a shape.
 *
 * ⭐ **A colour is on the other side of that line, for the reason a habit's is** (`SetHabitColor`,
 * ADR-010): the user's database has no colour property, so a colour is not in the schema, not on a
 * page, and not something a pull can overwrite. `MeasureImporter` has carried it forward across
 * every sync since M15.4 precisely because it exists nowhere but here. Letting the user choose it
 * adds nothing to what this app can write to Notion, which is still nothing.
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
     * Gives a measure the colour the user picked — M15.6f, the user's own request after seeing the
     * widget: *"I would add the possibility to change sleep and Weight colour."*
     *
     * ⚠ **The only write on this interface, and it reaches no reading and no Notion page.** See the
     * class KDoc: the read-only decision is about the *data*, and a colour is not in the schema.
     */
    suspend fun setColor(
        measureId: MeasureId,
        color: HabitColor,
    )
}

package com.cristianprincipi.habits.domain.repository

import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Everything the app can ask about habits, and the two things it can change.
 *
 * **Reads come from Room and only from Room** (`CLAUDE.md \§1`). Nothing here awaits the network: an
 * implementation that called Notion to answer [observeHabits] would make every screen as reliable as
 * the connection, which is the architecture this app exists to avoid.
 *
 * **The write surface is this small on purpose.** ADR-010 removed creating, renaming and deleting
 * habits \— in the user's Notion database a habit is a *checkbox property*, so creating one is a
 * schema change and deleting one destroys a column of history irreversibly. That work stays in
 * Notion, where its blast radius is visible. What is left is checking a day off and choosing a
 * colour, and colour never leaves the device.
 */
interface HabitRepository {
    /**
     * The habit set, ordered as the UI should show it.
     *
     * **Only the habits still in the mapped Notion set.** A habit whose property has left that set
     * keeps its row and its whole history in Room — nothing in this app deletes one — but it is
     * hidden everywhere, by the user's decision on 2026-08-31 (ADR-081), and comes back the moment
     * the property does. There is deliberately no way to ask for the hidden ones: this interface is
     * the only door to the habit set, so a screen cannot show one by accident.
     */
    fun observeHabits(): Flow<List<Habit>>

    /**
     * Every completion in `[from, to]`, **both bounds inclusive**, across all habits.
     *
     * One range query serves every v1 screen \— seven days for Week, a month for Month, a year for
     * Year, and start-date-to-today for the streaks and rates on Stats. There is deliberately no
     * per-habit overload: nothing needs one, and an unused query is an untested one.
     *
     * Absence is the answer for a missed day. A day with no [Completion] was not done; see
     * [Completion] for why misses are not stored.
     */
    fun observeCompletions(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Completion>>

    /**
     * Put the habits in this order — M15.5.
     *
     * **The whole list, not a move**, and the list is the domain's business rather than this
     * layer's: [com.cristianprincipi.habits.domain.usecase.MoveHabit] owns the rule that a move
     * swaps with a neighbour, and this only writes the answer. An interface that took
     * `(habitId, direction)` would put list arithmetic in the data layer and make the rule
     * untestable without a database.
     *
     * ⚠ **Ids not in [orderedIds] are left where they are.** The caller passes every visible
     * habit, so in practice the set is complete; a partial list is not rejected because there is
     * nothing sensible to do about it — renumbering only what was named is the least surprising
     * answer and cannot lose a habit.
     *
     * Local only. Notion has no order property (`docs/notion-schema.md` §3), so this is never
     * pushed — the same standing as the colour.
     */
    suspend fun setHabitOrder(orderedIds: List<HabitId>)

    /**
     * Check a habit off on a day, or take it back.
     *
     * **Idempotent, and states rather than toggles.** Two calls with `completed = true` leave one
     * completion. That matters more here than it looks: the check-off has to survive being replayed
     * by a sync that was killed mid-run (`CLAUDE.md \§3.5`), and a toggle replayed is a toggle undone.
     *
     * Returns as soon as the local write lands. Notion finds out when the sync engine next runs, and
     * the call succeeds whether or not the device has a network.
     */
    suspend fun setCompleted(
        habitId: HabitId,
        date: LocalDate,
        completed: Boolean,
    )

    /**
     * Set a habit's colour.
     *
     * App-local and one-way: the user's database has no colour property, so this is stored in Room
     * and never pushed (`docs/notion-schema.md \§3`).
     */
    suspend fun setColor(
        habitId: HabitId,
        color: HabitColor,
    )
}

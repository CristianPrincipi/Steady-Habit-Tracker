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
     * @param includeInactive whether to include habits whose checkbox property has disappeared from
     *   the Notion schema. The habit screens pass `false`; M7.5's manage-habits list passes `true`,
     *   because a habit that vanished upstream still has local history worth seeing.
     */
    fun observeHabits(includeInactive: Boolean): Flow<List<Habit>>

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

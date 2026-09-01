package com.cristianprincipi.habits.feature.stats

import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.HabitStats

/**
 * What the Stats screen is showing — `docs/design-spec.md` §5.
 *
 * ### The only screen in the app whose state is about **one** habit
 *
 * Week, Month and Year all carry a list and draw every habit in it. This one is a deep dive: the
 * design gives it a selector precisely because everything below that selector — the year grid, the
 * four tiles, the chart — describes a single habit. So [Content] carries the list *and* the chosen
 * one's figures, and the list is there only to draw the pills.
 */
sealed interface StatsUiState {
    /**
     * The first query has not come back yet.
     *
     * Draws the header and nothing under it, exactly as the other three tabs do: a spinner for a
     * Room query that returns in a frame is a flash of chrome, and `CLAUDE.md` §1 forbids the UI
     * awaiting anything slower.
     */
    data object Loading : StatsUiState

    /**
     * No habits **at all** — never "no data for this habit".
     *
     * A habit with an empty history is not empty here: it has a streak of zero, a rate of 0% and a
     * grid of misses, all of which are true things to draw. The insufficient-data case the design
     * worries about is M9.6's, and it lives inside [Content].
     */
    data class Empty(
        val reason: HabitsEmptyReason,
    ) : StatsUiState

    /**
     * One habit's figures, plus the set to choose from.
     *
     * @param habits every habit, in the repository's order — the selector pills, and nothing else.
     * @param selected the chosen habit's [HabitStats]. It is not an index into [habits] and not an
     *   id to look up: a screen that stored either could render a name from one habit and a streak
     *   from another for the frame between a re-order and the next query.
     */
    data class Content(
        val habits: List<Habit>,
        val selected: HabitStats,
    ) : StatsUiState {
        /** Which pill draws as selected. Derived, so it cannot disagree with [selected]. */
        val selectedId: HabitId get() = selected.habit.id
    }
}

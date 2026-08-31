package com.cristianprincipi.habits.feature.history

import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.domain.usecase.HabitYear

/**
 * What the Year screen is showing — `docs/design-spec.md` §4.
 *
 * ### The plainest of the three, and it is the window that makes it so
 *
 * [MonthUiState] carries its month because the user can page to one and the title is built from it.
 * This screen has neither: `GetHabitsForYear` takes **no date at all**, because the design gives the
 * Year view no sub-navigation and the window is always the trailing 365 days ending today. So there
 * is nothing to pin, nothing to normalise back to `null`, and no snapshot to pair with a query.
 *
 * The title is the constant word "Year", which is why this file has no `month`-shaped property and
 * `feature/history/` has no `YearHeader.kt`.
 */
sealed interface YearUiState {
    /**
     * The first query has not come back yet.
     *
     * Draws the header and nothing under it. Not an empty `Content` and not [Empty]: [Empty] says
     * "you have no habits", which is a claim this state cannot support yet.
     */
    data object Loading : YearUiState

    /**
     * No habits **at all** — never "none this year".
     *
     * Habits are the checkbox columns of one Notion database (ADR-010), so they are global and this
     * screen empties for exactly the reason Week and Month do, in exactly the same words
     * ([HabitsEmptyReason]).
     */
    data class Empty(
        val reason: HabitsEmptyReason,
    ) : YearUiState

    /**
     * A trailing year per habit, each with the completion figure its card prints beside it.
     *
     * @param habits [HabitYear]s straight from the use case. The grid and the percentage travel
     *   together because they are measured over the same window and the card shows both — computing
     *   them apart would risk a grid and a figure that disagree (`docs/design-spec.md` §4).
     */
    data class Content(
        val habits: List<HabitYear>,
    ) : YearUiState
}

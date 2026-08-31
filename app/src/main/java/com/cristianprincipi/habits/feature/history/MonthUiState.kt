package com.cristianprincipi.habits.feature.history

import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.domain.model.HabitPeriod
import java.time.YearMonth

/**
 * What the Month screen is showing — `docs/design-spec.md` §2.
 *
 * ### Why [month] is on the interface rather than inside [Content]
 *
 * `WeekUiState` keeps its week inside `Content` and renders no sub-nav without one, because the Week
 * header's title is the constant word "Week". This screen's title **is** the month
 * (`App.tsx` `headerTitles.month`), so a state with no month is a screen with no title.
 *
 * It costs nothing to carry, because the month never came from the repository: it is
 * `anchor ?: today`, which [MonthViewModel] knows synchronously and before any query returns. The
 * *habits* are what have to be waited for.
 */
sealed interface MonthUiState {
    /** The month being shown — the paged-to one, not today's, once the user has stepped away. */
    val month: YearMonth

    /**
     * The first query has not come back yet.
     *
     * Draws the header and nothing under it. Not an empty `Content`, and not [Empty]: neither is
     * known yet, and [Empty] in particular reads as "you have no habits", which is a claim this
     * state cannot support.
     */
    data class Loading(
        override val month: YearMonth,
    ) : MonthUiState

    /**
     * No habits **at all** — never "none in this month".
     *
     * Habits are the checkbox columns of one Notion database (ADR-010), so they are global and this
     * screen empties for exactly the reason Week does. Paging cannot change it, which is why the
     * sub-nav is hidden here: a control whose effect is provably nil.
     */
    data class Empty(
        override val month: YearMonth,
        val reason: HabitsEmptyReason,
    ) : MonthUiState

    /**
     * Habits to draw, for one calendar month.
     *
     * @param habits one entry per habit, each already holding its cells — leading `Padding` for the
     *   blanks that push the 1st onto its real weekday, then one `Day` per day of the month.
     *   `GetHabitsForMonth` does the work; this state is a courier, exactly as `WeekUiState` is.
     */
    data class Content(
        override val month: YearMonth,
        val habits: List<HabitPeriod>,
    ) : MonthUiState
}

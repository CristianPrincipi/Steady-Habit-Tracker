package com.cristianprincipi.habits.feature.history

import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.domain.model.HabitPeriod
import java.time.YearMonth

/**
 * What the Month screen is showing — `docs/design-spec.md` §2.
 *
 * ### Why [month] is on the interface rather than inside [Content]
 *
 * M8.1 put it there because the title was then the month's own name, so a state without a month was
 * a screen without a title. ⚠ **M8.7 made the title a constant word** (ADR-091) — and it stays on
 * the interface anyway, because `MonthHeader` reads it for the sub-nav label and because it costs
 * nothing: the month never came from the repository. It is `anchor ?: today`, which [MonthViewModel]
 * knows synchronously and before any query returns. The *habits* are what have to be waited for.
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
     * @param isCurrentMonth whether [month] is the month containing today, which is the whole of the
     *   "This month" button's enabled state (M8.7). One of the two fields here that needs a clock,
     *   which is precisely why it is a field: `CLAUDE.md` §6 keeps `now` out of composables, so the
     *   ViewModel answers the question and the UI reads a value.
     */
    data class Content(
        override val month: YearMonth,
        val habits: List<HabitPeriod>,
        val isCurrentMonth: Boolean,
    ) : MonthUiState
}

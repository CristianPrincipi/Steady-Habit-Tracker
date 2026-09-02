package com.cristianprincipi.habits.domain.model

import java.time.YearMonth

/**
 * One bar of the Stats screen's monthly chart — `docs/design-spec.md` §5.4.
 *
 * A [YearMonth] rather than a formatted label, for [HabitPeriod]'s reason: the domain has no
 * business knowing what a month is called, and the answer depends on a locale only a composable
 * holds. `core/datetime`'s `shortMonthLabel` turns this into the `Sep` the axis draws.
 *
 * **The month is carried in full, not just its name, and that matters at both ends of the window.**
 * A trailing year spans thirteen calendar months, so two of them share a short name — the axis
 * reading `Sep … Sep` is correct and is what the prototype draws. Something keyed on the name alone
 * would have merged those two buckets into one.
 *
 * @param completions days in [month] that were checked off. Never the whole month's length: the
 *   first and last buckets of the window are partial, and a habit that started mid-month has a
 *   partial one of its own.
 */
data class MonthlyCompletions(
    val month: YearMonth,
    val completions: Int,
) {
    init {
        require(completions >= 0) { "A month cannot have $completions completions" }
    }
}

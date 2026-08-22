package com.cristianprincipi.habits.core.datetime

import java.time.DayOfWeek

/**
 * The day a week starts on, for every view that draws one.
 *
 * **A constant, deliberately not a preference.** The prototype's Week strip runs Monday to Sunday
 * and offers no way to change it, so a setting would be a control the design never asked for, a
 * DataStore key, a migration and a second code path through the Week and Month grids — all to
 * reproduce what the user already sees. M11.3 deferred the preference post-v1; this is what stands
 * in for it.
 *
 * It lives here rather than in the design system because it is calendar arithmetic, not decoration:
 * M8's Month grid needs it to work out how many blank cells precede the first of the month, and
 * getting it from a different place than the Week strip does is how the two end up disagreeing.
 */
object WeekStart {
    val day: DayOfWeek = DayOfWeek.MONDAY
}

package com.cristianprincipi.habits.core.datetime

import java.time.Duration
import java.time.Instant

/**
 * How long ago something happened, coarsened to the unit a person would actually say.
 *
 * **A value, not a formatted string.** The words live in `strings.xml` (`CLAUDE.md` §6: every
 * user-facing string is a resource) and the plural rules live with them, so this carries the
 * *decision* — which unit, and how many — and nothing else. That is also what makes it testable
 * without a device or a locale.
 *
 * The ladder stops at days on purpose. This describes the last successful sync, and a sync that last
 * worked more than a few days ago is a problem the failure banner is already talking about — dressing
 * it up as "2 months ago" would add precision nobody needs to a number that only means "too long".
 */
sealed interface Elapsed {
    /** Less than a minute. "Just now" — no number is worth showing at this resolution. */
    data object JustNow : Elapsed

    data class Minutes(
        val count: Long,
    ) : Elapsed

    data class Hours(
        val count: Long,
    ) : Elapsed

    data class Days(
        val count: Long,
    ) : Elapsed
}

/**
 * @param then the moment in the past. @param now the moment to measure from — **passed in, never
 *   read**, because `CLAUDE.md` §6 says domain code must not reach for the clock itself.
 *
 * **A future [then] is [Elapsed.JustNow], not a negative count.** It should be impossible — the only
 * caller is a sync timestamp this device wrote — but the device's clock can move backwards (a
 * timezone fix, an NTP correction), and "in -3 minutes" is a worse answer than "just now" for a
 * caption nobody is relying on.
 */
fun elapsedBetween(
    then: Instant,
    now: Instant,
): Elapsed {
    val elapsed = Duration.between(then, now)
    if (elapsed.isNegative) return Elapsed.JustNow

    val minutes = elapsed.toMinutes()
    val hours = elapsed.toHours()
    val days = elapsed.toDays()
    return when {
        minutes < 1 -> Elapsed.JustNow
        hours < 1 -> Elapsed.Minutes(minutes)
        days < 1 -> Elapsed.Hours(hours)
        else -> Elapsed.Days(days)
    }
}

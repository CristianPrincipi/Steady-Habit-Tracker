package com.cristianprincipi.habits.core.datetime

import java.time.LocalDate
import java.time.YearMonth

/** Seven, and every grid in this app is seven across or seven tall (`docs/design-spec.md` §2). */
const val DAYS_PER_WEEK = 7

/** The trailing window the Year grid and every rate in the app are measured over. */
const val DAYS_PER_YEAR_WINDOW = 365

/**
 * The date each cell of a grid stands for, `null` where the cell is padding.
 *
 * One shape serves all three views because all three are the same thing: some blank cells, then a
 * run of consecutive days, then possibly more blank cells. What differs is only how many of each.
 *
 * Kept here rather than in `domain/` on purpose — this is calendar arithmetic, the "week math"
 * `CLAUDE.md` §5 puts in `core/datetime`. It knows nothing about habits or completions; the use
 * cases decide what each date *means*.
 */
typealias CalendarWindow = List<LocalDate?>

/**
 * The seven days of the week containing [date], [WeekStart] first.
 *
 * No padding: a week is aligned to the week start by definition. Any date in the week gives the
 * same answer, so callers stepping ±7 days never have to normalise first.
 */
fun weekWindow(date: LocalDate): CalendarWindow {
    val start = startOfWeek(date)
    return List(DAYS_PER_WEEK) { start.plusDays(it.toLong()) }
}

/** The [WeekStart] on or before [date]. */
fun startOfWeek(date: LocalDate): LocalDate {
    // Math.floorMod, via the 0..6 arithmetic below, because daysBetween can be negative and Kotlin's
    // % keeps the sign — which would put the start of the week *after* the date for three days out
    // of seven.
    val shift = (date.dayOfWeek.value - WeekStart.day.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
    return date.minusDays(shift.toLong())
}

/**
 * One cell per day of [month], preceded by the blanks that push the 1st onto its real weekday.
 *
 * **No trailing padding**, so the last row is ragged — `docs/design-spec.md` §2 is explicit that
 * adding any would change the shape of every month.
 */
fun monthWindow(month: YearMonth): CalendarWindow {
    val first = month.atDay(1)
    val leading = leadingBlanks(first)
    return List(leading) { null } + List(month.lengthOfMonth()) { first.plusDays(it.toLong()) }
}

/**
 * The 365 days ending on [today], padded at both ends to whole columns.
 *
 * The Year grid is column-major with seven rows, so a column is exactly one week: the flat list
 * below already reads down the columns without any transposition. Leading blanks put row one on the
 * week start; trailing blanks complete the final column (`docs/design-spec.md` §4).
 */
fun trailingYearWindow(today: LocalDate): CalendarWindow {
    val first = today.minusDays((DAYS_PER_YEAR_WINDOW - 1).toLong())
    val leading = leadingBlanks(first)
    val days = List(DAYS_PER_YEAR_WINDOW) { first.plusDays(it.toLong()) }
    val trailing = (DAYS_PER_WEEK - (leading + DAYS_PER_YEAR_WINDOW) % DAYS_PER_WEEK) % DAYS_PER_WEEK
    return List(leading) { null } + days + List(trailing) { null }
}

private fun leadingBlanks(first: LocalDate): Int =
    (first.dayOfWeek.value - WeekStart.day.value + DAYS_PER_WEEK) % DAYS_PER_WEEK

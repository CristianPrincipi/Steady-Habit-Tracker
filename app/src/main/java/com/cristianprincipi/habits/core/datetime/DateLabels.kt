package com.cristianprincipi.habits.core.datetime

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/*
 * Turning dates into text — the "date formatting" half of the package `CLAUDE.md` §5 describes.
 *
 * **Every function here takes a [Locale] rather than reading one.** That keeps `core/datetime` pure
 * `java.time` with no Android import, and it means a composable passes the locale from its own
 * configuration — which recomposes when the user changes their language, where a
 * `Locale.getDefault()` read at class-init would go stale.
 *
 * ### Why the platform does the translating
 *
 * `CLAUDE.md` §8 wants every user-facing string externalised so translation is cheap. Weekday and
 * month names are the case where that work is already done: `java.time` reads the JDK's CLDR data,
 * so these are right in every locale Android supports without anyone maintaining seven `<string>`s
 * per language — and right about abbreviation and ordering conventions that a translator would
 * otherwise have to be told about one language at a time.
 */

/**
 * The `Mon…Sun` initials above a week of pills — `docs/design-spec.md` §3.
 *
 * **Seven labels, starting at [WeekStart]**, so they line up with the seven cells `weekWindow`
 * produces. It reads `WeekStart` rather than hardcoding Monday for the reason that object's own
 * KDoc gives: a header that gets its first day from somewhere different than the grid does is a
 * header that ends up one column out of step with the days beneath it.
 */
fun weekdayLabels(locale: Locale): List<String> =
    List(DAYS_PER_WEEK) { offset ->
        WeekStart.day.plus(offset.toLong()).getDisplayName(TextStyle.SHORT, locale)
    }

/**
 * The `Aug 17 – Aug 23` label between the sub-nav arrows — `docs/design-spec.md` §3.
 *
 * An en dash, not a hyphen: it is a range, and the design draws one.
 *
 * ### ⚠ The year appears only when it is not [currentYear] — the user's call at M6.11
 *
 * The prototype shows `Aug 17 – Aug 23` and nothing more, and never had to care that paging back
 * makes that ambiguous, because its arrows are inert like every other control in it
 * (`docs/design-spec.md` §0). Ours page. M6.4 shipped the design's format and flagged it; the user
 * chose the hedge over both alternatives — **no year in the common case, a year the moment the week
 * is not in the current one**, so the label costs nothing on the screen you open every day and stops
 * lying on the one you reach after eleven taps back.
 *
 * **Decided per endpoint, not per range**, which is what makes a week straddling New Year read
 * correctly: with [currentYear] 2026, `Dec 28 – Jan 3, 2027` says exactly which January that is
 * while leaving December alone. A range test would have had to pick one year for both ends and be
 * wrong about the other.
 *
 * ### The pattern is `MMM d`, which is right for English and approximate elsewhere
 *
 * `CLAUDE.md` §8 makes v1 English-only. A properly localised range would need a *skeleton* rather
 * than a pattern — some locales lead with the day, and a few would want the month only once — and
 * `java.time` has no public skeleton API. `MMM d` is honest about being the design's format; the
 * month and day names inside it still come from the locale.
 *
 * @param currentYear the year to treat as "now", passed in rather than read: `CLAUDE.md` §6 keeps
 *   the clock out of everything below the ViewModel, and `WeekUiState.Content.currentYear` carries
 *   it from the same snapshot that decides which cell is today — so the label and the ring can
 *   never describe two different days.
 */
fun weekRangeLabel(
    weekStart: LocalDate,
    weekEndInclusive: LocalDate,
    locale: Locale,
    currentYear: Int,
): String {
    val start = weekStart.format(weekRangeFormat(weekStart.year, currentYear, locale))
    val end = weekEndInclusive.format(weekRangeFormat(weekEndInclusive.year, currentYear, locale))
    return "$start $EN_DASH $end"
}

private fun weekRangeFormat(
    year: Int,
    currentYear: Int,
    locale: Locale,
): DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        if (year == currentYear) WEEK_RANGE_PATTERN else WEEK_RANGE_PATTERN_WITH_YEAR,
        locale,
    )

private const val WEEK_RANGE_PATTERN = "MMM d"

/** `yyyy`, the year of era — not `YYYY`, which is the week-based year and off by one each New Year. */
private const val WEEK_RANGE_PATTERN_WITH_YEAR = "MMM d, yyyy"

/** `–`, written as an escape so it cannot be mistaken for a hyphen in a diff. */
private const val EN_DASH = "–"

/**
 * `August 2026` — the Month view's sub-nav label (`docs/design-spec.md` §2).
 *
 * The year is always printed, unlike [weekRangeLabel], which drops it for the current one. A week
 * label is two dates wide and gains nothing from a year the user is already in; a month label is one
 * word, and a `‹ August ›` that pages twelve times back to an identical `‹ August ›` would be a
 * control whose effect is invisible.
 *
 * `LLLL` rather than `MMMM`: this is a month standing on its own, not one inside a date. English
 * cannot tell the two apart, but the locales that inflect their month names can, and reading
 * `августа 2026` — the genitive, as in "the 3rd of August" — where a heading belongs is the kind of
 * bug that only ever shows up on someone else's phone.
 *
 * ⚠ **This had a `monthName` sibling and no longer does.** M8.1 built the Month header title from
 * the paged-to month's name, as the prototype does; M8.7 put that on a device and the user chose the
 * constant word "Month" instead (ADR-091), which left the function with no readers at all. The
 * month name still reaches the screen — through this label, between the arrows that explain it.
 */
fun monthLabel(
    month: YearMonth,
    locale: Locale,
): String = month.format(DateTimeFormatter.ofPattern(MONTH_LABEL_PATTERN, locale))

private const val MONTH_LABEL_PATTERN = "LLLL yyyy"

/**
 * `Sep` — one tick of the Stats screen's monthly chart (`docs/design-spec.md` §5.4).
 *
 * `LLL` rather than `MMM`, for [monthLabel]'s reason: an axis tick is a month standing on its own,
 * and the locales that inflect their month names spell that differently from the one inside a date.
 * English cannot tell the two apart, which is exactly why the wrong one survives review.
 *
 * ⚠ **The year is deliberately absent, and the axis can therefore repeat a label.** A trailing year
 * spans thirteen calendar months, so its first and last tick share a name — `Sep … Sep`, which is
 * what the prototype draws and is honest about the window being a full year. The bucket behind each
 * tick still knows which September it is; only the label is short.
 */
fun shortMonthLabel(
    month: YearMonth,
    locale: Locale,
): String = month.format(DateTimeFormatter.ofPattern(SHORT_MONTH_LABEL_PATTERN, locale))

private const val SHORT_MONTH_LABEL_PATTERN = "LLL"

/**
 * A date as a screen reader should hear it — "Monday, 24 August 2026".
 *
 * [FormatStyle.FULL] rather than a hand-written pattern: it is locale-correct for free, and it
 * carries the year. Verbose on screen, but nothing shows this — it is a `contentDescription`, where
 * being unambiguous beats being brief, and a pill in a week the user has paged away from should not
 * announce a bare "24 August".
 */
fun accessibleDate(
    date: LocalDate,
    locale: Locale,
): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

/**
 * A date short enough to sit inside a sentence — "Wed, Aug 19".
 *
 * For the undo snackbar (M6.6), whose whole job is to name **which** day a tap changed: the pill
 * flipping already says that something happened, and only the text can say what. That is why the
 * weekday is in here and not just the date — a mis-tap on this screen lands on the *neighbouring
 * column*, so "Wed" is the word that catches it.
 *
 * `MMM d` is deliberately the same half-pattern [weekRangeLabel] uses, so the snackbar and the
 * header above it name a day the same way rather than in two dialects. The same caveat applies:
 * it is the design's format, correct for English and approximate in locales that lead with the day
 * (`CLAUDE.md` §8 makes v1 English-only). The weekday and month names still come from the locale.
 */
fun shortDateLabel(
    date: LocalDate,
    locale: Locale,
): String = date.format(DateTimeFormatter.ofPattern(SHORT_DATE_PATTERN, locale))

private const val SHORT_DATE_PATTERN = "EEE, MMM d"

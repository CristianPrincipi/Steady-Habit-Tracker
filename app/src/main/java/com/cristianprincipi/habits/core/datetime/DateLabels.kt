package com.cristianprincipi.habits.core.datetime

import java.time.LocalDate
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
 * ### ⚠ No year, and that is the design's call rather than an oversight
 *
 * The prototype shows `Aug 17 – Aug 23` and nothing more. Paging back far enough therefore makes
 * the label ambiguous — which the prototype never had to care about, because its arrows are inert
 * like every other control in it (`docs/design-spec.md` §0). The remedy `Plan.md` M6.4 asks for is
 * a **jump back to this week**, not a year in the label, so that is what the header carries. If a
 * year turns out to be wanted, this is the one function that changes. Flagged at M6.11.
 *
 * ### The pattern is `MMM d`, which is right for English and approximate elsewhere
 *
 * `CLAUDE.md` §8 makes v1 English-only. A properly localised range would need a *skeleton* rather
 * than a pattern — some locales lead with the day, and a few would want the month only once — and
 * `java.time` has no public skeleton API. `MMM d` is honest about being the design's format; the
 * month and day names inside it still come from the locale.
 */
fun weekRangeLabel(
    weekStart: LocalDate,
    weekEndInclusive: LocalDate,
    locale: Locale,
): String {
    val format = DateTimeFormatter.ofPattern(WEEK_RANGE_PATTERN, locale)
    return "${weekStart.format(format)} $EN_DASH ${weekEndInclusive.format(format)}"
}

private const val WEEK_RANGE_PATTERN = "MMM d"

/** `–`, written as an escape so it cannot be mistaken for a hyphen in a diff. */
private const val EN_DASH = "–"

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

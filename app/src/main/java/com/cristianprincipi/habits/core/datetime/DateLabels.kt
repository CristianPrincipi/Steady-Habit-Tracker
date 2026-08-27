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

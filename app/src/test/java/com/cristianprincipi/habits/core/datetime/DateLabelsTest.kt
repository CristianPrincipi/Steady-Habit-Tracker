package com.cristianprincipi.habits.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * The labels above the pills, and what a screen reader hears.
 *
 * The failure these guard against is a header one column out of step with the days beneath it —
 * which looks like a data bug and is a labelling bug.
 */
class DateLabelsTest {
    @Test
    fun thereAreSevenLabels() {
        assertEquals(DAYS_PER_WEEK, weekdayLabels(Locale.UK).size)
    }

    @Test
    fun theyStartOnTheWeekStartRatherThanOnSunday() {
        // ⭐ The one that matters. `Locale.US` conventionally starts a week on Sunday, and a
        // labelling helper that deferred to the locale would silently disagree with `weekWindow` —
        // which starts at `WeekStart` regardless. The grid is Monday-based (`design-spec.md` §3),
        // so the labels must be too, in every locale.
        val labels = weekdayLabels(Locale.US)

        assertEquals(DayOfWeek.MONDAY.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US), labels.first())
    }

    @Test
    fun theLabelsAreTheEnglishAbbreviationsTheDesignShows() {
        // `docs/design-spec.md` §3 draws "Mon…Sun", and `WeekPills`' own preview hardcodes exactly
        // these. If the platform's SHORT style ever stopped matching, the preview and the app would
        // quietly diverge.
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            weekdayLabels(Locale.UK),
        )
    }

    @Test
    fun everyLabelIsDistinct() {
        // A locale whose SHORT names collide would put two identical headers over different days.
        assertEquals(DAYS_PER_WEEK, weekdayLabels(Locale.UK).toSet().size)
    }

    @Test
    fun aSpokenDateNamesTheDayTheMonthAndTheYear() {
        // Unambiguous rather than brief: this is only ever a contentDescription, and a pill in a
        // week the user has paged away from should not announce a bare "24 August".
        val spoken = accessibleDate(LocalDate.of(2026, 8, 24), Locale.UK)

        assertTrue("expected the weekday in $spoken", spoken.contains("Monday"))
        assertTrue("expected the month in $spoken", spoken.contains("August"))
        assertTrue("expected the year in $spoken", spoken.contains("2026"))
    }

    @Test
    fun aSpokenDateFollowsTheLocale() {
        val uk = accessibleDate(LocalDate.of(2026, 8, 24), Locale.UK)
        val france = accessibleDate(LocalDate.of(2026, 8, 24), Locale.FRANCE)

        assertTrue("expected a French weekday in $france", france.contains("lundi"))
        assertEquals(false, uk == france)
    }
}

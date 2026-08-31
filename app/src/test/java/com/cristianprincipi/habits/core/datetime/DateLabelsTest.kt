package com.cristianprincipi.habits.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
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

    // --- the sub-nav label (M6.4) --------------------------------------------------------------

    @Test
    fun aWeekRangeReadsAsTheDesignDraws() {
        // `docs/design-spec.md` §3 draws exactly this.
        val label = weekRangeLabel(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23), Locale.UK, THIS_YEAR)

        assertEquals("Aug 17 – Aug 23", label)
    }

    @Test
    fun theSeparatorIsAnEnDashRatherThanAHyphen() {
        // It is a range. The two characters are near-identical in a diff, which is why the constant
        // is written as an escape and why this is asserted rather than eyeballed.
        val label = weekRangeLabel(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23), Locale.UK, THIS_YEAR)

        assertTrue("expected an en dash in $label", label.contains('–'))
        assertTrue("expected no hyphen in $label", !label.contains('-'))
    }

    @Test
    fun aWeekCrossingAMonthNamesBothMonths() {
        // The case a naive "MMM d – d" would get wrong, and the reason both ends are formatted.
        //
        // ⚠ **"Sept", not "Sep"** — and that is the platform being right, not a typo. CLDR's
        // British English abbreviates September to four letters where American English uses three,
        // so `Locale.US` would render "Sep 6" here. It is a small demonstration of why these names
        // come from `java.time` rather than from seven `<string>`s somebody maintains by hand.
        val label = weekRangeLabel(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6), Locale.UK, THIS_YEAR)

        assertEquals("Aug 31 – Sept 6", label)
    }

    @Test
    fun theMonthAbbreviationItselfFollowsTheLocale() {
        val british = weekRangeLabel(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), Locale.UK, THIS_YEAR)
        val american = weekRangeLabel(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), Locale.US, THIS_YEAR)

        assertTrue("expected the British four-letter form in $british", british.contains("Sept"))
        assertTrue("expected the American three-letter form in $american", american.contains("Sep 1"))
    }

    @Test
    fun theYearIsAbsentForAWeekInsideTheCurrentOne() {
        // The common case, and the design's format: the label you see every day is unchanged by
        // M6.11. Anything else would put a year on the landing screen 51 weeks out of 52.
        val label = weekRangeLabel(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23), Locale.UK, THIS_YEAR)

        assertTrue("expected no year in $label", !label.contains("2026"))
    }

    @Test
    fun aWeekCrossingIntoTheNextYearNamesOnlyTheEndThatLeavesIt() {
        // ⚠ The reason the decision is per endpoint rather than per range. December is still this
        // year and says nothing; January is not, and says which January it is. A range-level test
        // would have had to pick one year for both ends and be wrong about the other.
        val label = weekRangeLabel(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3), Locale.UK, THIS_YEAR)

        assertEquals("Dec 28 – Jan 3, 2027", label)
    }

    @Test
    fun aWeekWhollyInAnotherYearNamesItAtBothEnds() {
        // Page back far enough and "Aug 17 – Aug 23" could be any year — the ambiguity M6.11 was
        // asked about. Both ends carry it because both are equally in the wrong year.
        val label = weekRangeLabel(LocalDate.of(2024, 8, 19), LocalDate.of(2024, 8, 25), Locale.UK, THIS_YEAR)

        assertEquals("Aug 19, 2024 – Aug 25, 2024", label)
    }

    @Test
    fun theYearIsTheYearOfEraRatherThanTheWeekBasedOne() {
        // ⚠ `yyyy` not `YYYY`. The week-based year rolls at the ISO week boundary, so a `YYYY`
        // pattern would print 2027 for a 31 December that belongs to 2026 — wrong on exactly the
        // one week of the year this feature exists to disambiguate.
        val label = weekRangeLabel(LocalDate.of(2024, 12, 30), LocalDate.of(2025, 1, 5), Locale.UK, THIS_YEAR)

        assertEquals("Dec 30, 2024 – Jan 5, 2025", label)
    }

    @Test
    fun theMonthNameFollowsTheLocale() {
        val french = weekRangeLabel(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23), Locale.FRANCE, THIS_YEAR)

        assertTrue("expected a French month in $french", french.contains("août"))
    }

    // --- month labels (M8.1) -------------------------------------------------------------------

    @Test
    fun aMonthLabelReadsAsTheDesignDraws() {
        assertEquals("August 2026", monthLabel(YearMonth.of(2026, 8), Locale.UK))
    }

    @Test
    fun theYearIsAlwaysPrintedOnAMonthLabelEvenForTheCurrentOne() {
        // ⭐ The one place this deliberately differs from weekRangeLabel, which drops the current
        // year. Twelve steps back from August lands on another August, and a label that hid the
        // year would make the arrow that got there look broken.
        val here = monthLabel(YearMonth.of(THIS_YEAR, 8), Locale.UK)
        val aYearBack = monthLabel(YearMonth.of(THIS_YEAR - 1, 8), Locale.UK)

        assertTrue("expected the current year in $here", here.contains(THIS_YEAR.toString()))
        assertEquals(false, here == aYearBack)
    }

    @Test
    fun aMonthLabelFollowsTheLocale() {
        val uk = monthLabel(YearMonth.of(2026, 8), Locale.UK)
        val france = monthLabel(YearMonth.of(2026, 8), Locale.FRANCE)

        assertTrue("expected a French month name in $france", france.contains("août"))
        assertEquals(false, uk == france)
    }

    @Test
    fun aMonthStandsOnItsOwnRatherThanInsideADate() {
        // ⚠ LLLL, not MMMM. English cannot tell the two apart, and Czech can: a heading wants the
        // nominative "leden", while the form used inside a date is "ledna". A month name used as a
        // page title in the genitive is a bug that only ever appears on someone else's phone.
        val czech = Locale.forLanguageTag("cs")

        assertEquals("leden 2026", monthLabel(YearMonth.of(2026, 1), czech))
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

/**
 * The year the suite treats as "now" — `SampleHabits.referenceDate` is in it, and so is every date
 * these tests call ordinary.
 *
 * Passed in rather than read from a clock because `weekRangeLabel` takes it as a parameter, which is
 * the whole point: `CLAUDE.md` §6 keeps `now` out of everything below the ViewModel, so the one
 * place that knows what year it is stays the one place a test has to control.
 */
private const val THIS_YEAR = 2026

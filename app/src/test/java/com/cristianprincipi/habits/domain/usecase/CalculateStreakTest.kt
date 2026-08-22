package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.Streak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The streak engine, tested against the cases `Plan.md` M3.10 names and a few it implies.
 *
 * A wrong streak is not a crash — it is a believable number in a stat tile that nobody questions —
 * so the coverage here is deliberately heavier than the size of the code suggests.
 *
 * **No non-scheduled-day or N-per-week cases.** Schedules are deferred post-v1, every habit is
 * daily, and any missed day breaks the run (`CLAUDE.md` §1). Do not add one back without the
 * schedule model that would justify it.
 */
class CalculateStreakTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    /** A clock reading midday on [today], so no test depends on the hour unless it says so. */
    private fun streakOn(
        today: LocalDate,
        completed: List<LocalDate>,
        zone: ZoneId = ZoneOffset.UTC,
    ): Streak = CalculateStreak(Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone))(completed)

    private fun runEndingOn(
        last: LocalDate,
        days: Int,
    ): List<LocalDate> = (0 until days).map { last.minusDays(it.toLong()) }

    @Test
    fun noCompletionsAtAllIsNoStreak() {
        assertEquals(Streak.None, streakOn(LocalDate.of(2026, 8, 22), completed = emptyList()))
    }

    @Test
    fun oneCompletionTodayIsAStreakOfOne() {
        val today = LocalDate.of(2026, 8, 22)

        assertEquals(Streak(current = 1, best = 1), streakOn(today, listOf(today)))
    }

    @Test
    fun todayNotDoneYetDoesNotBreakTheRun() {
        // The single most important case here. Counting from today would reset every user's streak
        // to 0 each midnight and only restore it once they had checked in — a habit kept for a year
        // would look abandoned before breakfast.
        val today = LocalDate.of(2026, 8, 22)

        val streak = streakOn(today, runEndingOn(today.minusDays(1), days = 5))

        assertEquals(Streak(current = 5, best = 5), streak)
    }

    @Test
    fun twoDaysWithoutCheckingInDoesBreakIt() {
        // Today is forgiven; yesterday is not. This is the boundary of that grace.
        val today = LocalDate.of(2026, 8, 22)

        val streak = streakOn(today, runEndingOn(today.minusDays(2), days = 5))

        assertEquals(Streak(current = 0, best = 5), streak)
    }

    @Test
    fun anUnbrokenRunCountsEveryDayOfIt() {
        val today = LocalDate.of(2026, 8, 22)

        assertEquals(Streak(current = 30, best = 30), streakOn(today, runEndingOn(today, days = 30)))
    }

    @Test
    fun aMissedDayInTheMiddleEndsTheCurrentRunThere() {
        val today = LocalDate.of(2026, 8, 22)
        val recent = runEndingOn(today, days = 3)
        val older = runEndingOn(today.minusDays(4), days = 7)

        val streak = streakOn(today, recent + older)

        assertEquals(Streak(current = 3, best = 7), streak)
    }

    @Test
    fun theBestStreakIsTheLongestRunAnywhereInTheHistory() {
        val today = LocalDate.of(2026, 8, 22)
        val recent = runEndingOn(today, days = 2)
        val ancient = runEndingOn(LocalDate.of(2024, 5, 1), days = 40)

        val streak = streakOn(today, recent + ancient)

        assertEquals(Streak(current = 2, best = 40), streak)
    }

    @Test
    fun aRunLongerThanAYearKeepsBestAtLeastAsLongAsCurrent() {
        // The prototype's bug, and the reason both figures are measured over the whole history:
        // it bounds the best streak to the trailing 365 days while letting the current run go back
        // forever, which for a 400-day habit reports current=400, best=365. Streak's own invariant
        // rejects that pair, so this would have been a crash rather than a wrong number.
        val today = LocalDate.of(2026, 8, 22)

        val streak = streakOn(today, runEndingOn(today, days = 400))

        assertEquals(Streak(current = 400, best = 400), streak)
    }

    @Test
    fun completionsDatedInTheFutureAreIgnored() {
        // Nothing in the app can create one — ToggleCompletion refuses a future date — but a sync
        // pulling from a device whose clock ran ahead can, and it would otherwise inflate best.
        val today = LocalDate.of(2026, 8, 22)
        val tomorrowAndBeyond = listOf(today.plusDays(1), today.plusDays(2), today.plusDays(3))

        val streak = streakOn(today, listOf(today) + tomorrowAndBeyond)

        assertEquals(Streak(current = 1, best = 1), streak)
    }

    @Test
    fun duplicateCompletionsForOneDayCountOnce() {
        val today = LocalDate.of(2026, 8, 22)

        val streak = streakOn(today, listOf(today, today, today.minusDays(1)))

        assertEquals(Streak(current = 2, best = 2), streak)
    }

    @Test
    fun aLeapDayIsJustAnotherDayInTheRun() {
        // 2028 is a leap year, so 29 February exists and sits between the 28th and 1 March.
        val today = LocalDate.of(2028, 3, 1)
        val across = listOf(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29), today)

        assertEquals(Streak(current = 3, best = 3), streakOn(today, across))
    }

    @Test
    fun aNonLeapYearHasNoGapWhereTheLeapDayWouldBe() {
        // The mirror of the case above: in 2027 the 28th and 1 March are consecutive, and a run
        // across them must not be treated as broken by a day that does not exist.
        val today = LocalDate.of(2027, 3, 1)
        val across = listOf(LocalDate.of(2027, 2, 28), today)

        assertEquals(Streak(current = 2, best = 2), streakOn(today, across))
    }

    @Test
    fun aDaylightSavingChangeDoesNotSkipOrRepeatADay() {
        // Madrid springs forward on 29 March 2026: that day is 23 hours long. A streak measured in
        // elapsed time rather than calendar days would lose it.
        val today = LocalDate.of(2026, 3, 30)
        val across = listOf(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29), today)

        assertEquals(Streak(current = 3, best = 3), streakOn(today, across, zone = madrid))
    }

    @Test
    fun aCompletionAtElevenAtNightBelongsToThatDay() {
        // CLAUDE.md §6: a habit checked at 23:00 belongs to that day. The clock reads 23:00, and
        // today's completion still counts as today's rather than rolling into tomorrow.
        val today = LocalDate.of(2026, 8, 22)
        val lateEvening = Clock.fixed(today.atTime(23, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val streak = CalculateStreak(lateEvening)(runEndingOn(today, days = 4))

        assertEquals(Streak(current = 4, best = 4), streak)
    }

    @Test
    fun crossingMidnightWithoutCheckingInLeavesTheStreakStanding() {
        // One minute past midnight, the previous day becomes "yesterday" and the grace applies.
        val checkedThrough = LocalDate.of(2026, 8, 22)
        val justAfterMidnight =
            Clock.fixed(LocalDate.of(2026, 8, 23).atTime(0, 1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val streak = CalculateStreak(justAfterMidnight)(runEndingOn(checkedThrough, days = 6))

        assertEquals(Streak(current = 6, best = 6), streak)
    }

    @Test
    fun crossingASecondMidnightFinallyBreaksIt() {
        val checkedThrough = LocalDate.of(2026, 8, 22)
        val twoMidnightsLater =
            Clock.fixed(LocalDate.of(2026, 8, 24).atTime(0, 1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val streak = CalculateStreak(twoMidnightsLater)(runEndingOn(checkedThrough, days = 6))

        assertEquals(Streak(current = 0, best = 6), streak)
    }

    @Test
    fun theSameInstantInTwoTimeZonesCanBeTwoDifferentDays() {
        // 22 August, 23:30 UTC is already the 23rd in Auckland. The engine reads "today" from the
        // clock's zone, so the same completion history gives different answers — which is correct,
        // and is why the injected clock resolves its zone per call (ADR-029).
        val instant = LocalDate.of(2026, 8, 22).atTime(23, 30).toInstant(ZoneOffset.UTC)
        val completed = listOf(LocalDate.of(2026, 8, 21))

        val inUtc = CalculateStreak(Clock.fixed(instant, ZoneOffset.UTC))(completed)
        val inAuckland = CalculateStreak(Clock.fixed(instant, ZoneId.of("Pacific/Auckland")))(completed)

        // UTC: today is the 22nd, so the 21st is yesterday and the run stands.
        assertEquals(Streak(current = 1, best = 1), inUtc)
        // Auckland: today is the 23rd, so the 21st is two days ago and the run has gone.
        assertEquals(Streak(current = 0, best = 1), inAuckland)
    }
}

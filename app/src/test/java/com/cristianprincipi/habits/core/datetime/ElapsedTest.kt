package com.cristianprincipi.habits.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The arithmetic behind "Last synced 3 minutes ago".
 *
 * Worth its own test because every boundary here is a place a caption can say something silly, and
 * because it is the only part of that line that is not a string resource.
 */
class ElapsedTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")

    private fun ago(duration: Duration) = elapsedBetween(now.minus(duration), now)

    @Test
    fun secondsAgoIsJustNow() {
        // No number is worth showing at this resolution, and "0 minutes ago" is not English.
        assertEquals(Elapsed.JustNow, ago(Duration.ofSeconds(0)))
        assertEquals(Elapsed.JustNow, ago(Duration.ofSeconds(59)))
    }

    @Test
    fun oneMinuteIsWhereCountingStarts() {
        assertEquals(Elapsed.Minutes(1), ago(Duration.ofSeconds(60)))
    }

    @Test
    fun minutesRunUpToTheHour() {
        assertEquals(Elapsed.Minutes(59), ago(Duration.ofMinutes(59)))
        assertEquals(Elapsed.Hours(1), ago(Duration.ofMinutes(60)))
    }

    @Test
    fun hoursRunUpToTheDay() {
        assertEquals(Elapsed.Hours(23), ago(Duration.ofHours(23)))
        assertEquals(Elapsed.Days(1), ago(Duration.ofHours(24)))
    }

    @Test
    fun daysDoNotBecomeWeeksOrMonths() {
        // The ladder stops here on purpose: a sync that last worked 90 days ago is a problem the
        // failure banner is already talking about, and "3 months" adds precision to "too long".
        assertEquals(Elapsed.Days(90), ago(Duration.ofDays(90)))
    }

    @Test
    fun everythingIsRoundedDownRatherThanNearest() {
        // 119 minutes is "1 hour ago", not "2 hours ago". Rounding up would let the caption claim
        // more time has passed than actually has, which is the wrong direction for a staleness cue.
        assertEquals(Elapsed.Hours(1), ago(Duration.ofMinutes(119)))
    }

    @Test
    fun aTimestampFromTheFutureIsJustNowRatherThanNegative() {
        // Should be impossible — this device wrote it — but a clock correction or a timezone fix
        // moves the device's clock backwards, and "in -3 minutes" is worse than "just now".
        assertEquals(Elapsed.JustNow, elapsedBetween(now.plus(Duration.ofHours(2)), now))
    }
}

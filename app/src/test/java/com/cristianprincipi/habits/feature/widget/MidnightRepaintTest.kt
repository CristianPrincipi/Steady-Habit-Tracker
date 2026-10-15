package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.testing.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** When the midnight repaint is asked for — M15.11. */
class MidnightRepaintTest {
    private fun clockAt(
        local: LocalDateTime,
        zone: ZoneId,
    ) = MutableClock(local.atZone(zone).toInstant(), zone)

    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun anEveningAsksForTheComingMidnightInTheLocalZone() {
        val clock = clockAt(LocalDateTime.of(2026, 10, 15, 20, 30), rome)

        assertEquals(
            OffsetDateTime.of(2026, 10, 16, 0, 0, 0, 0, ZoneOffset.ofHours(2)).toInstant().toEpochMilli(),
            nextMidnightMillis(clock),
        )
    }

    @Test
    fun aMidnightAlreadyReachedAsksForTheNextOne() {
        // The alarm re-arms from inside its own repaint, at or just after midnight. Asking for
        // "today's" midnight there would fire again at once, for ever.
        val clock = clockAt(LocalDateTime.of(2026, 10, 16, 0, 0), rome)

        assertEquals(
            OffsetDateTime.of(2026, 10, 17, 0, 0, 0, 0, ZoneOffset.ofHours(2)).toInstant().toEpochMilli(),
            nextMidnightMillis(clock),
        )
    }

    @Test
    fun aDayThatStartsAtOneBecauseOfDaylightSavingIsAskedForAtOne() {
        // Brazil moved its clocks at midnight in 2018: 4 November had no 00:00, it began at 01:00.
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val clock = clockAt(LocalDateTime.of(2018, 11, 3, 22, 0), saoPaulo)

        assertEquals(
            OffsetDateTime.of(2018, 11, 4, 1, 0, 0, 0, ZoneOffset.ofHours(-2)).toInstant().toEpochMilli(),
            nextMidnightMillis(clock),
        )
    }
}

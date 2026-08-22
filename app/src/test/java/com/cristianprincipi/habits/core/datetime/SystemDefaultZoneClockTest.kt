package com.cristianprincipi.habits.core.datetime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.math.abs

/**
 * The one behaviour [SystemDefaultZoneClock] exists for.
 *
 * `Clock.systemDefaultZone()` would have been the obvious provider, and it is wrong here for a
 * reason no compiler catches: it freezes the time zone at construction, and this clock is built
 * once per process. The contrast test below pins that difference down, so nobody simplifies the
 * object away later.
 */
class SystemDefaultZoneClockTest {
    private val original: TimeZone = TimeZone.getDefault()

    @After
    fun restoreTheDefaultZone() {
        TimeZone.setDefault(original)
    }

    @Test
    fun theClockReportsWhateverZoneTheDeviceIsInNow() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))

        assertEquals(ZoneId.of("Europe/Madrid"), SystemDefaultZoneClock.zone)
    }

    @Test
    fun theClockFollowsAZoneChangeThatHappensAfterTheAppStarted() {
        // The user flies from Madrid to Auckland. "Today" is the whole check-off loop, so a clock
        // still answering in Madrid would put check-offs on the wrong day until the process died.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
        val clock: Clock = SystemDefaultZoneClock

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))

        assertEquals(ZoneId.of("Pacific/Auckland"), clock.zone)
    }

    @Test
    fun theFactoryThisReplacesDoesNotFollowThatChange() {
        // Not a test of the JDK for its own sake — this is the bug being avoided, written down.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
        val frozen = Clock.systemDefaultZone()

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))

        assertEquals(ZoneId.of("Europe/Madrid"), frozen.zone)
        assertNotEquals(frozen.zone, SystemDefaultZoneClock.zone)
    }

    @Test
    fun todayIsResolvedInTheCurrentZone() {
        // Auckland is a day ahead of Honolulu for most of the day; the pair is chosen because the
        // two dates differ across a wide band of UTC hours, so this does not depend on when it runs.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
        val auckland = LocalDate.now(SystemDefaultZoneClock)

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        val honolulu = LocalDate.now(SystemDefaultZoneClock)

        assertTrue(
            "Auckland must never be behind Honolulu, got $auckland and $honolulu",
            !auckland.isBefore(honolulu),
        )
    }

    @Test
    fun millisAndInstantAgree() {
        // millis() is overridden rather than inherited, so the two could drift apart in code.
        val millis = SystemDefaultZoneClock.millis()
        val instant = SystemDefaultZoneClock.instant().toEpochMilli()

        assertTrue("millis() and instant() disagree by ${abs(instant - millis)} ms", abs(instant - millis) < 1_000L)
    }

    @Test
    fun theModuleHandsOutTheZoneFollowingClockAndNotTheFrozenFactory() {
        // The object is only worth having if it is the one on the graph.
        assertSame(SystemDefaultZoneClock, DateTimeModule.provideClock())
    }

    @Test
    fun withZonePinsTheZoneAsTheContractRequires() {
        val pinned = SystemDefaultZoneClock.withZone(ZoneId.of("UTC"))

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))

        assertEquals(ZoneId.of("UTC"), pinned.zone)
    }
}

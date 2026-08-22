package com.cristianprincipi.habits.core.datetime

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The app's real clock: system time, in whatever time zone the device is in **right now**.
 *
 * `CLAUDE.md` §6 bans `LocalDate.now()` in domain and data code so that tests can control time.
 * ADR-015 settled what the abstraction is: `java.time.Clock` itself, already in the standard
 * library, so the requirement costs no dependency. Tests pass a `Clock.fixed(...)`; the app gets
 * this.
 *
 * **Why not `Clock.systemDefaultZone()`.** That factory resolves `ZoneId.systemDefault()` **once**,
 * at construction, and holds it forever. The clock here is a `@Singleton` built when the process
 * starts, so a user who flies from Madrid to Auckland would keep computing "today" in Madrid until
 * something killed the app — and "today" is the whole check-off loop. Resolving the zone per call
 * costs a `TimeZone.getDefault()` and removes that entire class of bug.
 *
 * DST is not the case this fixes; a region `ZoneId` handles DST either way. An actual change of
 * zone is.
 */
internal object SystemDefaultZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    /**
     * Hands back a fixed-zone clock, as the contract requires.
     *
     * Only callers that explicitly want to pin a zone reach this — `LocalDate.now(clock)` does not.
     */
    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    /** Overridden to skip building an [Instant] that `DayEntity`'s millis column would discard. */
    override fun millis(): Long = System.currentTimeMillis()
}

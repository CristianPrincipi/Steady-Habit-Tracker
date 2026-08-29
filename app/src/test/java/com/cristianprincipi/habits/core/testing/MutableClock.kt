package com.cristianprincipi.habits.core.testing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A [Clock] a test can move.
 *
 * `Clock.fixed` is the right tool almost everywhere in this codebase and is what most tests use: a
 * frozen clock makes "which week is this?" a claim rather than a race. It cannot express the one
 * thing M6.9 is about, though — **a day that changes while the app is running** — so this exists
 * alongside it rather than instead of it.
 *
 * Defaults to UTC because the tests that need it are asserting on dates, not on zones.
 */
class MutableClock(
    private var current: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    /** Moves to the start of [date], in this clock's zone. */
    fun setTo(date: LocalDate) {
        current = date.atStartOfDay(zone).toInstant()
    }
}

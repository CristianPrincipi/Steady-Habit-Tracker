package com.cristianprincipi.habits.domain.model

import java.time.LocalDate

/**
 * One habit, checked off on one day.
 *
 * **A day, not an instant** (`CLAUDE.md \u00a76`). A habit checked at 23:00 belongs to that day whatever
 * the timezone does afterwards, so this carries a [LocalDate] and never a timestamp. Room stores it
 * as an epoch day; the two are the same fact in different units.
 *
 * This lines up exactly with what Notion holds: a page's `Date` property is date-only with a null
 * time zone (`docs/notion-schema.md \u00a72.4`), so the round trip has no timezone hazard in it.
 *
 * There is no "not completed" value. A day is complete if a [Completion] exists for it and
 * incomplete if none does; storing explicit misses would mean writing a row for every habit on
 * every day the user did nothing.
 */
data class Completion(
    val habitId: HabitId,
    val date: LocalDate,
)

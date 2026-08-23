package com.cristianprincipi.habits.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * An admission that last-write-wins threw one of the user's edits away.
 *
 * `CLAUDE.md` §3.5: "Where resolution discards a user's edit, say so in the UI. Never resolve
 * destructively and silently." M4.9 made these survive the background process that produced them;
 * this is the shape the screen that finally says so reads them in.
 *
 * **[habitNames] is resolved late, on purpose.** The stored notice keeps habit *ids*, because names
 * change upstream and a notice written last week should render with the name the habit has today
 * (`DiscardedEdit`'s KDoc, ADR-038). The join happens when the notice is read, not when it is
 * written. A habit whose column has since been deleted has no name left to find, so the list can be
 * shorter than what was discarded — which is why [habitCount] is carried separately rather than
 * derived from it.
 *
 * @param date the day whose check-offs were decided. A local date, like every completion
 *   (`CLAUDE.md` §6) — a habit checked at 23:00 belongs to that day whatever the time zone does.
 * @param resolvedAt when resolution happened. A wall-clock instant, because "2 hours ago" is what
 *   the screen shows.
 */
data class DiscardedEditNotice(
    val date: LocalDate,
    val habitNames: List<String>,
    val habitCount: Int,
    val discardedSide: EditSide,
    val resolvedAt: Instant,
)

/**
 * Which copy of a day was thrown away.
 *
 * **In a single-user app both sides are the same person**, which is exactly why this matters rather
 * than being an implementation detail: "the change you made on your phone was replaced by your later
 * change in Notion" and its opposite are different sentences, and the user is entitled to know which
 * one happened to them.
 */
enum class EditSide {
    /** This device's copy lost. The user's later edit in Notion won. */
    Local,

    /** Notion's copy lost. The user's later edit on this device won. */
    Remote,
}

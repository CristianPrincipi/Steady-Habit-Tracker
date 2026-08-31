package com.cristianprincipi.habits.domain.model

import java.time.LocalDate

/**
 * A habit the user keeps.
 *
 * **Read `docs/notion-schema.md` before changing this class.** A habit is not a row in the user's
 * Notion database — it is a *checkbox property* on a page-per-day, so almost everything the plan
 * originally assumed a habit would carry does not exist upstream:
 *
 * - **No description.** Notion has nowhere to put one and ADR-010 makes the habit set read-only, so
 *   v1 has no source and no editor for it. Modelling a field that is null in every row and
 *   uneditable in every screen would be dead weight in five layers (ADR-027).
 * - **No colour.** [color] is app-local presentation metadata, assigned by us and never pushed.
 * - **No start date.** [startDate] is *inferred* from the earliest day synced from Notion, which is
 *   the closest thing the data has to "when this began". M4 computes it; nothing here does.
 * - **No active flag.** A habit whose property has left the mapped set is *hidden* — the
 *   user's decision, 2026-08-31 (ADR-081) — so it never reaches this class. `HabitEntity.isActive`
 *   and `HabitDao.deactivateAllExcept` still keep the row and its history; nothing above the
 *   repository can see it, which is what makes "invisible" a property of the code rather than a
 *   rule every new screen has to remember.
 *
 * Pure Kotlin, `java.time` only (ADR-015). No Android import and no Compose `Color` reaches this
 * package — [HabitColor] is an enum precisely so that it cannot.
 */
data class Habit(
    val id: HabitId,
    /** The property name with its leading emoji stripped: `"\uD83D\uDCD6 Read"` becomes `"Read"`. */
    val name: String,
    /** The leading emoji of the Notion property name, and the app's icon for this habit. */
    val emoji: String,
    val color: HabitColor,
    /** The earliest day this habit is known to have existed. Inferred, never read from Notion. */
    val startDate: LocalDate,
)

/**
 * A habit's local identity.
 *
 * Opaque on purpose. Notion's identity for a habit is its **property id**, which lives on the Room
 * entity and never reaches the domain (`docs/notion-schema.md §6.1`) — keeping that out of here is
 * what lets the property be renamed upstream without the domain noticing.
 */
@JvmInline
value class HabitId(
    val value: String,
)

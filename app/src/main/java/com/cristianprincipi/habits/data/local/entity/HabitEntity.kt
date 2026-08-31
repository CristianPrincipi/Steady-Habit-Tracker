package com.cristianprincipi.habits.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A habit, as stored.
 *
 * **This table caches the *schema* of the user's Notion database, not its rows.** A habit is a
 * checkbox property (`docs/notion-schema.md` §1), so the habit set is refreshed wholesale from the
 * schema on every sync and never pushed back — ADR-010 forbids writing a schema.
 *
 * That is why there are no sync columns here. Nothing about a habit can be locally ahead of Notion:
 * the only field the user can change is [color], which has no Notion counterpart at all and is never
 * sent anywhere. [DayEntity] carries the sync state, because a day is what Notion stores as a page.
 */
@Entity(
    tableName = "habits",
    indices = [Index(value = ["notionPropertyId"], unique = true)],
)
data class HabitEntity(
    /**
     * A local identifier, generated on first import.
     *
     * **Deliberately not the Notion property id**, even though that would save a column.
     * `docs/notion-schema.md` §6.1 says to key on the property id, but flags "are these stable?" as
     * a question M4.1 must answer against the live API. If the answer disappoints, the matching key
     * changes and [notionPropertyId] changes with it — while this id, and every foreign key that
     * points at it, stays where it was. A wrong guess costs a column; the alternative costs a
     * migration of the whole history.
     */
    @PrimaryKey val id: String,
    /** The identity Notion gives the property. Survives a rename upstream; the name does not. */
    val notionPropertyId: String,
    /**
     * The property name exactly as Notion holds it, emoji and all.
     *
     * Stored verbatim and never normalised. `docs/notion-schema.md` §2.3: one of the five real
     * property names carries a `U+FE0F` variation selector and the others do not, so a helper that
     * "tidies" these strings would make every API call for that property fail. The display name and
     * the emoji are split out of this at the mapper (M3.5), so there is one copy of the truth.
     */
    val notionPropertyName: String,
    /** A `HabitColor` name. App-local, never pushed (`docs/notion-schema.md` §3). */
    val color: String,
    /** Inferred from the earliest day synced from Notion; there is no start date upstream. */
    val startDateEpochDay: Long,
    /**
     * False once the checkbox property has left the mapped set.
     *
     * **History stays, and the habit is hidden** (ADR-081): `HabitDao.observeActive` is the only
     * read the UI has, so a false here removes the habit from every screen without removing a
     * single completion. Set it true again — which a returning property does — and the habit comes
     * back whole, same row, same id, same history.
     */
    val isActive: Boolean,
    /**
     * Display order, assigned at import.
     *
     * The design shows one fixed list of habits and offers no way to reorder it, so this exists to
     * reproduce the order the user already sees in Notion rather than to be edited. How M4 fills it
     * depends on whether the API reports property order; ordering by name is the fallback.
     */
    val sortOrder: Int,
)

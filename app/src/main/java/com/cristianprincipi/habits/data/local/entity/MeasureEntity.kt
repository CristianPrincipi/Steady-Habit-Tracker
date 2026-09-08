package com.cristianprincipi.habits.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A number column the user asked to see averaged — `Weight` and `Sleep` (M15.4).
 *
 * ### ⭐⭐ A table of its own, and that is the whole exclusion mechanism
 *
 * A measure could have been a flag on [HabitEntity]. It is not, because every screen in this app
 * reads habits: the Week pills, the Month and Year dot grids, the streak tiles. A measure sharing
 * that table would have to be filtered out of each of them, by a `where` somebody has to remember
 * to write — and an eleventh screen would forget. Here there is nothing to remember: a measure is
 * not in the collection those screens read, so none of them can draw one.
 *
 * The same argument one layer up keeps measure ids out of `NotionPropertyMapping.habitPropertyIds`.
 *
 * ### It mirrors [HabitEntity] closely, and the two omissions are deliberate
 *
 * **No `startDateEpochDay`.** A habit needs one because its completion rate is
 * `completions ÷ days since start`. An average over the last thirty days needs no such anchor, and
 * a column that existed only to be ignored would invite a future session to compute a streak from
 * it. Streaks are meaningless for a reading and there is nothing here to build one out of.
 *
 * **No sync columns**, for [HabitEntity]'s own reason and more strongly: this caches the *schema*,
 * and a measure is read-only besides (the user's *"read only, just the average"*, M15.4). Nothing
 * about a measure can be locally ahead of Notion, and there is no code path that writes one back.
 */
@Entity(
    tableName = "measures",
    indices = [Index(value = ["notionPropertyId"], unique = true)],
)
data class MeasureEntity(
    /** A local identifier, generated on first import — [HabitEntity.id]'s reasoning exactly. */
    @PrimaryKey val id: String,
    /** The identity Notion gives the property. Survives a rename upstream; the name does not. */
    val notionPropertyId: String,
    /** The property name exactly as Notion holds it, emoji and all. Never normalised (§2.3). */
    val notionPropertyName: String,
    /**
     * What to write after the number — `kg`, `h` — or blank for none.
     *
     * ⚠⚠ **Typed by the user and underivable.** `NotionPropertySchemaDto` carries no number
     * format, and `CLAUDE.md` §3.4 forbids reading "kg" off a column *named* `Weight`.
     * `docs/notion-schema.md` §6 has the real answer from the owner — kg and decimal hours — and
     * nothing in this codebase is allowed to know it.
     *
     * Copied from `NotionPropertyMapping.measures` at import, the way `sortOrder` is copied from
     * `habitPropertyIds`' order: the mapping is the input of record, this is the working copy.
     */
    val unit: String,
    /** A `HabitColor` name — the shared palette (M15.2), not a parallel one. App-local, never pushed. */
    val color: String,
    /** False once the number property has left the mapped set. [HabitEntity.isActive]'s contract. */
    val isActive: Boolean,
    /** Display order, assigned at import from the mapping's order. */
    val sortOrder: Int,
)

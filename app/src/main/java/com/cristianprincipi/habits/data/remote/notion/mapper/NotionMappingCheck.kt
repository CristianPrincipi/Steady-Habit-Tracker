package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionPropertyId

/**
 * What the saved mapping looks like against the schema Notion has **right now**.
 *
 * `CLAUDE.md` §3.4: "Validate the mapping before every sync. If a property was renamed, retyped or
 * deleted in Notion, pause sync and surface a clear, actionable error. Never silently drop data, and
 * never overwrite a real value with a null because a lookup missed."
 *
 * One of those three is no longer a failure. **A rename cannot break us** (ADR-038): the mapping is
 * keyed on the property id, which the reference says "remains constant when the property name
 * changes", so a renamed habit validates cleanly and [Valid] simply carries its new name. Deletion
 * and retyping remain, and both are [Broken].
 */
sealed interface NotionMappingCheck {
    /**
     * Every mapped property is present and still the right type.
     *
     * Carries the **current** names, so the caller does not have to go back to the schema — which is
     * also how a rename reaches `HabitEntity.notionPropertyName` without anything special happening.
     */
    data class Valid(
        val dayKey: MappedProperty,
        /** In the mapping's order, which is the order the user chose and `sortOrder` follows. */
        val habits: List<MappedProperty>,
    ) : NotionMappingCheck

    /**
     * Sync must not run.
     *
     * **Every problem found, not the first.** A user who renamed one column and deleted another
     * should be told both at once; fixing one and being shown the next is how a person concludes
     * the app is broken. [problems] is never empty.
     */
    data class Broken(
        val problems: List<NotionMappingProblem>,
    ) : NotionMappingCheck
}

/**
 * A mapped property, resolved: the id we hold, and the name Notion currently shows for it.
 *
 * [name] is **for display only** — `docs/notion-api.md` §9. Nothing looks a property up by it, and
 * nothing writes with it. It is the emoji-carrying, user-editable string that
 * `docs/notion-schema.md` §2.3 warns must be stored verbatim and never normalised.
 */
data class MappedProperty(
    val id: NotionPropertyId,
    val name: String,
)

/**
 * A reason the mapping no longer describes the user's database.
 *
 * These are rendered to the user (M4.13), so each one carries what a person needs to act: which
 * property, what it is called, and what happened to it. **A deleted property has no name to carry**
 * — it is gone from the schema — so the caller supplies the last known one from
 * `HabitEntity.notionPropertyName`, which is the only place it survives.
 */
sealed interface NotionMappingProblem {
    /**
     * The schema is not from the data source the mapping names.
     *
     * A programming error rather than anything the user did — every call takes the id from the same
     * mapping — but it is one bad argument away, and continuing would validate a mapping against
     * somebody else's columns and conclude they had all been deleted.
     */
    data class WrongDataSource(
        val expected: NotionDataSourceId,
        val actual: NotionDataSourceId,
    ) : NotionMappingProblem

    /** The `Date` property that identifies a day is gone. Nothing can be read or written without it. */
    data class DayKeyMissing(
        val id: NotionPropertyId,
    ) : NotionMappingProblem

    /** The day key is still there but is no longer a date. */
    data class DayKeyRetyped(
        val id: NotionPropertyId,
        val name: String,
        /** Notion's own type string, raw — so a type this build has never heard of still names itself. */
        val actualType: String,
    ) : NotionMappingProblem

    /**
     * A mapped habit's checkbox property no longer exists.
     *
     * In Notion this destroys the column and every value in it (`docs/notion-schema.md` §4.1), so
     * the local history may now be the only copy. Sync pauses; nothing here deletes anything.
     */
    data class HabitMissing(
        val id: NotionPropertyId,
    ) : NotionMappingProblem

    /**
     * A mapped habit is still there but is no longer a checkbox.
     *
     * Writing to it anyway is the "overwrite a real value" case §3.4 names: the property now holds
     * something the user meant, and a `{"checkbox": false}` would replace it.
     */
    data class HabitRetyped(
        val id: NotionPropertyId,
        val name: String,
        val actualType: String,
    ) : NotionMappingProblem
}

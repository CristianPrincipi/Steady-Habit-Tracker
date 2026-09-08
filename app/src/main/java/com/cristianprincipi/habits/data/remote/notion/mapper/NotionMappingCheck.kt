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
        /**
         * The mapped number columns, in the mapping's order (M15.4). Empty is the normal state.
         *
         * Defaulted so that every existing construction site — and every test that asserts on a
         * habits-only mapping — still says what it meant before measures existed.
         */
        val measures: List<MappedMeasure> = emptyList(),
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
 * A validated measure: the resolved property, plus the unit the user typed for it (M15.4).
 *
 * Composed of a [MappedProperty] rather than repeating its two fields, so the "[name] is for display
 * only, nothing looks a property up by it" contract above is stated once and inherited here.
 */
data class MappedMeasure(
    val property: MappedProperty,
    val unit: String,
) {
    val id: NotionPropertyId get() = property.id

    /** The column's **current** name in Notion — which is what a measure tile is labelled with. */
    val name: String get() = property.name
}

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

    /**
     * A mapped measure's number property no longer exists (M15.4).
     *
     * ⚠⚠ **Separate from [HabitMissing] rather than folded into it, and the reason is the
     * sentence the user reads.** Both say "a column you mapped is gone", but the advice differs:
     * a missing habit may leave this device holding the only copy of a year of history
     * (`docs/notion-schema.md` §4.1), whereas a missing measure loses a column of numbers Notion
     * itself has already destroyed and the app only ever mirrored. Telling a user their history
     * might be at risk when it is not is how a screen stops being believed.
     *
     * Sync pauses either way. §3.4: never silently average nothing.
     */
    data class MeasureMissing(
        val id: NotionPropertyId,
    ) : NotionMappingProblem

    /**
     * A mapped measure is still there but is no longer a number.
     *
     * ⚠ **This one is not the "overwrite a real value" case [HabitRetyped] is** — nothing writes
     * to a measure, so there is nothing to overwrite. It is the *read* half of §3.4: a column
     * retyped from `number` to `select` would decode as `Unreadable` on every page, and the average
     * would quietly become "no readings" rather than saying the mapping is wrong.
     */
    data class MeasureRetyped(
        val id: NotionPropertyId,
        val name: String,
        val actualType: String,
    ) : NotionMappingProblem
}

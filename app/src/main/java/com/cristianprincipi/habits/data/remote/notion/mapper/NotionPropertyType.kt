package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto

/**
 * Every property type a Notion data source can hold, and whether the API will let you write it.
 *
 * The list is transcribed from the live property-object reference, read 2026-08-22 in M4.6 — not
 * remembered (`CLAUDE.md` §3.2). It covers the whole documented set rather than only the six types
 * `docs/notion-schema.md` §2 says this database has today, because the alternative is that the user
 * adding an ordinary `select` column makes the mapping screen show "unknown" for it.
 *
 * **[isReadOnly] is a claim about the docs, not a guess.** It is `true` only for the types the
 * reference states cannot be updated through the API. Everything else is `false`, which means "not
 * documented as read-only" — a weaker statement than "we have written it and it worked". That is
 * enough for the job it has: `CLAUDE.md` §3.2 requires the mapping UI to *prevent* a read-only
 * property being chosen as a write target "rather than failing at sync time", and preventing needs
 * only the exclusions to be right.
 *
 * What v1 actually writes is narrower still, and is enforced somewhere else entirely — see
 * [NotionPropertyValue.Writable], which only [NotionPropertyValue.Checkbox] and
 * [NotionPropertyValue.DateValue] implement.
 */
enum class NotionPropertyType(
    val wireName: String,
    val isReadOnly: Boolean,
) {
    /** ⭐ A habit. `docs/notion-schema.md` §1: habits are checkbox *columns*, not rows. */
    Checkbox("checkbox", isReadOnly = false),

    /** ⭐ The day key. `docs/notion-schema.md` §2: date-only, no time component, no time zone. */
    Date("date", isReadOnly = false),

    /** `Progress`. Read-only: "the value can't be updated through the API". */
    Formula("formula", isReadOnly = true),

    /** `Area`. The API returns an array even when one option is selected. */
    MultiSelect("multi_select", isReadOnly = false),

    /** `Weight` and `Sleep`. A bare JSON number — never the formatted display string. */
    Number("number", isReadOnly = false),

    /** Every page has exactly one, and in this database it carries no data. */
    Title("title", isReadOnly = false),

    RichText("rich_text", isReadOnly = false),
    Select("select", isReadOnly = false),
    Status("status", isReadOnly = false),
    Url("url", isReadOnly = false),
    Email("email", isReadOnly = false),
    PhoneNumber("phone_number", isReadOnly = false),
    Files("files", isReadOnly = false),
    People("people", isReadOnly = false),
    Place("place", isReadOnly = false),
    Relation("relation", isReadOnly = false),

    /** "The API does not support updating `rollup` page property values." */
    Rollup("rollup", isReadOnly = true),

    CreatedBy("created_by", isReadOnly = true),
    CreatedTime("created_time", isReadOnly = true),
    LastEditedBy("last_edited_by", isReadOnly = true),
    LastEditedTime("last_edited_time", isReadOnly = true),

    /** Auto-incrementing, and "cannot be updated with the API". */
    UniqueId("unique_id", isReadOnly = true),
    ;

    companion object {
        private val BY_WIRE_NAME = entries.associateBy(NotionPropertyType::wireName)

        /**
         * @return the type, or `null` for a wire name this build does not know.
         *
         * **Null is a normal answer, not a failure.** Notion adds property types, and
         * `NotionPropertySchemaDto.type` keeps the raw string precisely so an unrecognised one can
         * still be shown to the user and skipped, instead of breaking the schema read.
         */
        fun fromWire(wireName: String): NotionPropertyType? = BY_WIRE_NAME[wireName]
    }
}

/**
 * The schema entry's [NotionPropertyType], or `null` if this build does not know the type.
 *
 * The DTO keeps `type` as the raw wire string on purpose (`CLAUDE.md` §6: an unknown value must
 * never break a sync), so this is where interpretation happens and where "unknown" stays a value
 * the mapping screen can render rather than a parse failure.
 */
val NotionPropertySchemaDto.propertyType: NotionPropertyType?
    get() = NotionPropertyType.fromWire(type)

package com.cristianprincipi.habits.data.remote.notion.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A data source — **where the schema lives**, and therefore where the habits live (ADR-037).
 *
 * `docs/notion-api.md` §1: a database now *contains* data sources, and the properties belong to the
 * data source rather than to the database. What this app persists alongside the user's mapping is
 * this object's [id].
 *
 * Note it has a `title` (a rich-text array) and **no `name`** — unlike the `{id, name}` stubs a
 * database lists in [NotionDatabaseDto.dataSources]. Confirmed against the live reference in M4.5.
 */
@Serializable
data class NotionDataSourceDto(
    val id: String,
    val title: List<NotionRichTextDto> = emptyList(),
    val properties: Map<String, NotionPropertySchemaDto> = emptyMap(),
) {
    /** The title as one string. Rich text is an array of runs; Notion splits on formatting. */
    val displayTitle: String
        get() = title.joinToString(separator = "") { run -> run.plainText }
}

/**
 * One property in the schema: the definition, not a value.
 *
 * The map key that holds this is the property **name**, and [name] repeats it — which is exactly
 * what makes the id/name distinction easy to get wrong (`notion-api.md` §8). [id] is the stable one
 * and the one the mapping is keyed on (ADR-038).
 *
 * [type] stays a `String` rather than an enum: `CLAUDE.md` §6's "an unknown key must never break a
 * sync" applies equally to an unknown *value*, and Notion adds property types. M4.6 turns the
 * handful this app understands into a closed set and leaves the rest unmapped instead of unparsed.
 */
@Serializable
data class NotionPropertySchemaDto(
    val id: String,
    val name: String,
    val type: String,
)

/**
 * One run of rich text, reduced to the only field this app needs.
 *
 * `plain_text` is Notion's own already-flattened rendering of the run, so nothing here has to
 * reimplement mentions, equations or annotations to read a title.
 */
@Serializable
data class NotionRichTextDto(
    @SerialName("plain_text") val plainText: String = "",
)

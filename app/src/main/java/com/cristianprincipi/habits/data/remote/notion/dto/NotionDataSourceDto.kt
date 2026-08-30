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
    val parent: NotionParentDto? = null,
) {
    /** The title as one string. Rich text is an array of runs; Notion splits on formatting. */
    val displayTitle: String
        get() = title.joinToString(separator = "") { run -> run.plainText }

    /**
     * The database this data source belongs to — **the id a Notion URL is built from**.
     *
     * ⭐ **It arrives on every sync and, until M7.5, nothing read it.** `SyncEngine` fetches this
     * object before every run to validate the mapping, so learning where the database is costs no
     * request at all. `NotionPropertyMapping`'s KDoc struck the database id for having "no reader";
     * the manage-habits screen's "Open in Notion" is that reader.
     *
     * `null` for an externally synced source, whose parent is a `data_source_id` rather than a
     * database — [NotionParentDto] keeps those apart rather than reading whichever field is set.
     */
    val databaseId: String?
        get() = parent?.takeIf { it.type == DATABASE_PARENT }?.databaseId
}

/**
 * The `parent.type` that means "a database contains this".
 *
 * ⚠ **A file-level constant rather than a private companion**, which is not a style choice:
 * `@Serializable` generates a `Companion.serializer()`, and declaring the companion `private` makes
 * that inaccessible to every caller — `KtorNotionApi` stops compiling.
 */
private const val DATABASE_PARENT = "database_id"

/**
 * What an object hangs off, as the type-tagged pair Notion sends.
 *
 * Confirmed against the live reference on 2026-08-30 (`CLAUDE.md` §3.2, `docs/notion-api.md` §12):
 * a data source's `parent` is `{"type": "database_id", "database_id": "..."}`, and the discriminator
 * names the field that carries the id. **Both are read**, because Notion documents a second shape
 * for externally synced sources (`data_source_id`) and taking `database_id` on faith would hand a
 * URL builder a null it cannot see.
 *
 * ⚠ Not to be confused with the sibling `database_parent`, which is the parent of the *database*
 * — a page, usually — and is a level further out than anything here wants.
 */
@Serializable
data class NotionParentDto(
    val type: String = "",
    @SerialName("database_id") val databaseId: String? = null,
)

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

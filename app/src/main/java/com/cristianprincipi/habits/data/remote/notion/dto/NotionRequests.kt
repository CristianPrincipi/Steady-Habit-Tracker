package com.cristianprincipi.habits.data.remote.notion.dto

import com.cristianprincipi.habits.data.remote.notion.NotionApiSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The request bodies, all in one file because each is a handful of lines and they are only ever
// read next to each other.
//
// They are `internal`, and the response DTOs are not. A response type is what the client hands
// back; a request type is an implementation detail of how it asks. Nothing outside this package
// should be able to construct one, because constructing one is how a caller invents a query that
// the throttle and the retry policy never see.

/**
 * `POST /v1/search`, filtered to data sources.
 *
 * This is how the setup flow finds what the token can actually see — and, by returning nothing at
 * all, how it discovers that the user created a token but never shared the database with it
 * (`CLAUDE.md` §3.3).
 */
@Serializable
internal data class SearchRequest(
    val query: String? = null,
    val filter: SearchFilter = SearchFilter(),
    @SerialName("page_size") val pageSize: Int = NotionApiSpec.MAX_PAGE_SIZE,
    @SerialName("start_cursor") val startCursor: String? = null,
)

/** `docs/notion-api.md` §4: `value` accepts `"page"` or `"data_source"`. We only ever want the latter. */
@Serializable
internal data class SearchFilter(
    val property: String = "object",
    val value: String = "data_source",
)

/**
 * `POST /v1/data_sources/{id}/query`.
 *
 * With [filter] left null this asks for the whole data source, which is the first-run import. With
 * it set, it is the incremental pull `CLAUDE.md` §3.5 requires — "never re-fetch the entire database
 * on every run".
 */
@Serializable
internal data class QueryDataSourceRequest(
    val filter: LastEditedTimeFilter? = null,
    /**
     * ⭐ **Always sorted by `last_edited_time` ascending, and this is a correctness requirement.**
     *
     * `docs/notion-api.md` §14: *"Notion doesn't guarantee any particular sort order when no sort
     * parameters are provided."* The pull's resume point is `MAX(remoteLastEditedAtEpochMillis)` over
     * the days already stored, so with results in arbitrary order a run killed halfway through
     * pagination advances that watermark past pages it never applied — and the next run, asking for
     * everything edited *after* the watermark, never sees them again. Silent, permanent, and
     * invisible in any test that returns one page.
     *
     * Ascending edit order makes every prefix of the result set a valid stopping point, which is
     * what `CLAUDE.md` §3.5's "idempotent and resumable — it *will* be killed mid-run" actually
     * requires. It is equally what saves the 10,000-result ceiling (§5): a truncated set is then a
     * prefix too, and the next run continues from where it stopped.
     */
    val sorts: List<TimestampSort> = listOf(TimestampSort()),
    @SerialName("start_cursor") val startCursor: String? = null,
    @SerialName("page_size") val pageSize: Int = NotionApiSpec.MAX_PAGE_SIZE,
)

/**
 * A **timestamp** sort, not a property sort.
 *
 * The distinction matters: a property sort names a column in the user's schema, which
 * `CLAUDE.md` §3.4 forbids depending on. This sorts on the page's own `last_edited_time`, which
 * every page has whatever the schema looks like — the same reason `LastEditedTimeFilter` works on
 * a database with no last-edited property. Shape confirmed live in M4.8 (`docs/notion-api.md` §14).
 */
@Serializable
internal data class TimestampSort(
    val timestamp: String = "last_edited_time",
    val direction: String = "ascending",
)

/**
 * The timestamp filter from `docs/notion-api.md` §7 — "works whether or not the data source has a
 * matching property", which matters because this database has no last-edited *property*.
 *
 * [timestamp] repeats the field name by design; that is the shape the reference gives.
 */
@Serializable
internal data class LastEditedTimeFilter(
    @SerialName("last_edited_time") val condition: TimestampCondition,
    val timestamp: String = "last_edited_time",
)

/**
 * `after`, exclusive, compared at **millisecond precision** when the string carries a time.
 *
 * The string must always carry an explicit offset. §7: "if no timezone is provided, then the
 * timezone defaults to UTC" — true today, and exactly the kind of default that silently shifts a
 * pull window by hours if it ever changes. [com.cristianprincipi.habits.data.remote.notion.KtorNotionApi]
 * formats it from a `java.time.Instant`, which is UTC with a `Z` on it by construction.
 */
@Serializable
internal data class TimestampCondition(
    val after: String,
)

/**
 * `POST /v1/pages` — creating the page for a day that has none yet.
 *
 * `docs/notion-schema.md` §2.5: this is a normal part of the write path, not an edge case, because
 * a page *is* a day. It must stay idempotent — two rapid check-offs on a fresh day must not create
 * two pages for one date — which is the sync engine's job (M4.8), not this type's.
 */
@Serializable
internal data class CreatePageRequest(
    val parent: DataSourceParent,
    val properties: Map<String, JsonElement>,
)

/** `docs/notion-api.md` §4: a created page parents to the **data source**, not to the database. */
@Serializable
internal data class DataSourceParent(
    @SerialName("data_source_id") val dataSourceId: String,
    val type: String = "data_source_id",
)

/**
 * `PATCH /v1/pages/{id}` — a **partial** update, and that is the whole point.
 *
 * `docs/notion-schema.md` §7: `Weight`, `Sleep` and `Area` are the user's data that this app does
 * not model, and "the sync engine must read pages without touching them, and must never null them
 * out on write". Sending only the properties we own is what guarantees that; the JSON config's
 * `explicitNulls = false` is the second belt.
 */
@Serializable
internal data class UpdatePageRequest(
    val properties: Map<String, JsonElement>,
)

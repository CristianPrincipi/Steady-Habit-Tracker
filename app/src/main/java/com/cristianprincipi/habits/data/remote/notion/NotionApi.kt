package com.cristianprincipi.habits.data.remote.notion

import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDatabaseDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionListDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Everything this app can ask of Notion. **The only network surface in the codebase.**
 *
 * `CLAUDE.md` §6: "`data/remote/notion` is the only package allowed to touch the network. Nothing
 * else constructs an HTTP call — not a ViewModel, not a use case, not a composable." This interface
 * is what that rule is enforced against; if a call is not here, the app cannot make it.
 *
 * **An interface so the sync engine can be tested with the network faked** (`CLAUDE.md` §6 again,
 * and M4.14's fake). It exposes DTOs rather than domain models on purpose — mapping happens above
 * it, driven by the user's saved property mapping, and a client that mapped would need to know
 * about habits.
 *
 * **What is deliberately missing: archiving.** ADR-036 established that v1 deletes nothing, so there
 * is no method that can. `docs/notion-api.md` §10 leaves `in_trash` vs `is_archived` unresolved for
 * *requests* for exactly that reason — nothing needs the answer.
 *
 * Every method returns a [NotionResult] and none of them throws. Every one goes through the shared
 * throttle and retry policy in `core/network`.
 */
interface NotionApi {
    /**
     * The data sources this token can see — the first thing the setup flow asks, and the answer to
     * "is the database actually shared with the connection?".
     *
     * Note it searches for **data sources, not databases** (ADR-037): the result is already the id
     * the rest of this interface wants, so the common path needs no [retrieveDatabase] call at all.
     *
     * **An empty result is the diagnosis, not an error.** A valid token that has been shared with
     * nothing returns zero results rather than a 404, and `CLAUDE.md` §3.3 says the user must be
     * told to go and share the database instead of being shown "nothing found".
     */
    suspend fun searchDataSources(
        query: String? = null,
        startCursor: String? = null,
    ): NotionResult<NotionListDto<NotionDataSourceDto>>

    /**
     * Resolves a **database** id — what a Notion URL contains, and the only id a user can copy — to
     * the data sources inside it.
     *
     * The fallback path for setup: needed when the user pastes a link rather than picking from
     * [searchDataSources]. A single-source database yields exactly one entry, which is what
     * `docs/notion-api.md` §1 means by "one extra call, not a new concept for the user".
     */
    suspend fun retrieveDatabase(databaseId: String): NotionResult<NotionDatabaseDto>

    /**
     * The schema — **which, in this database, is the habit list** (`docs/notion-schema.md` §1).
     *
     * Habits are checkbox *properties*, so this call is how the app learns what habits exist. It is
     * also what `CLAUDE.md` §3.4's "validate the mapping before every sync" runs against: a property
     * that has been deleted or retyped shows up here as a missing or changed id.
     */
    suspend fun retrieveDataSource(dataSourceId: String): NotionResult<NotionDataSourceDto>

    /**
     * One page of days, newest-edited first is **not** guaranteed — no sort is sent, so treat the
     * order as arbitrary.
     *
     * @param editedAtOrAfter when set, only pages edited at **or after** this instant — inclusive,
     *   because Notion rounds `last_edited_time` down to the minute (`notion-api.md` §7a) and an
     *   exclusive bound would drop for ever any edit sharing the resume point's minute. This is the
     *   incremental pull `CLAUDE.md` §3.5 requires; the value comes from `DayDao.latestRemoteEditMillis`.
     *   When null, the whole data source — the first-run import, and nothing else.
     * @param startCursor [NotionListDto.cursorForNextPage] from the previous page, or null to start.
     *
     * The caller must keep asking while `cursorForNextPage` is non-null, **and** must check
     * [NotionListDto.isTruncated] — `has_more` alone lies at the 10,000-result ceiling
     * (`docs/notion-api.md` §5).
     */
    suspend fun queryPages(
        dataSourceId: String,
        editedAtOrAfter: Instant? = null,
        startCursor: String? = null,
    ): NotionResult<NotionListDto<NotionPageDto>>

    /**
     * Creates the page for a day that has none. `docs/notion-schema.md` §2.5: a normal part of the
     * write path, because a page *is* a day.
     *
     * @param properties keyed by property **id** (ADR-038), values in Notion's wire shape. Only the
     *   properties passed are set; the date key is one of them, and this app owns no others.
     */
    suspend fun createPage(
        dataSourceId: String,
        properties: Map<String, JsonElement>,
    ): NotionResult<NotionPageDto>

    /**
     * Pushes check-offs: a **partial** update of one day's page.
     *
     * @param properties keyed by property **id** (ADR-038). Whatever is absent is left exactly as it
     *   was — which is how `Weight`, `Sleep` and `Area` survive a sync untouched, as
     *   `docs/notion-schema.md` §7 requires.
     *
     * **Keying writes by id rests on one line of the reference** ("`id` may be used in place of name
     * when creating or updating pages") that another page of the same docs contradicts —
     * `docs/notion-api.md` §11. It is confirmed against the user's own database at M4.15, on a
     * duplicate. If it turns out to be name-only, the mapping stays keyed on id and only the caller
     * changes: it resolves id → current name from the schema it already fetched.
     */
    suspend fun updatePage(
        pageId: String,
        properties: Map<String, JsonElement>,
    ): NotionResult<NotionPageDto>
}

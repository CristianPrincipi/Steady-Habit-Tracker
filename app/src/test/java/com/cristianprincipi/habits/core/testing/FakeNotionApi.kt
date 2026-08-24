package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.data.remote.notion.NotionApi
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.NotionResult
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceRefDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDatabaseDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionListDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.time.Instant

/**
 * A Notion workspace in memory. **The only Notion the tests ever talk to** (`CLAUDE.md` §6, §8).
 *
 * Built in M4.8 for the sync engine and deliberately a little more capable than that step needs,
 * because M4.14 owns the full scenario matrix and should grow this rather than start again.
 *
 * Three behaviours are modelled on purpose, because the engine's correctness rests on them:
 *
 * - **Query results are filtered by `editedAtOrAfter` — inclusively, as the real endpoint's
 *   `on_or_after` is — and sorted ascending by edit time**, exactly as
 *   `QueryDataSourceRequest` asks the real endpoint to. That is what makes a killed run resumable,
 *   so a fake that returned everything in insertion order would hide the bug it prevents.
 * - **Pagination is real.** [pageSize] is small by default so the walk over `cursorForNextPage` is
 *   exercised by ordinary tests rather than by one that remembers to.
 * - **Writes move the clock forward.** Every create and update stamps a new `last_edited_time`, so
 *   the pull that follows a push sees what Notion would actually report.
 * - **A run can be killed mid-pagination** ([failQueryAfterPages]), which is the only way to
 *   exercise ADR-047's cursorless resume rather than assume it.
 *
 * ### The scenario matrix `CLAUDE.md` §6 asks for — audited in M4.14
 *
 * | Case | Where it is proved |
 * | --- | --- |
 * | First-run import | `SyncPullTest.aFirstRunImportsEveryDayAndItsCheckOffs`, `SyncEngineTest.aFirstRunTakesTheHabitSetFromTheSchema` |
 * | Offline edit queue | `SyncEngineTest.aFailedPullLeavesEverythingStillOwed` (it stays owed) **+ `SyncRecoveryTest.anEditMadeOfflineIsSentWhenTheNetworkComesBack`** (it is then delivered) |
 * | Both sides edited | `SyncConflictTest`, all of it |
 * | Page deleted or archived upstream | `SyncPullTest.aPageReportedGoneRemovesTheDayItStoodFor`, `…aGonePageWithAnUnpushedEditIsRecreated…`, `SyncPushTest.aPushToAPageThatIsGone…` |
 * | Renamed or removed property | `SyncEngineTest.aRenamedPropertyRenamesTheHabitAndKeepsItsIdentity`, `…aHabitTheMappingNoLongerNames…`, `…aBrokenMappingStopsTheRun…`, **`SyncRecoveryTest.aRetypedDayKeyStopsTheRunWithAnEditStillPending`** |
 * | Two habits, one property id (ADR-035) | `NotionMappingStoreTest.aBlobNamingTheSameHabitTwiceIsRejected…` (the guard) and `HabitDaoTest.aSecondHabitClaimingTheSameNotionPropertyIdIsSILENTLYDROPPED` (the hazard it guards against) |
 * | Revoked token | `KtorNotionApiTest.aRevokedTokenIsUnauthorisedAndIsNotRetried` (client), **`SyncRecoveryTest.aRevokedTokenIsNotWorthRetrying…`** (engine) |
 * | 429 backoff | `KtorNotionApiTest.aRateLimitIsRetriedAfterTheServersOwnDelay` + `…ThatNeverClears…` (client), **`SyncRecoveryTest.aRateLimitTheClientCouldNotClear…`** (engine) |
 * | Resume after a mid-run kill | `SyncPushTest.aPageLeftBehindByAKilledRunIsAdopted…` (push side), **`SyncRecoveryTest.theNextRunResumesFromTheWatermarkAndLosesNothing`** (pull side) |
 *
 * *"Local delete" is struck: nothing local can delete a record (ADR-036).*
 *
 * ⭐ **The bold entries are M4.14's.** Before it, a descending query order — the thing ADR-047's
 * ascending sort exists to rule out — was caught by **none** of the 461 tests. It is now caught by
 * two, which was confirmed by mutating this fake to sort descending and watching them fail.
 */
class FakeNotionApi(
    var schema: NotionDataSourceDto,
    private val pageSize: Int = 2,
) : NotionApi {
    private val pages = LinkedHashMap<String, NotionPageDto>()

    /** Fails every call. For the offline and revoked-token paths. */
    var failEverythingWith: NotionError? = null

    /** Fails only `queryPages`, leaving the schema readable. */
    var failQueryWith: NotionError? = null

    /**
     * ⭐ Serves this many pages of a paginated pull, then fails — **a run killed mid-pagination**.
     *
     * The only way to exercise ADR-047's actual claim. The engine has no stored cursor: its resume
     * point is `MAX(remoteLastEditedAt)` over the days it already has, and that is only *correct*
     * because the query asks for ascending `last_edited_time`, which makes every prefix of a run a
     * valid stopping point. A fake that could only fail the whole query proved the run stopped; it
     * could not prove the next one picks up in the right place.
     *
     * The failure is [failQueryWith] when one is set, so a test can choose between "the socket died"
     * and "the process was killed" — the engine treats them the same and the distinction is the
     * test's, not its.
     *
     * Counted per **run**: the counter resets whenever a query arrives with no cursor.
     */
    var failQueryAfterPages: Int? = null

    /** Fails only `updatePage`. The `404` case is how a deleted page is discovered (§14). */
    var failUpdateWith: NotionError? = null

    /** Every `editedAtOrAfter` the engine has asked with, in order. The incremental-pull assertion. */
    val queriedEditedAtOrAfter = mutableListOf<Instant?>()

    /**
     * What the search finds. `null` means the one [schema], which is what every sync test wants.
     *
     * Set it to an empty list for the case `CLAUDE.md` §3.3 is about: a valid token shared with
     * nothing, which returns **zero results** rather than an error.
     */
    var searchResults: List<NotionDataSourceDto>? = null

    /** The stubs `retrieveDatabase` reports. Empty is a legal answer and the setup flow must cope. */
    var databaseDataSources: List<NotionDataSourceRefDto> = emptyList()

    /** Ids passed to `retrieveDatabase`, in order — where the link parser's output is asserted. */
    val retrievedDatabaseIds = mutableListOf<String>()

    /** Property maps sent to `createPage`, in order. */
    val created = mutableListOf<Map<String, JsonElement>>()

    /** Page id and property map for each `updatePage`, in order. */
    val updated = mutableListOf<Pair<String, Map<String, JsonElement>>>()

    /**
     * Returns trashed and archived pages from `queryPages`, **which the real endpoint never does**.
     *
     * `docs/notion-api.md` §14: a trashed page is not returned and an archived one is excluded by
     * default, which is exactly why a remote delete is invisible to an incremental pull. The default
     * here models that. This flag exists so the engine's defensive `isGone` branch can be exercised
     * — a page arriving flagged is the case the page object's two side-by-side fields leave open —
     * and turning it on is a test saying so out loud rather than a fixture drifting from reality.
     */
    var includeTrashedInQuery: Boolean = false

    /**
     * Runs just before a write reaches the store, while the engine is mid-request.
     *
     * The only way to reproduce the race that matters on a device: the user checking a habit off
     * while a push for that day is in flight.
     */
    var duringWrite: (suspend () -> Unit)? = null

    private var nextEditMillis = 1_000L
    private var nextPageNumber = 1
    private var pagesServedThisRun = 0

    /** The pages currently in the workspace, oldest edit first. */
    val currentPages: List<NotionPageDto> get() = pages.values.sortedBy { it.lastEditedTime }

    fun page(id: String): NotionPageDto? = pages[id]

    /** Puts a page into the workspace as though the user had made it there. */
    fun givePage(page: NotionPageDto): NotionPageDto {
        pages[page.id] = page
        return page
    }

    /** The `last_edited_time` the next write will stamp, so a test can line timestamps up. */
    fun nextEditedTime(): String = Instant.ofEpochMilli(nextEditMillis).toString()

    /**
     * Paginated, like the query endpoint, and for the same reason: the setup flow walks every page
     * of the search, and a fake that answered in one shot would let a first-page-only bug ship in a
     * workspace big enough to have a second page.
     */
    override suspend fun searchDataSources(
        query: String?,
        startCursor: String?,
    ): NotionResult<NotionListDto<NotionDataSourceDto>> {
        failEverythingWith?.let { return NotionResult.Failure(it) }

        val visible = searchResults ?: listOf(schema)
        val from = startCursor?.toInt() ?: 0
        val window = visible.drop(from).take(pageSize)
        val next = from + window.size
        val hasMore = next < visible.size
        return NotionResult.Success(
            NotionListDto(
                results = window,
                hasMore = hasMore,
                nextCursor = if (hasMore) next.toString() else null,
            ),
        )
    }

    override suspend fun retrieveDatabase(databaseId: String): NotionResult<NotionDatabaseDto> {
        failEverythingWith?.let { return NotionResult.Failure(it) }
        retrievedDatabaseIds += databaseId
        return NotionResult.Success(NotionDatabaseDto(id = databaseId, dataSources = databaseDataSources))
    }

    override suspend fun retrieveDataSource(dataSourceId: String): NotionResult<NotionDataSourceDto> =
        failEverythingWith?.let { NotionResult.Failure(it) }
            ?: NotionResult.Success(schema)

    // ReturnCount: three ways a query ends — failing outright, failing partway through a run, or
    // answering. One exit each, and which one happened is the whole subject of `SyncRecoveryTest`.
    @Suppress("ReturnCount")
    override suspend fun queryPages(
        dataSourceId: String,
        editedAtOrAfter: Instant?,
        startCursor: String?,
    ): NotionResult<NotionListDto<NotionPageDto>> {
        failEverythingWith?.let { return NotionResult.Failure(it) }
        // Recorded once per paginated run rather than once per page: the cursor pages of one walk
        // all carry the same filter, and a test asserting the pull window wants the window.
        if (startCursor == null) {
            queriedEditedAtOrAfter += editedAtOrAfter
            pagesServedThisRun = 0
        }
        val pageLimit = failQueryAfterPages
        if (pageLimit == null) {
            failQueryWith?.let { return NotionResult.Failure(it) }
        } else if (pagesServedThisRun >= pageLimit) {
            return NotionResult.Failure(failQueryWith ?: NotionError.Network(IOException("killed mid-pull")))
        }

        // A trashed page is never returned by the real endpoint (`docs/notion-api.md` §14), which is
        // why a remote delete is invisible to an incremental pull. Modelled here so no test can
        // accidentally rely on the engine being told.
        val visible =
            pages.values
                .filterNot { it.isGone && !includeTrashedInQuery }
                .filter { editedAtOrAfter == null || !Instant.parse(it.lastEditedTime).isBefore(editedAtOrAfter) }
                .sortedBy { it.lastEditedTime }

        val from = startCursor?.toInt() ?: 0
        val window = visible.drop(from).take(pageSize)
        val next = from + window.size
        val hasMore = next < visible.size
        pagesServedThisRun++
        return NotionResult.Success(
            NotionListDto(
                results = window,
                hasMore = hasMore,
                nextCursor = if (hasMore) next.toString() else null,
            ),
        )
    }

    override suspend fun createPage(
        dataSourceId: String,
        properties: Map<String, JsonElement>,
    ): NotionResult<NotionPageDto> {
        failEverythingWith?.let { return NotionResult.Failure(it) }
        duringWrite?.invoke()
        created += properties
        val page =
            NotionPageDto(
                id = "created-${nextPageNumber++}",
                lastEditedTime = stamp(),
                properties = properties.mapValues { (_, value) -> value as JsonObject },
            )
        pages[page.id] = page
        return NotionResult.Success(page)
    }

    override suspend fun updatePage(
        pageId: String,
        properties: Map<String, JsonElement>,
    ): NotionResult<NotionPageDto> {
        (failEverythingWith ?: failUpdateWith)?.let { return NotionResult.Failure(it) }
        duringWrite?.invoke()
        updated += pageId to properties
        // A partial update: the properties sent are merged over what the page already holds, which
        // is what keeps `Weight`, `Sleep` and `Area` alive through a sync (docs/notion-schema.md §7).
        // A fake that replaced the map would make that impossible to get wrong, and it is the exact
        // thing worth being able to get wrong in a test.
        return pages[pageId]?.let { existing ->
            val merged =
                existing.properties +
                    properties.entries.associate { (id, value) ->
                        keyFor(existing, id) to value as JsonObject
                    }
            val page = existing.copy(lastEditedTime = stamp(), properties = merged)
            pages[pageId] = page
            NotionResult.Success(page)
        } ?: NotionResult.Failure(NotionError.MissingOrNotShared)
    }

    /**
     * The map key a property id sits under on this page — its **name**, or the id itself for a
     * property the page does not have yet.
     *
     * The real API keys a page's `properties` by name on the way out and takes ids on the way in
     * (ADR-038), and reproducing that asymmetry is what stops a test passing because both sides
     * happened to use the same string.
     */
    private fun keyFor(
        page: NotionPageDto,
        propertyId: String,
    ): String =
        page.properties.entries
            .firstOrNull { (_, value) -> value["id"]?.toString()?.trim('"') == propertyId }
            ?.key
            ?: propertyId

    private fun stamp(): String = Instant.ofEpochMilli(nextEditMillis.also { nextEditMillis += 1_000 }).toString()
}

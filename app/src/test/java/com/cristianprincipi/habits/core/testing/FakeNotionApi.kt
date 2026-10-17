package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.data.remote.notion.NotionApi
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.NotionResult
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceRefDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDatabaseDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionListDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionRequestStatusDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 * - **A written property reads back the way Notion returns it** — keyed by name, carrying its `id`
 *   and `type` (M16.2). The engine sends `{"number": 82.9}` under an id; until this fake stamped
 *   both back on, a value it had written was unreadable to the next pull, which made "the number
 *   survives the round trip" a claim no test could make.
 * - **A run can be killed mid-pagination** ([failQueryAfterPages]), which is the only way to
 *   exercise ADR-047's cursorless resume rather than assume it — and, since M13.11,
 *   **mid-drain** too ([failWritesAfter]), which is the same claim on the push side.
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
 * | Airplane mode **mid-drain** | ⭐ M13.11: `SyncRecoveryTest.aDrainKilledPartWayKeepsWhatItDelivered…`, `…theRunAfterAKilledDrainSendsOnlyWhatIsStillOwed`, `…aKilledDrainNeverSendsADeliveredDayTwice` |
 *
 * *"Local delete" is struck: nothing local can delete a record (ADR-036).*
 *
 * ⭐ **The bold entries are M4.14's.** Before it, a descending query order — the thing ADR-047's
 * ascending sort exists to rule out — was caught by **none** of the 461 tests. It is now caught by
 * two, which was confirmed by mutating this fake to sort descending and watching them fail.
 *
 * ⭐ **The last row is M13.11's, and it closes the same hole on the other side.** Every push-failure
 * test before it was all-or-nothing, because [failUpdateWith] fails *every* write — so the case a
 * phone actually produces, a week of offline check-offs draining until the network dies halfway,
 * could not be written. See [failWritesAfter].
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

    /**
     * Serve this many pages, then answer as Notion does at its 10,000-result ceiling: `has_more`
     * **false** with a `request_status` of `incomplete` (`docs/notion-api.md` §5).
     *
     * ⚠ ADR-118's lesson applied a second time. A fake that could only fail a whole query could not
     * prove a run resumes in the right place; a fake that can only end a query *cleanly* cannot
     * prove anything about the one case where `has_more` lies. The backfill (ADR-132) is the first
     * code path where that lie has no watermark to correct it.
     */
    var truncateAfterPages: Int? = null

    /** Fails only `updatePage`. The `404` case is how a deleted page is discovered (§14). */
    var failUpdateWith: NotionError? = null

    /**
     * ⭐ Lets this many writes through in a run, then fails them — **a drain killed part-way**.
     *
     * The push-side mirror of [failQueryAfterPages], and it exists for the identical reason. That
     * knob's own note says a fake which could only fail the *whole* query proved the run stopped but
     * could not prove the next one picks up in the right place. [failUpdateWith] leaves push in
     * exactly that position: it fails **every** write, so the only push failure any test could
     * describe was an all-or-nothing one.
     *
     * The case a phone actually produces is neither. A week of check-offs made offline drains oldest
     * first ([com.cristianprincipi.habits.data.local.dao.DayDao.withState] is `ORDER BY epochDay
     * ASC`), and the network dies somewhere in the middle of it — leaving some days delivered and
     * marked `SYNCED`, and the rest still owed. What has to be true afterwards is that the next run
     * sends the remainder and **only** the remainder: re-sending a delivered day is wasted rate
     * limit, and skipping an owed one loses a check-off `CLAUDE.md` §1 promises is never lost.
     *
     * Counts **creates and updates together**, because a dropped socket does not care which verb was
     * in flight and a drain can contain both — a day with no page yet is a create.
     *
     * The failure is always a dropped socket, because that is the case this knob is named for — the
     * network going away mid-drain. [failUpdateWith] stays the way to choose a different cause, and
     * keeps its all-or-nothing meaning. Counted per **run**, reset by the same signal as
     * [failQueryAfterPages]: every `sync()` opens with a cursorless pull.
     */
    var failWritesAfter: Int? = null

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
    private var writesThisRun = 0

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
            writesThisRun = 0
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
        pagesServedThisRun++
        val cutShort = truncateAfterPages?.let { pagesServedThisRun >= it } == true
        val hasMore = next < visible.size && !cutShort
        return NotionResult.Success(
            NotionListDto(
                results = window,
                hasMore = hasMore,
                nextCursor = if (hasMore) next.toString() else null,
                requestStatus =
                    if (cutShort) {
                        NotionRequestStatusDto(type = "incomplete", incompleteReason = "query_result_limit_reached")
                    } else {
                        null
                    },
            ),
        )
    }

    override suspend fun createPage(
        dataSourceId: String,
        properties: Map<String, JsonElement>,
    ): NotionResult<NotionPageDto> {
        refusedWrite()?.let { return it }
        duringWrite?.invoke()
        created += properties
        val page =
            NotionPageDto(
                id = "created-${nextPageNumber++}",
                lastEditedTime = stamp(),
                properties =
                    properties.entries.associate { (id, value) ->
                        nameInSchema(id) to asReturned(id, value as JsonObject)
                    },
            )
        pages[page.id] = page
        return NotionResult.Success(page)
    }

    override suspend fun updatePage(
        pageId: String,
        properties: Map<String, JsonElement>,
    ): NotionResult<NotionPageDto> {
        refusedWrite(alsoFailWith = failUpdateWith)?.let { return it }
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
                        keyFor(existing, id) to asReturned(id, value as JsonObject)
                    }
            val page = existing.copy(lastEditedTime = stamp(), properties = merged)
            pages[pageId] = page
            NotionResult.Success(page)
        } ?: NotionResult.Failure(NotionError.MissingOrNotShared)
    }

    /**
     * Why this write should be refused, or `null` to let it through — and it spends the budget.
     *
     * Deciding it here rather than at each call site is what keeps creates and updates sharing one
     * count, which is the point of [failWritesAfter]: a drain that dies part-way does not restart
     * its tally because the next day happened to need a different verb.
     *
     * ⭐ **When a budget is set it is the authority and [alsoFailWith] is ignored.** Otherwise
     * `failWritesAfter = 3` together with `failUpdateWith` would fail the *first* write rather than
     * the fourth — the test would have asked for a partial drain and been given an all-or-nothing
     * one, silently.
     *
     * `ReturnCount` is suppressed for the same reason [queryPages] suppresses it: four ways a write
     * ends, one exit each, and the order they are checked in is the whole subject above.
     *
     * @param alsoFailWith a failure this endpoint has of its own — [failUpdateWith], which fails
     *   every update when no budget is set.
     */
    @Suppress("ReturnCount")
    private fun refusedWrite(alsoFailWith: NotionError? = null): NotionResult.Failure? {
        failEverythingWith?.let { return NotionResult.Failure(it) }
        val budget = failWritesAfter
        if (budget != null) {
            if (writesThisRun >= budget) {
                return NotionResult.Failure(NotionError.Network(IOException("killed mid-push")))
            }
        } else {
            alsoFailWith?.let { return NotionResult.Failure(it) }
        }
        writesThisRun++
        return null
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

    /** The name the schema gives a property id — what Notion keys a returned page by. */
    private fun nameInSchema(propertyId: String): String =
        schema.properties.values
            .firstOrNull { it.id == propertyId }
            ?.name ?: propertyId

    /**
     * A written value as a read returns it: `{"number": 82.9}` becomes
     * `{"id": …, "type": "number", "number": 82.9}`. The type is the value's own key, which is how
     * Notion's write shape names it.
     */
    private fun asReturned(
        propertyId: String,
        value: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("id", propertyId)
            value.keys.firstOrNull { it != "id" && it != "type" }?.let { type -> put("type", type) }
            value.forEach { (key, element) -> if (key != "id" && key != "type") put(key, element) }
        }

    private fun stamp(): String = Instant.ofEpochMilli(nextEditMillis.also { nextEditMillis += 1_000 }).toString()
}

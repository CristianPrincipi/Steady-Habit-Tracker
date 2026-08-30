package com.cristianprincipi.habits.domain.repository

import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SavedNotionMapping
import kotlinx.coroutines.flow.Flow

/**
 * Connecting the app to the user's Notion database, one step at a time.
 *
 * The flow this serves, in order: paste a token → see what it can reach → pick the database → say
 * which columns are the day and which are habits → import. Each step is one method, and each can
 * fail in a way the screen has to explain, which is why they all answer with a
 * [com.cristianprincipi.habits.domain.model.NotionSetupResult] rather than throwing.
 *
 * **Nothing here speaks HTTP, and nothing here is a Notion DTO.** `CLAUDE.md` §6 stops both at the
 * data layer; a ViewModel holding a `NotionPropertySchemaDto` would be one refactor away from a
 * screen that renders a wire field.
 *
 * **The token goes in and never comes back out.** [saveToken] takes it, [isConnected] reports only
 * whether one exists, and there is no reader — `CLAUDE.md` §3.6, and
 * [com.cristianprincipi.habits.data.remote.notion.auth.NotionTokenStore]'s own note that a screen
 * receiving the token has put it into a `UiState` that something will eventually print.
 */
interface NotionSetupRepository {
    /** Whether a token is stored — **not whether it works**. A revoked token is still stored. */
    val isConnected: Flow<Boolean>

    /**
     * Whether setup has **finished** — a database is picked and its columns are mapped.
     *
     * ⚠ **Not the same question as [isConnected], and the difference is load-bearing.** A token is
     * stored at the *first* step of the wizard, two steps before the user has chosen anything; a
     * caller that treats "has a token" as "is set up" will decide the flow is over while the user
     * is still in the middle of it. This is the one to route on.
     */
    val isConfigured: Flow<Boolean>

    /**
     * A link to the user's habit database in Notion, or `null` while the app has not learned one.
     *
     * ⭐ **The one thing this app can hand to Notion instead of asking it for something.** ADR-010
     * makes the habit set read-only here — a habit *is* a column in that database — so "go and
     * change it where it lives" is the honest answer, and a screen that says that owes the user a
     * way there.
     *
     * **`null` is normal and temporary.** The wizard keeps a *data source* id, and a `notion.so`
     * link needs the *database* id, which the schema fetch on the first sync supplies. So a
     * connection made by an older build, or one whose first sync has not landed, has none yet. A
     * consumer disables its control and says so rather than hiding it (ADR-060).
     */
    val databaseUrl: Flow<String?>

    /**
     * Stores the pasted token, encrypted under the Android Keystore.
     *
     * Does **not** validate it: that is [findSources]'s job, because the only way to know a token
     * works is to spend a call on it. Saving first is deliberate — the client reads the token from
     * the store, so it has to be there before anything can be tried with it.
     */
    suspend fun saveToken(token: String)

    /**
     * Every database the connection can reach.
     *
     * ⭐ **An empty result is [com.cristianprincipi.habits.domain.model.SyncFailure.DatabaseNotShared],
     * not an empty list.** `CLAUDE.md` §3.3: a valid token that has been shared with nothing returns
     * zero results rather than an error, and a screen that renders "no databases found" sends the
     * user hunting for a problem with their token. The problem is almost always that they have not
     * opened the database in Notion and added the connection to it.
     */
    suspend fun findSources(): NotionSetupResult<List<NotionSource>>

    /**
     * The same answer, for a database the user pasted a link to.
     *
     * The fallback path, and it earns its place: Notion's search is eventually consistent, so a
     * database shared thirty seconds ago can be missing from [findSources] while being perfectly
     * reachable by id. A link is also all the user has when the workspace is large enough that the
     * list is unhelpful.
     *
     * @param databaseLink a full Notion URL or a bare id, in any of the shapes a user can copy.
     *   Parsing is this layer's job, not the screen's.
     */
    suspend fun findSourcesInDatabase(databaseLink: String): NotionSetupResult<List<NotionSource>>

    /**
     * The chosen database's columns, each already carrying what it is allowed to be.
     *
     * `CLAUDE.md` §3.4 forbids hardcoding a property name anywhere, which is why this exists at all:
     * the mapping screen is populated from the live schema every time, and knows nothing about
     * `Date` or `📖 Read` until Notion says so.
     */
    suspend fun loadProperties(sourceId: String): NotionSetupResult<List<NotionProperty>>

    /**
     * What is already mapped, or `null` if setup has never finished.
     *
     * Read before the mapping step renders, so it can start from the user's previous answers rather
     * than from a guess — see [SavedNotionMapping].
     */
    suspend fun savedMapping(): SavedNotionMapping?

    /**
     * Saves what the user decided, replacing any previous mapping.
     *
     * @param habitPropertyIds in the order they should be shown — the order becomes
     *   `HabitEntity.sortOrder`.
     */
    suspend fun saveMapping(
        sourceId: String,
        dayKeyPropertyId: String,
        habitPropertyIds: List<String>,
    )

    /**
     * Runs the first full sync, in the foreground, and reports how it went.
     *
     * The one place in the app where a sync is awaited rather than scheduled, and the exception
     * proves the rule: `CLAUDE.md` §1 says no screen may wait on a sync **to render**, and no screen
     * does — but a setup flow whose last step is "import your habits" has nothing to show until it
     * has. Every later sync goes through WorkManager.
     *
     * @return the value of the run — a success carries how many days arrived.
     */
    suspend fun importNow(): NotionSetupResult<Int>

    /**
     * Forgets the connection. The **disconnect** action `CLAUDE.md` §3.6 requires.
     *
     * Four things go unconditionally, and the list is longer than it first looks:
     *
     * 1. the token, ciphertext and Keystore key together;
     * 2. the property mapping — without a token it names columns in a database the app cannot
     *    reach, and a stale one is how the next connection silently maps onto the wrong schema;
     * 3. the last sync failure — an error about a database this app is no longer connected to is
     *    not something to keep showing;
     * 4. the discarded-edit notices, for the same reason.
     *
     * **`lastSyncedAt` is deliberately left alone**, the way `NotionMappingStore.clear` leaves it:
     * disconnecting is not a claim about when the data was last correct.
     *
     * @param alsoDeleteLocalData the user's separate, explicit choice — **never implied by
     *   disconnecting**. Losing the history is not what "stop syncing" means, and for a habit whose
     *   Notion column has since been deleted the local copy may be the only one left in existence
     *   (`docs/notion-schema.md` §4.1). Default `false` is the safe direction, and the screen asks.
     */
    suspend fun disconnect(alsoDeleteLocalData: Boolean = false)
}

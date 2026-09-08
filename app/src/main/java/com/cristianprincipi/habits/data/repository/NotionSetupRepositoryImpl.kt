package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.data.local.LocalHabitData
import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionMappingStore
import com.cristianprincipi.habits.data.preferences.NotionMeasure
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.preferences.NotionPropertyMapping
import com.cristianprincipi.habits.data.remote.notion.NotionApi
import com.cristianprincipi.habits.data.remote.notion.NotionDatabaseLink
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.NotionResult
import com.cristianprincipi.habits.data.remote.notion.auth.NotionToken
import com.cristianprincipi.habits.data.remote.notion.auth.NotionTokenStore
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionPropertyType
import com.cristianprincipi.habits.data.remote.notion.mapper.propertyType
import com.cristianprincipi.habits.data.sync.DiscardedEditStore
import com.cristianprincipi.habits.data.sync.LastSyncFailureStore
import com.cristianprincipi.habits.data.sync.SyncEngine
import com.cristianprincipi.habits.data.sync.SyncOutcome
import com.cristianprincipi.habits.data.sync.failure
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SavedMeasure
import com.cristianprincipi.habits.domain.model.SavedNotionMapping
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotionSetupRepository] over the client, the two stores and the sync engine.
 *
 * **This class is the boundary `CLAUDE.md` §6 draws.** Above it: `NotionSource`, `NotionProperty`,
 * `SyncFailure` — types a composable can render. Below it: DTOs, `NotionError`, percent-encoded
 * property ids and a Ktor client. Nothing crosses except through the translations here.
 */
@Singleton
class NotionSetupRepositoryImpl
    @Inject
    // The collaborators the connection's whole life needs: one remote, the two things setup saves,
    // the engine that runs the first import, and the three things disconnect wipes. `CLAUDE.md`
    // §3.6 is what makes the tail of that list long, and it is a list worth being explicit about —
    // a facade over "everything disconnect clears" would hide the enumeration this class's KDoc
    // exists to keep honest. `LocalHabitData` is already one such extraction, made because the
    // database wipe was a genuinely different job; the rest are not.
    @Suppress("LongParameterList")
    constructor(
        private val api: NotionApi,
        private val tokenStore: NotionTokenStore,
        private val mappingStore: NotionMappingStore,
        private val syncEngine: SyncEngine,
        private val lastFailure: LastSyncFailureStore,
        private val discardedEdits: DiscardedEditStore,
        private val localData: LocalHabitData,
    ) : NotionSetupRepository {
        override val isConnected: Flow<Boolean> = tokenStore.isConnected

        override val isConfigured: Flow<Boolean> = mappingStore.mapping.map { mapping -> mapping != null }

        // The id is stored and the URL is derived, rather than the other way round: a stored URL
        // would be a second spelling of the same fact, and `NotionDatabaseLink` already owns how a
        // Notion link is spelled in both directions.
        override val databaseUrl: Flow<String?> =
            mappingStore.mapping.map { mapping ->
                mapping?.databaseId?.value?.let(NotionDatabaseLink::urlFor)
            }

        override suspend fun saveToken(token: String) {
            tokenStore.save(NotionToken(token.trim()))
        }

        /**
         * Every page of the search, not just the first.
         *
         * A workspace with more than a hundred shared data sources would otherwise show the user a
         * list that silently stops — and the one they want is as likely to be on page two as page
         * one, since the search has no ordering we control.
         */
        override suspend fun findSources(): NotionSetupResult<List<NotionSource>> {
            val sources = mutableListOf<NotionSource>()
            var cursor: String? = null
            do {
                val page =
                    when (val result = api.searchDataSources(startCursor = cursor)) {
                        is NotionResult.Failure -> return NotionSetupResult.Failure(result.error.asSetupFailure())
                        is NotionResult.Success -> result.value
                    }
                page.results.mapTo(sources, NotionDataSourceDto::asSource)
                cursor = page.cursorForNextPage
            } while (cursor != null)

            return sources.orNotShared()
        }

        // ReturnCount: three outcomes, one exit each — an unreadable link, a call that failed, and
        // the answer. Folding them into one expression would bury which of the three happened, and
        // this method exists precisely to keep the first two apart.
        @Suppress("ReturnCount")
        override suspend fun findSourcesInDatabase(databaseLink: String): NotionSetupResult<List<NotionSource>> {
            // A link this app cannot read is not a server problem, and it never becomes a request.
            // Reporting it as `DatabaseNotShared` would send someone to Notion's sharing menu to fix
            // what is actually a paste into the wrong box.
            val databaseId =
                NotionDatabaseLink.idOrNull(databaseLink)
                    ?: return NotionSetupResult.UnreadableLink

            val database =
                when (val result = api.retrieveDatabase(databaseId)) {
                    is NotionResult.Failure -> return NotionSetupResult.Failure(result.error.asSetupFailure())
                    is NotionResult.Success -> result.value
                }

            // The stubs carry a plain-string name; the full object carries rich text. Both end up as
            // one display string, which is all the picker needs — resolving each stub to its full
            // object would be one request per data source to learn nothing new.
            return database.dataSources
                .map { stub -> NotionSource(id = stub.id, title = stub.name) }
                .orNotShared()
        }

        override suspend fun loadProperties(sourceId: String): NotionSetupResult<List<NotionProperty>> =
            when (val result = api.retrieveDataSource(sourceId)) {
                is NotionResult.Failure -> {
                    NotionSetupResult.Failure(result.error.asSetupFailure())
                }

                is NotionResult.Success -> {
                    NotionSetupResult.Success(
                        // Sorted by name so the list is stable between runs. Notion returns the
                        // properties as a JSON object, and object key order is not something to rely
                        // on — a list that reshuffles itself between two visits to the same screen
                        // looks broken even when nothing changed.
                        result.value.properties.values
                            .map(NotionPropertySchemaDto::asProperty)
                            .sortedBy(NotionProperty::name),
                    )
                }
            }

        override suspend fun savedMapping(): SavedNotionMapping? =
            mappingStore.read()?.let { mapping ->
                SavedNotionMapping(
                    sourceId = mapping.dataSourceId.value,
                    dayKeyPropertyId = mapping.dayKeyPropertyId.value,
                    habitPropertyIds = mapping.habitPropertyIds.map(NotionPropertyId::value),
                    measures = mapping.measures.map { SavedMeasure(it.id.value, it.unit) },
                )
            }

        override suspend fun saveMapping(
            sourceId: String,
            dayKeyPropertyId: String,
            habitPropertyIds: List<String>,
            measures: List<SavedMeasure>,
        ) {
            mappingStore.save(
                NotionPropertyMapping(
                    dataSourceId = NotionDataSourceId(sourceId),
                    dayKeyPropertyId = NotionPropertyId(dayKeyPropertyId),
                    // List -> LinkedHashSet: the user's order is the habit order, and a duplicate
                    // would be ADR-035's silent data loss. The screen cannot produce one — it toggles
                    // set membership — but the type is what guarantees it.
                    habitPropertyIds = habitPropertyIds.map(::NotionPropertyId).toSet(),
                    // ⚠ Stays a List, so the same guarantee has to be made by hand — a measure
                    // carries a unit, so two entries for one property are distinct set members and
                    // a `Set` would not collapse them. `NotionPropertyMapping`'s `require` is what
                    // catches it, and it throws here rather than storing an ambiguous mapping.
                    measures = measures.map { NotionMeasure(NotionPropertyId(it.propertyId), it.unit) },
                ),
            )
        }

        /**
         * The first sync, awaited rather than scheduled — and recorded like any other run.
         *
         * Writing the outcome to [LastSyncFailureStore] matters even though this call returns it:
         * a first import that failed is the state the sync-status screen must show afterwards, and
         * leaving it unrecorded would mean the app forgot a failure the user has already seen.
         */
        override suspend fun importNow(): NotionSetupResult<Int> {
            val outcome = syncEngine.sync()
            lastFailure.record(outcome)
            return when (outcome) {
                is SyncOutcome.Success -> NotionSetupResult.Success(outcome.daysPulled)

                // NotConfigured cannot happen — saveMapping ran first — but it is not a success
                // either, and mapping it to "imported 0 days" would report a silent no-op as a
                // finished setup.
                else -> NotionSetupResult.Failure(outcome.failure ?: SyncFailure.NotConnected)
            }
        }

        /**
         * Disconnect, in the order that survives being killed halfway.
         *
         * **The token goes first.** Everything else here is recoverable — a mapping is a few taps,
         * notices are notices — but a process killed after wiping the mapping and before wiping the
         * token would leave the one thing `CLAUDE.md` §3.6 calls a password sitting on the device
         * with nothing pointing at it, and no screen offering to remove it. Wiping the secret first
         * means a half-finished disconnect fails safe.
         *
         * **The local data goes last, and as one transaction** — see [LocalHabitData].
         */
        override suspend fun disconnect(alsoDeleteLocalData: Boolean) {
            tokenStore.clear()
            mappingStore.clear()
            lastFailure.clear()
            discardedEdits.clear()

            if (alsoDeleteLocalData) localData.deleteEverything()
        }

        /**
         * An empty list is the diagnosis, not an empty list.
         *
         * ⭐ `CLAUDE.md` §3.3 and `NotionApi.searchDataSources`'s own KDoc: a valid token that has
         * been shared with nothing returns **zero results**, not a 404 and not a 403. Rendering that
         * as "no databases found" sends the user to check their token, which is the one thing that
         * is definitely fine — the call succeeded.
         */
        private fun List<NotionSource>.orNotShared(): NotionSetupResult<List<NotionSource>> =
            if (isEmpty()) NotionSetupResult.Failure(SyncFailure.DatabaseNotShared) else NotionSetupResult.Success(this)
    }

private fun NotionDataSourceDto.asSource(): NotionSource = NotionSource(id = id, title = displayTitle)

/**
 * A schema entry as the mapping screen needs it, with its eligibility already decided.
 *
 * `CLAUDE.md` §3.2 requires the UI to *prevent* a read-only property being chosen "rather than
 * failing at sync time". Deciding it here rather than in a composable is what makes that structural:
 * by the time a property is rendered it already knows what it may be, and there is no branch on
 * screen for anyone to get wrong.
 *
 * A type this build has never heard of — [propertyType] is `null` — is [NotionPropertyRole.Unavailable]
 * and keeps its raw wire name, so a column added by a future Notion release shows up as itself
 * instead of vanishing from the list.
 */
private fun NotionPropertySchemaDto.asProperty(): NotionProperty =
    NotionProperty(
        id = id,
        name = name,
        typeName = type,
        role =
            when (propertyType) {
                NotionPropertyType.Date -> NotionPropertyRole.DayKey

                NotionPropertyType.Checkbox -> NotionPropertyRole.Habit

                // M15.4. One branch, and it inherits this function's whole argument: by the time a
                // number reaches the mapping screen it already knows it may be a measure, so no
                // composable has to decide it and none can get it wrong.
                NotionPropertyType.Number -> NotionPropertyRole.Measure

                else -> NotionPropertyRole.Unavailable
            },
    )

/**
 * A wire error as something the setup screen can say.
 *
 * Delegates to the same translation `LastSyncFailureStore` uses, so the two screens cannot drift
 * into calling the same 401 different things — with one deliberate exception. **A 404 during setup
 * is `DatabaseNotShared` whatever it came from**, which is what the shared mapping already produces,
 * and it is the sentence that fixes the problem more often than any other in this app.
 */
private fun NotionError.asSetupFailure(): SyncFailure = SyncOutcome.Failed(this).failure ?: SyncFailure.NotConnected

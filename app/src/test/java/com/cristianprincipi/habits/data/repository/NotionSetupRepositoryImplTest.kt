package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.core.testing.FakeNotionApi
import com.cristianprincipi.habits.core.testing.FakeNotionTokenStore
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.dataSourceOf
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.data.local.LocalHabitData
import com.cristianprincipi.habits.data.local.TestTransactionRunner
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceRefDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionRichTextDto
import com.cristianprincipi.habits.data.sync.DiscardedEdit
import com.cristianprincipi.habits.data.sync.LastSyncFailureStore
import com.cristianprincipi.habits.data.sync.SyncSide
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SyncFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The setup flow's data layer: the boundary between Notion's vocabulary and the screen's.
 *
 * Two things are being proved throughout. That **no DTO, no HTTP status and no percent-encoded id
 * escapes** (`CLAUDE.md` §6) — every assertion below is on a domain type. And that the failures the
 * user will actually hit come back as the sentence that fixes them, which for this screen means
 * §3.3's not-shared case above all others.
 */
class NotionSetupRepositoryImplTest {
    private val world = SyncWorld()

    /** Only the failure store's file. The mapping shares [SyncWorld]'s, so the engine reads what setup wrote. */
    private val dataStore = InMemoryDataStore()
    private val tokenStore = FakeNotionTokenStore(token = null)

    private fun repository(api: FakeNotionApi = world.api) =
        NotionSetupRepositoryImpl(
            api = api,
            tokenStore = tokenStore,
            mappingStore = world.mappingStore,
            syncEngine = world.engine,
            lastFailure = LastSyncFailureStore(dataStore),
            discardedEdits = world.discardedEdits,
            localData =
                LocalHabitData(
                    world.habits,
                    world.days,
                    world.completions,
                    world.measures,
                    TestTransactionRunner(),
                ),
        )

    private fun <T> NotionSetupResult<T>.value(): T = (this as NotionSetupResult.Success).value

    private fun <T> NotionSetupResult<T>.reason(): SyncFailure = (this as NotionSetupResult.Failure).reason

    // --- the token ---------------------------------------------------------------------------

    @Test
    fun theTokenIsStoredAndNeverHandedBack() =
        runTest {
            // The interface has no reader at all — CLAUDE.md §3.6. All the screen may learn is that
            // one exists, which is what this asserts.
            val repository = repository()

            repository.saveToken("ntn_pretend")

            assertTrue(repository.isConnected.first())
        }

    @Test
    fun aPastedTokenIsTrimmed() =
        runTest {
            // Clipboards pick up trailing newlines, and a token with one is a token the API rejects
            // — as 401, which reads to the user as "your token is wrong" when it was fine.
            repository().saveToken("  ntn_pretend\n")

            assertEquals("ntn_pretend", tokenStore.read()?.value)
        }

    // --- finding the database ----------------------------------------------------------------

    @Test
    fun theSearchIsWalkedToItsLastPage() =
        runTest {
            // The fake paginates at 2. A workspace with more shared sources than one page would
            // otherwise show a list that silently stops, and the wanted one is as likely to be late
            // in it as early — the search has no ordering this app controls.
            world.api.searchResults =
                listOf(sourceNamed("ds-1", "Habits"), sourceNamed("ds-2", "Books"), sourceNamed("ds-3", "Meals"))

            val sources = repository().findSources().value()

            assertEquals(listOf("Habits", "Books", "Meals"), sources.map(NotionSource::title))
        }

    @Test
    fun aValidTokenSharedWithNothingIsTheNotSharedDiagnosis() =
        runTest {
            // ⭐ The whole point of this screen. Zero results is not an empty list to render — the
            // call succeeded, the token is fine, and the fix is in Notion's own sharing menu.
            world.api.searchResults = emptyList()

            assertEquals(SyncFailure.DatabaseNotShared, repository().findSources().reason())
        }

    @Test
    fun aRevokedTokenSaysSoRatherThanBlamingTheDatabase() =
        runTest {
            world.api.failEverythingWith = NotionError.Unauthorized

            assertEquals(SyncFailure.TokenRejected, repository().findSources().reason())
        }

    @Test
    fun beingOfflineIsNotADatabaseProblemEither() =
        runTest {
            world.api.failEverythingWith = NotionError.Network(IOException())

            assertEquals(SyncFailure.Offline, repository().findSources().reason())
        }

    // --- the pasted-link fallback ------------------------------------------------------------

    @Test
    fun aPastedLinkIsResolvedToItsDataSources() =
        runTest {
            // The fallback path: Notion's search is eventually consistent, so a database shared a
            // moment ago can be missing from the list while being perfectly reachable by id.
            val id = "8f2c41d97b5e40a3ac6e1d9f0b73e254"
            world.api.databaseDataSources = listOf(NotionDataSourceRefDto(id = "ds-1", name = "Habits"))

            val sources = repository().findSourcesInDatabase("https://www.notion.so/My-Habits-$id?v=deadbeef").value()

            assertEquals(listOf(NotionSource(id = "ds-1", title = "Habits")), sources)
            assertEquals(listOf(id), world.api.retrievedDatabaseIds)
        }

    @Test
    fun aLinkWithNoIdInItNeverReachesTheNetworkAndIsNotBlamedOnNotion() =
        runTest {
            // ⭐ Not a SyncFailure. Nothing was sent, so nothing Notion did can explain it — and
            // reporting it as `DatabaseNotShared` would send someone to the sharing menu to fix a
            // paste into the wrong box. That is the sharing instruction's whole value, spent wrong.
            val result = repository().findSourcesInDatabase("my habits database")

            assertEquals(NotionSetupResult.UnreadableLink, result)
            assertTrue("nothing id-shaped must not be sent", world.api.retrievedDatabaseIds.isEmpty())
        }

    @Test
    fun aTokenPastedIntoTheLinkFieldIsCaughtLocallyToo() =
        runTest {
            // Both are on the clipboard during setup, and the fields are two steps apart.
            val result = repository().findSourcesInDatabase("ntn_notarealtokenjustastringofletters")

            assertEquals(NotionSetupResult.UnreadableLink, result)
            assertTrue(world.api.retrievedDatabaseIds.isEmpty())
        }

    @Test
    fun aDatabaseWithNoDataSourcesReadsAsNotShared() =
        runTest {
            // Legal, and indistinguishable to the user from the sharing problem — both mean "there
            // is nothing here to connect to".
            world.api.databaseDataSources = emptyList()

            val result = repository().findSourcesInDatabase("8f2c41d97b5e40a3ac6e1d9f0b73e254")

            assertEquals(SyncFailure.DatabaseNotShared, result.reason())
        }

    // --- reading the schema ------------------------------------------------------------------

    @Test
    fun everyPropertyArrivesKnowingWhatItIsAllowedToBe() =
        runTest {
            // CLAUDE.md §3.2 wants the UI to PREVENT a read-only property being picked rather than
            // fail at sync time. Deciding it here is what makes that structural: there is no branch
            // left on screen for anyone to get wrong.
            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
                    NotionPropertySchemaDto(PropertyIds.WEIGHT, "Weight", "number"),
                    NotionPropertySchemaDto("pr%3Ag", "Progress", "formula"),
                )

            val roles =
                repository()
                    .loadProperties("ds-1")
                    .value()
                    .associate { it.name to it.role }

            assertEquals(NotionPropertyRole.Habit, roles["📖 Read"])
            assertEquals(NotionPropertyRole.DayKey, roles["Date"])
            // ⭐ M15.4 moved this one, and this assertion is what made the move visible: it read
            // `Unavailable` until a number became mappable, and changing it is the whole of gap (b).
            assertEquals(NotionPropertyRole.Measure, roles["Weight"])
            // ⚠ A formula stays Unavailable, and that is the line the new case did NOT blur: a
            // number is offered because the app has somewhere to put it, not because it is
            // writable. This one cannot be written at all.
            assertEquals(NotionPropertyRole.Unavailable, roles["Progress"])
        }

    @Test
    fun aPropertyTypeThisBuildHasNeverHeardOfStillNamesItself() =
        runTest {
            // Notion adds property types. An unknown one must appear in the list as itself rather
            // than vanish — a column the user can see in Notion and not in the app reads as a bug.
            world.api.schema = dataSourceOf(NotionPropertySchemaDto("x%3Ay", "Vibes", "vibe_meter"))

            val property = repository().loadProperties("ds-1").value().single()

            assertEquals("vibe_meter", property.typeName)
            assertEquals(NotionPropertyRole.Unavailable, property.role)
        }

    @Test
    fun thePropertyIdCrossesTheBoundaryVerbatim() =
        runTest {
            // Percent-encoded, and nothing decodes, trims or normalises it (docs/notion-schema.md
            // §6.1). The string Notion sent is the string that goes back.
            world.api.schema = dataSourceOf(NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"))

            assertEquals(
                "ZI%40W",
                repository()
                    .loadProperties("ds-1")
                    .value()
                    .single()
                    .id,
            )
        }

    @Test
    fun thePropertiesComeBackInAStableOrder() =
        runTest {
            // Notion returns them as a JSON object, and object key order is not a thing to rely on.
            // A list that reshuffles between two visits to the same screen looks broken.
            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto("c", "Zebra", "checkbox"),
                    NotionPropertySchemaDto("a", "Apple", "checkbox"),
                    NotionPropertySchemaDto("b", "Mango", "checkbox"),
                )

            val names = repository().loadProperties("ds-1").value().map(NotionProperty::name)

            assertEquals(listOf("Apple", "Mango", "Zebra"), names)
        }

    // --- saving and importing ----------------------------------------------------------------

    @Test
    fun theMappingIsSavedInTheOrderTheUserChose() =
        runTest {
            // The order becomes HabitEntity.sortOrder, so it is the user's habit order.
            val repository = repository()

            repository.saveMapping(
                sourceId = "ds-1",
                dayKeyPropertyId = PropertyIds.DATE,
                habitPropertyIds = listOf(PropertyIds.JOURNAL, PropertyIds.READ),
                measures = emptyList(),
            )

            val mapping = world.mappingStore.read()
            assertEquals(NotionDataSourceId("ds-1"), mapping?.dataSourceId)
            assertEquals(NotionPropertyId(PropertyIds.DATE), mapping?.dayKeyPropertyId)
            assertEquals(
                listOf(NotionPropertyId(PropertyIds.JOURNAL), NotionPropertyId(PropertyIds.READ)),
                mapping?.habitPropertyIds?.toList(),
            )
        }

    @Test
    fun theFirstImportRunsTheEngineAndCountsWhatArrived() =
        runTest {
            val repository = repository()
            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())
            world.api.givePage(page("p1", august19))

            assertEquals(1, repository.importNow().value())
        }

    @Test
    fun aFirstImportThatFailedIsRememberedForTheSyncStatusScreen() =
        runTest {
            // It is returned to this screen AND written down. A failure the user has already seen
            // must not be forgotten the moment they leave the setup flow.
            val repository = repository()
            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())
            world.api.failEverythingWith = NotionError.Unauthorized

            val result = repository.importNow()

            assertEquals(SyncFailure.TokenRejected, result.reason())
            assertEquals(SyncFailure.TokenRejected, LastSyncFailureStore(dataStore).read())
        }

    // --- what "set up" means -------------------------------------------------------------------

    @Test
    fun storingATokenDoesNotMeanSetupIsFinished() =
        runTest {
            // ⭐ Found by looking at the running app. The wizard saves the token at its FIRST step,
            // two steps before the user has chosen anything — so anything routing on "has a token"
            // decides the flow is over while the user is still in the middle of it, and they never
            // see the database picker or the mapping.
            val repository = repository()

            repository.saveToken("ntn_pretend")

            assertTrue("a token is stored", repository.isConnected.first())
            assertFalse("but setup is not finished", repository.isConfigured.first())
        }

    @Test
    fun savingTheMappingIsWhatFinishesSetup() =
        runTest {
            val repository = repository()

            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())

            assertTrue(repository.isConfigured.first())
        }

    @Test
    fun disconnectingUnfinishesIt() =
        runTest {
            val repository = repository()
            repository.saveToken("ntn_pretend")
            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())

            repository.disconnect()

            assertFalse(repository.isConfigured.first())
            assertFalse(repository.isConnected.first())
        }

    // --- disconnect --------------------------------------------------------------------------

    @Test
    fun disconnectingWipesEverythingThatPointsAtNotion() =
        runTest {
            // CLAUDE.md §3.6's disconnect. Four things, not two — a mapping without a token names
            // columns in a database the app cannot reach, and an error about a database it is no
            // longer connected to is not something to keep showing.
            val repository = repository()
            repository.saveToken("ntn_pretend")
            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())
            world.api.failEverythingWith = NotionError.Unauthorized
            repository.importNow()
            world.discardedEdits.record(
                listOf(DiscardedEdit(20_000, setOf("h"), SyncSide.LOCAL, 1L)),
            )

            repository.disconnect()

            assertNull("the token", tokenStore.read())
            assertNull("the mapping", world.mappingStore.read())
            assertNull("the last failure", LastSyncFailureStore(dataStore).read())
            assertEquals("the notices", emptyList<DiscardedEdit>(), world.discardedEdits.read())
        }

    @Test
    fun disconnectingKeepsTheHistoryUnlessAskedOtherwise() =
        runTest {
            // ⭐ The default, and the important one. Losing the history is not what "stop syncing"
            // means, and for a habit whose Notion column was since deleted this may be the only
            // copy left in existence (docs/notion-schema.md §4.1).
            val repository = repository()
            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())
            world.api.givePage(page("p1", august19))
            repository.importNow()
            assertTrue("precondition: something was imported", world.habits.current.isNotEmpty())

            repository.disconnect(alsoDeleteLocalData = false)

            assertTrue("habits survive", world.habits.current.isNotEmpty())
            assertTrue("days survive", world.days.current.isNotEmpty())
        }

    @Test
    fun deletingTheLocalDataIsAnExplicitChoiceAndTakesEverything() =
        runTest {
            val repository = repository()
            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))
            repository.importNow()
            assertTrue("precondition: a completion exists", world.completions.current.isNotEmpty())

            repository.disconnect(alsoDeleteLocalData = true)

            assertTrue("habits gone", world.habits.current.isEmpty())
            assertTrue("days gone", world.days.current.isEmpty())
            assertTrue("completions gone", world.completions.current.isEmpty())
        }

    @Test
    fun deletingTheLocalDataTakesTheMeasureReadingsToo() =
        runTest {
            // ⚠⚠ **The failure this guards against surfaces nowhere.** A table added to the
            // database and forgotten in `LocalHabitData` does not break anything visible:
            // disconnect reports success, every screen goes empty because the habits are gone, and
            // the user's weight history quietly stays on the device after they asked for it to be
            // removed. `CLAUDE.md` §3.6 says the disconnect wipes "the synced local data", and this
            // is the only thing checking that "the synced local data" still means all of it.
            val repository = repository()
            repository.saveMapping("ds-1", PropertyIds.DATE, listOf(PropertyIds.READ), emptyList())
            world.days.upsert(
                DayEntity(
                    epochDay = august19.toEpochDay(),
                    notionPageId = "p1",
                    localUpdatedAtEpochMillis = null,
                    remoteLastEditedAtEpochMillis = 1L,
                    syncState = SyncState.SYNCED,
                ),
            )
            world.measures.upsert(
                listOf(
                    MeasureEntity(
                        id = "m1",
                        notionPropertyId = PropertyIds.WEIGHT,
                        notionPropertyName = "Weight",
                        unit = "kg",
                        color = HabitColor.GREEN.name,
                        isActive = true,
                        sortOrder = 0,
                    ),
                ),
            )
            world.measures.upsertReading(MeasurementEntity("m1", august19.toEpochDay(), 84.0))
            assertTrue("precondition: a reading exists", world.measures.currentReadings.isNotEmpty())

            repository.disconnect(alsoDeleteLocalData = true)

            assertTrue("readings gone", world.measures.currentReadings.isEmpty())
            assertTrue("measures gone", world.measures.current.isEmpty())
        }

    @Test
    fun theTokenGoesFirstSoAHalfFinishedDisconnectFailsSafe() =
        runTest {
            // Everything else here is recoverable; the token is the one thing §3.6 calls a
            // password. A process killed mid-disconnect must not leave it behind.
            val repository = repository()
            repository.saveToken("ntn_pretend")

            repository.disconnect()

            assertNull(tokenStore.read())
        }

    private fun sourceNamed(
        id: String,
        title: String,
    ): NotionDataSourceDto = NotionDataSourceDto(id = id, title = listOf(NotionRichTextDto(title)))
}

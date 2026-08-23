package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.FakeNotionApi
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import com.cristianprincipi.habits.data.local.TestTransactionRunner
import com.cristianprincipi.habits.data.local.dao.FakeCompletionDao
import com.cristianprincipi.habits.data.local.dao.FakeDayDao
import com.cristianprincipi.habits.data.local.dao.FakeHabitDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionMappingStore
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.preferences.NotionPropertyMapping
import com.cristianprincipi.habits.data.preferences.SettingsDataStore
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import com.cristianprincipi.habits.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The sync engine, against [FakeNotionApi] — never the live API (`CLAUDE.md` §6, §8).
 *
 * The fixtures use the real property ids and names from `docs/notion-schema.md`, and the workspace
 * carries a `Weight` column the mapping does not name, because half of what this engine has to get
 * right is what it leaves alone.
 *
 * M4.14 owns the full scenario matrix and grows [FakeNotionApi] to reach it. What is covered here is
 * the engine's own decisions: the order of the three phases, adoption by date, conflict detection,
 * and every path where a wrong move would lose data.
 */
class SyncEngineTest {
    private val dataSourceId = "ds-1"
    private val readId = "ZI%40W"
    private val journalId = "Ye%3Aq"
    private val dateId = "M%3BBw"
    private val weightId = "Nw%7Cb"

    private val schema =
        dataSource(
            NotionPropertySchemaDto(readId, "📖 Read", "checkbox"),
            NotionPropertySchemaDto(journalId, "✒️ Journal", "checkbox"),
            NotionPropertySchemaDto(dateId, "Date", "date"),
            NotionPropertySchemaDto(weightId, "Weight", "number"),
        )

    private val mapping =
        NotionPropertyMapping(
            dataSourceId = NotionDataSourceId(dataSourceId),
            dayKeyPropertyId = NotionPropertyId(dateId),
            habitPropertyIds = setOf(NotionPropertyId(readId), NotionPropertyId(journalId)),
        )

    private val clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC)

    private val august19 = LocalDate.of(2026, 8, 19)
    private val august20 = LocalDate.of(2026, 8, 20)

    // ---- the habit set -------------------------------------------------------------------------

    @Test
    fun aFirstRunTakesTheHabitSetFromTheSchema() =
        runTest {
            val world = world()

            world.engine.sync()

            assertEquals(
                listOf(readId to "📖 Read", journalId to "✒️ Journal"),
                world.habits.current
                    .sortedBy { it.sortOrder }
                    .map { it.notionPropertyId to it.notionPropertyName },
            )
        }

    @Test
    fun theColumnsTheMappingDoesNotNameAreNotHabits() =
        runTest {
            // `Weight` is in the same schema and is the user's daily data, not a habit
            // (docs/notion-schema.md §4.3). Nothing here should ever have adopted it.
            val world = world()

            world.engine.sync()

            assertTrue(world.habits.current.none { it.notionPropertyId == weightId })
        }

    @Test
    fun aRenamedPropertyRenamesTheHabitAndKeepsItsIdentity() =
        runTest {
            // ⭐ ADR-038's payoff, end to end: the id did not move, so this is a rename and not a
            // deletion followed by a new habit — which would have orphaned every completion.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true)))
            world.engine.sync()
            val before = world.habits.current.single { it.notionPropertyId == readId }

            world.api.schema =
                dataSource(
                    NotionPropertySchemaDto(readId, "📖 Reading", "checkbox"),
                    NotionPropertySchemaDto(journalId, "✒️ Journal", "checkbox"),
                    NotionPropertySchemaDto(dateId, "Date", "date"),
                )
            world.engine.sync()

            val after = world.habits.current.single { it.notionPropertyId == readId }
            assertEquals(before.id, after.id)
            assertEquals("📖 Reading", after.notionPropertyName)
            assertEquals(1, world.completions.current.count { it.habitId == after.id })
        }

    @Test
    fun theLocalColourSurvivesEveryLaterSync() =
        runTest {
            // Colour is app-local and exists nowhere else (docs/notion-schema.md §3), and
            // HabitDao.upsert writes every column — so the pull has to carry it forward by hand.
            val world = world()
            world.engine.sync()
            val habitId =
                world.habits.current
                    .first()
                    .id
            world.habits.setColor(habitId, "PURPLE")

            world.engine.sync()

            assertEquals(
                "PURPLE",
                world.habits.current
                    .single { it.id == habitId }
                    .color,
            )
        }

    @Test
    fun aHabitTheMappingNoLongerNamesGoesInactiveAndKeepsItsHistory() =
        runTest {
            // Deleting a Notion property destroys its column and every value in it (§4.1), so these
            // rows may be the only copy left. Never a delete.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(journalId to true)))
            world.engine.sync()
            val journal = world.habits.current.single { it.notionPropertyId == journalId }

            world.mappingStore.save(mapping.copy(habitPropertyIds = setOf(NotionPropertyId(readId))))
            world.engine.sync()

            assertEquals(
                false,
                world.habits.current
                    .single { it.id == journal.id }
                    .isActive,
            )
            assertTrue(world.completions.current.any { it.habitId == journal.id })
        }

    // ---- the pull ------------------------------------------------------------------------------

    @Test
    fun aFirstRunImportsEveryDayAndItsCheckOffs() =
        runTest {
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true, journalId to false)))
            world.api.givePage(page("p2", august20, checks = mapOf(readId to true, journalId to true)))

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 2, daysPushed = 0), outcome)
            assertEquals(
                setOf(august19.toEpochDay(), august20.toEpochDay()),
                world.days.current.keys,
            )
            assertEquals(3, world.completions.current.size)
        }

    @Test
    fun thePullWalksEveryCursorPage() =
        runTest {
            // FakeNotionApi pages two at a time, so five days is three round trips.
            val world = world()
            repeat(5) { index ->
                world.api.givePage(page("p$index", august19.plusDays(index.toLong()), checks = mapOf(readId to true)))
            }

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 5, daysPushed = 0), outcome)
        }

    @Test
    fun theSecondRunOnlyAsksForWhatChangedSinceTheNewestPageItHas() =
        runTest {
            // CLAUDE.md §3.5: "pull incrementally... never re-fetch the entire database on every run".
            val world = world()
            world.api.givePage(page("p1", august19, editedAtMillis = 1_000))
            world.api.givePage(page("p2", august20, editedAtMillis = 2_000))
            world.engine.sync()

            world.engine.sync()

            assertEquals(listOf(null, Instant.ofEpochMilli(2_000)), world.api.queriedEditedAfter)
        }

    @Test
    fun aPageWithNoReadableDateIsSkippedAndCounted() =
        runTest {
            // A page with no date is not a day. Counted rather than swallowed, so a run that ignored
            // half the database cannot look like a clean one.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true)))
            world.api.givePage(NotionPageDto(id = "p2", lastEditedTime = "1970-01-01T00:00:09Z"))

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 1, daysPushed = 0, pagesSkipped = 1), outcome)
        }

    @Test
    fun aCheckboxThatWillNotDecodeLeavesTheLocalCompletionAlone() =
        runTest {
            // ADR-044 keeps the decoder total; this is the other half. A checkbox is never null in
            // Notion, so a value that will not read means the shape changed — and writing `false`
            // over it would invent a miss the user never had.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true)))
            world.engine.sync()

            world.api.givePage(
                NotionPageDto(
                    id = "p1",
                    lastEditedTime = "1970-01-01T00:01:00Z",
                    properties =
                        mapOf(
                            "Date" to dateProperty(august19),
                            "📖 Read" to
                                buildJsonObject {
                                    put("id", readId)
                                    put("type", "checkbox")
                                    put("checkbox", "yes")
                                },
                        ),
                ),
            )
            world.engine.sync()

            assertEquals(1, world.completions.current.size)
        }

    @Test
    fun aPageReportedGoneRemovesTheDayItStoodFor() =
        runTest {
            // The defensive branch, and the fake has to be told to allow it: a real query never
            // returns a trashed page (docs/notion-api.md §14), which is why this is not how a
            // remote delete is normally found. Keeping a day Notion no longer has would be
            // inventing data.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true)))
            world.engine.sync()

            world.api.includeTrashedInQuery = true
            world.api.givePage(
                page("p1", august19, checks = mapOf(readId to true), gone = true, editedAtMillis = 9_000),
            )
            world.engine.sync()

            assertTrue(world.days.current.isEmpty())
            assertTrue(world.completions.current.isEmpty())
        }

    @Test
    fun aGonePageWithAnUnpushedEditBecomesAConflictRatherThanVanishing() =
        runTest {
            // The user checked something off; dropping it to match a delete they may not have
            // intended is the silent destruction CLAUDE.md §3.5 rules out.
            val world = world()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, readId)

            world.api.includeTrashedInQuery = true
            world.api.givePage(page("p1", august19, gone = true, editedAtMillis = 9_000))
            world.engine.sync()

            assertEquals(
                SyncState.CONFLICT,
                world.days.current
                    .getValue(august19.toEpochDay())
                    .syncState,
            )
            assertEquals(1, world.completions.current.size)
        }

    // ---- the push ------------------------------------------------------------------------------

    @Test
    fun aDayOwedToNotionIsSentAsOneUpdateCarryingEveryMappedHabit() =
        runTest {
            // One page update for the whole day, not one per checkbox — and the unchecked habit is
            // sent as `false` rather than left out, because un-checking is a value, not an omission.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to false, journalId to false)))
            world.engine.sync()
            world.checkOffLocally(august19, readId)

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 0, daysPushed = 1), outcome)
            val (pageId, properties) = world.api.updated.single()
            assertEquals("p1", pageId)
            assertEquals(setOf(readId, journalId), properties.keys)
            assertEquals(
                true,
                properties
                    .getValue(readId)
                    .jsonObject["checkbox"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toBoolean(),
            )
            assertEquals(
                false,
                properties
                    .getValue(journalId)
                    .jsonObject["checkbox"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toBoolean(),
            )
        }

    @Test
    fun thePushNeverMentionsAPropertyTheAppDoesNotOwn() =
        runTest {
            // ⭐ docs/notion-schema.md §7: the sync "must read pages without touching them, and must
            // never null them out on write". `Weight` survives because it is never in the body.
            val world = world()
            world.api.givePage(page("p1", august19).withWeight(84.0))
            world.engine.sync()
            world.checkOffLocally(august19, readId)

            world.engine.sync()

            assertTrue(
                weightId !in
                    world.api.updated
                        .single()
                        .second.keys,
            )
            assertEquals(84.0, world.api.page("p1")?.weightValue())
        }

    @Test
    fun aDayWithNoPageYetIsCreatedWithItsDate() =
        runTest {
            // docs/notion-schema.md §2.5: a page *is* a day, so the first check-off on a fresh day
            // creates one. The date goes in the body; nothing else identifies it.
            val world = world()
            world.engine.sync()
            world.checkOffLocally(august20, readId)

            world.engine.sync()

            val created = world.api.created.single()
            assertEquals(setOf(readId, journalId, dateId), created.keys)
            assertEquals(
                "2026-08-20",
                created
                    .getValue(dateId)
                    .jsonObject["date"]
                    ?.jsonObject
                    ?.get("start")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                SyncState.SYNCED,
                world.days.current
                    .getValue(august20.toEpochDay())
                    .syncState,
            )
        }

    @Test
    fun aPageLeftBehindByAKilledRunIsAdoptedByItsDateInsteadOfDuplicated() =
        runTest {
            // ⭐ The idempotency argument for pulling before pushing. A run killed between
            // `createPage` returning and the id being stored leaves an orphan page and a day that
            // still believes it has none. The next pull matches it *by date* and adopts it.
            val world = world()
            world.engine.sync()
            world.checkOffLocally(august20, readId)
            world.api.givePage(page("orphan", august20, checks = mapOf(readId to true), editedAtMillis = 9_000))

            world.engine.sync()

            assertTrue(world.api.created.isEmpty())
            val day = world.days.current.getValue(august20.toEpochDay())
            assertEquals("orphan", day.notionPageId)
            assertEquals(SyncState.SYNCED, day.syncState)
        }

    @Test
    fun aCheckOffMadeWhileThePushIsInFlightIsNotMarkedSynced() =
        runTest {
            // ⭐ The race a device actually produces: the write lands in Room immediately, so a
            // blind SYNCED here would erase the only record that Notion is behind.
            val world = world()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, readId, at = 5_000)
            world.api.duringWrite = { world.checkOffLocally(august19, journalId, at = 9_000) }

            world.engine.sync()

            val day = world.days.current.getValue(august19.toEpochDay())
            assertEquals(SyncState.PENDING_PUSH, day.syncState)
            assertEquals("p1", day.notionPageId)
        }

    @Test
    fun aPushToAPageThatIsGoneForgetsThePageIdAndKeepsTheDayOwed() =
        runTest {
            // ⭐ The only remote delete this app can detect (docs/notion-api.md §14). The id is only
            // ever a cache of the date, so dropping it costs nothing and the next run recreates.
            val world = world()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, readId)
            world.api.failUpdateWith = NotionError.MissingOrNotShared

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Failed(NotionError.MissingOrNotShared), outcome)
            val day = world.days.current.getValue(august19.toEpochDay())
            assertNull(day.notionPageId)
            assertEquals(SyncState.PENDING_PUSH, day.syncState)
        }

    // ---- conflicts -----------------------------------------------------------------------------

    @Test
    fun bothSidesChangingIsAConflictAndTheLocalEditIsLeftIntact() =
        runTest {
            // ⭐ Detected here, resolved in M4.9. What must not happen is either side being
            // overwritten silently (CLAUDE.md §3.5).
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to false, journalId to false)))
            world.engine.sync()
            world.checkOffLocally(august19, readId)
            world.api.givePage(
                page("p1", august19, checks = mapOf(readId to false, journalId to true), editedAtMillis = 9_000),
            )

            world.engine.sync()

            val day = world.days.current.getValue(august19.toEpochDay())
            assertEquals(SyncState.CONFLICT, day.syncState)
            val read = world.habits.current.single { it.notionPropertyId == readId }
            assertTrue(world.completions.current.any { it.habitId == read.id })
        }

    @Test
    fun aConflictedDayIsNotPushed() =
        runTest {
            // Pushing it would resolve the conflict destructively, in favour of whoever synced last.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to false)))
            world.engine.sync()
            world.checkOffLocally(august19, readId)
            world.api.givePage(page("p1", august19, checks = mapOf(journalId to true), editedAtMillis = 9_000))
            world.engine.sync()

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 0, daysPushed = 0), outcome)
            assertTrue(world.api.updated.isEmpty())
        }

    @Test
    fun aRemoteChangeThatMatchesTheLocalOneIsNotAConflict() =
        runTest {
            // The killed-push case seen from the other side: the page holds exactly what we were
            // trying to send, so there is nothing to disagree about.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to false)))
            world.engine.sync()
            world.checkOffLocally(august19, readId)
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true), editedAtMillis = 9_000))

            world.engine.sync()

            assertEquals(
                SyncState.SYNCED,
                world.days.current
                    .getValue(august19.toEpochDay())
                    .syncState,
            )
            assertTrue(world.api.updated.isEmpty())
        }

    // ---- start dates ---------------------------------------------------------------------------

    @Test
    fun aHabitIsDatedFromItsFirstCheckOff() =
        runTest {
            // ADR-046. Notion holds no start date, and an old page carries `false` for a habit that
            // did not exist yet and for one simply not done — identically.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(journalId to true)))
            world.api.givePage(page("p2", august20, checks = mapOf(readId to true)))

            world.engine.sync()

            assertEquals(
                august20.toEpochDay(),
                world.habits.current
                    .single { it.notionPropertyId == readId }
                    .startDateEpochDay,
            )
        }

    @Test
    fun aHabitNeverCheckedOffIsDatedFromTheOldestDayOnRecord() =
        runTest {
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true)))
            world.api.givePage(page("p2", august20, checks = mapOf(readId to true)))

            world.engine.sync()

            assertEquals(
                august19.toEpochDay(),
                world.habits.current
                    .single { it.notionPropertyId == journalId }
                    .startDateEpochDay,
            )
        }

    // ---- the run as a whole --------------------------------------------------------------------

    @Test
    fun withNoMappingThereIsNothingToSync() =
        runTest {
            val world = world(mapping = null)

            assertEquals(SyncOutcome.NotConfigured, world.engine.sync())
            assertTrue(world.habits.current.isEmpty())
        }

    @Test
    fun aBrokenMappingStopsTheRunBeforeAnythingIsWritten() =
        runTest {
            // CLAUDE.md §3.4: pause and surface. Pushing under a stale mapping is how a real value
            // gets overwritten with a null.
            val world = world()
            world.api.givePage(page("p1", august19, checks = mapOf(readId to true)))
            world.api.schema = dataSource(NotionPropertySchemaDto(dateId, "Date", "date"))

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.MappingBroken)
            assertTrue(world.habits.current.isEmpty())
            assertTrue(world.days.current.isEmpty())
            assertTrue(world.api.updated.isEmpty())
        }

    @Test
    fun aFailedPullLeavesEverythingStillOwed() =
        runTest {
            val world = world()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, readId)
            world.api.failQueryWith = NotionError.Network(java.io.IOException("offline"))

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.Failed)
            assertEquals(
                SyncState.PENDING_PUSH,
                world.days.current
                    .getValue(august19.toEpochDay())
                    .syncState,
            )
            assertTrue(world.api.updated.isEmpty())
        }

    @Test
    fun onlyASuccessfulRunIsRecordedAsTheLastSync() =
        runTest {
            // The user needs to know when their data was last actually correct, not when something
            // last tried.
            val world = world()
            world.api.failEverythingWith = NotionError.Unauthorized

            world.engine.sync()

            assertNull(world.settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aSuccessfulRunRecordsWhenItFinished() =
        runTest {
            val world = world()

            world.engine.sync()

            assertEquals(clock.millis(), world.settings.lastSyncedAtEpochMillis.first())
        }

    // ---- fixtures ------------------------------------------------------------------------------

    /**
     * One app's worth of local storage, plus the workspace it syncs against.
     *
     * Wires itself rather than being handed its parts, so a test reads as a story about the two
     * sides drifting apart. The one piece of wiring worth noticing is the pair of lambdas between
     * the day and completion fakes: they reproduce the foreign key and its cascade, which is the
     * behaviour the engine leans on when a remote delete arrives.
     */
    private class World(
        schema: NotionDataSourceDto,
        clock: Clock,
    ) {
        val api = FakeNotionApi(schema)
        val habits = FakeHabitDao()
        val days = FakeDayDao()
        val completions = FakeCompletionDao { epochDay -> epochDay in days.current }

        private val preferences = InMemoryDataStore()
        val mappingStore = NotionMappingStore(preferences)
        val settings = SettingsDataStore(preferences)

        val engine =
            SyncEngine(
                api = api,
                mappingStore = mappingStore,
                habitImporter =
                    HabitImporter(
                        habitDao = habits,
                        dayDao = days,
                        completionDao = completions,
                        transactionRunner = TestTransactionRunner(),
                        clock = clock,
                    ),
                dayDao = days,
                completionDao = completions,
                transactionRunner = TestTransactionRunner(),
                settings = SettingsRepositoryImpl(settings),
                clock = clock,
            )

        init {
            days.onDelete = { epochDay -> completions.dropDay(epochDay) }
        }
    }

    private suspend fun world(mapping: NotionPropertyMapping? = this.mapping): World =
        World(schema, clock).apply { mapping?.let { mappingStore.save(it) } }

    /**
     * A local check-off, written exactly as `HabitRepositoryImpl.setCompleted` writes one: the day
     * first, then the completion, and `PENDING_PUSH` stamped whatever changed.
     */
    private suspend fun World.checkOffLocally(
        date: LocalDate,
        propertyId: String,
        at: Long = 5_000,
    ) {
        val habitId = habits.current.single { it.notionPropertyId == propertyId }.id
        val epochDay = date.toEpochDay()
        val existing = days.find(epochDay)
        days.upsert(
            existing?.copy(localUpdatedAtEpochMillis = at, syncState = SyncState.PENDING_PUSH)
                ?: DayEntity(
                    epochDay = epochDay,
                    notionPageId = null,
                    localUpdatedAtEpochMillis = at,
                    remoteLastEditedAtEpochMillis = null,
                    syncState = SyncState.PENDING_PUSH,
                ),
        )
        completions.insert(HabitCompletionEntity(habitId = habitId, epochDay = epochDay))
    }

    private fun dataSource(vararg properties: NotionPropertySchemaDto) =
        NotionDataSourceDto(
            id = dataSourceId,
            properties = properties.associateBy(NotionPropertySchemaDto::name),
        )

    /**
     * A page, keyed the way Notion keys one: by property **name**, with the id inside the value.
     *
     * Nothing in the engine may read that key — the mapping is on ids (ADR-038) — and building the
     * fixtures faithfully is what would catch it if something did.
     */
    private fun page(
        id: String,
        date: LocalDate,
        checks: Map<String, Boolean> = emptyMap(),
        editedAtMillis: Long = 1_000,
        gone: Boolean = false,
    ): NotionPageDto =
        NotionPageDto(
            id = id,
            lastEditedTime = Instant.ofEpochMilli(editedAtMillis).toString(),
            properties =
                buildMap {
                    put("Date", dateProperty(date))
                    checks.forEach { (propertyId, checked) ->
                        put(nameOf(propertyId), checkboxProperty(propertyId, checked))
                    }
                },
            inTrash = gone,
        )

    /** The user's `Weight` column: real data on the same page, which no sync may touch (§7). */
    private fun NotionPageDto.withWeight(weight: Double): NotionPageDto =
        copy(
            properties =
                properties +
                    (
                        "Weight" to
                            buildJsonObject {
                                put("id", weightId)
                                put("type", "number")
                                put("number", weight)
                            }
                    ),
        )

    private fun nameOf(propertyId: String): String =
        schema.properties.values
            .single { it.id == propertyId }
            .name

    private fun checkboxProperty(
        propertyId: String,
        checked: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("id", propertyId)
            put("type", "checkbox")
            put("checkbox", checked)
        }

    private fun dateProperty(date: LocalDate): JsonObject =
        buildJsonObject {
            put("id", dateId)
            put("type", "date")
            putJsonObject("date") { put("start", date.toString()) }
        }

    private fun NotionPageDto.weightValue(): Double? =
        properties.values
            .firstOrNull { it["id"]?.jsonPrimitive?.content == weightId }
            ?.get("number")
            ?.jsonPrimitive
            ?.content
            ?.toDouble()
}

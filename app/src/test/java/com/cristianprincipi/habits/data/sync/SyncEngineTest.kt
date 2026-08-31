package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.dataSourceOf
import com.cristianprincipi.habits.core.testing.defaultMapping
import com.cristianprincipi.habits.core.testing.defaultSchema
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.core.testing.syncTestClock
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.preferences.NotionDatabaseId
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The run as a whole, and the habit set it keeps in step with the schema.
 *
 * The other three files take a phase each: [SyncPullTest], [SyncPushTest] and [SyncConflictTest].
 * They share the fixture in `core/testing/SyncWorld.kt`, which is also what M4.14 grows.
 *
 * Nothing here talks to the live API, ever (`CLAUDE.md` §6, §8).
 */
class SyncEngineTest {
    // ---- the habit set -------------------------------------------------------------------------

    @Test
    fun aFirstRunTakesTheHabitSetFromTheSchema() =
        runTest {
            val world = connectedWorld()

            world.engine.sync()

            assertEquals(
                listOf(PropertyIds.READ to "📖 Read", PropertyIds.JOURNAL to "✒️ Journal"),
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
            val world = connectedWorld()

            world.engine.sync()

            assertTrue(world.habits.current.none { it.notionPropertyId == PropertyIds.WEIGHT })
        }

    @Test
    fun aRenamedPropertyRenamesTheHabitAndKeepsItsIdentity() =
        runTest {
            // ⭐ ADR-038's payoff, end to end: the id did not move, so this is a rename and not a
            // deletion followed by a new habit — which would have orphaned every completion.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))
            world.engine.sync()
            val before = world.habits.current.single { it.notionPropertyId == PropertyIds.READ }

            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Reading", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.JOURNAL, "✒️ Journal", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
                )
            world.engine.sync()

            val after = world.habits.current.single { it.notionPropertyId == PropertyIds.READ }
            assertEquals(before.id, after.id)
            assertEquals("📖 Reading", after.notionPropertyName)
            assertEquals(1, world.completions.current.count { it.habitId == after.id })
        }

    @Test
    fun theLocalColourSurvivesEveryLaterSync() =
        runTest {
            // Colour is app-local and exists nowhere else (docs/notion-schema.md §3), and
            // HabitDao.upsert writes every column — so the pull has to carry it forward by hand.
            val world = connectedWorld()
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
    fun aHabitTheMappingNoLongerNamesIsHiddenAndKeepsItsHistory() =
        runTest {
            // Deleting a Notion property destroys its column and every value in it (§4.1), so these
            // rows may be the only copy left. Never a delete.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.JOURNAL to true)))
            world.engine.sync()
            val journal = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }

            world.mappingStore.save(defaultMapping.copy(habitPropertyIds = setOf(NotionPropertyId(PropertyIds.READ))))
            world.engine.sync()

            assertEquals(
                false,
                world.habits.current
                    .single { it.id == journal.id }
                    .isActive,
            )
            assertTrue(world.completions.current.any { it.habitId == journal.id })
            // ⭐ **Inactive means invisible** (ADR-081 — the user's decision). The row is still
            // there, holding history that may be the only copy left, and it stops reaching every
            // screen at once because `observeActive` is the only list they have.
            assertTrue(
                world.habits
                    .observeActive()
                    .first()
                    .none { it.id == journal.id },
            )
        }

    @Test
    fun aHabitThatComesBackReusesItsOldRowRatherThanStartingASecondOne() =
        runTest {
            // ⭐⭐ **The drift case M7.4 does not name: add-after-remove.** Its three cases are added,
            // renamed and removed, and all three have tests — but a habit removed from the mapping
            // and later put back travels a path none of them describe, and it is the one where the
            // damage would be permanent rather than cosmetic.
            //
            // ⚠ **Both ingredients are already guarded, and that is exactly why this is worth
            // having.** `HabitDaoTest.findAllSeesInactiveHabitsToo` pins that the query sees inactive
            // rows, and `HabitImporter` reuses `previous?.id` — but nothing composes them, and the
            // composition is what M7.4's Definition of Done actually promises: "no data loss".
            //
            // If the importer ever stopped seeing the inactive row, this habit would be minted a
            // fresh UUID; the old row would keep every completion, the new one would show an empty
            // history, and **nothing would look broken** — the habit would simply appear to have
            // started today. That is the worst shape of failure available here.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.JOURNAL to true)))
            world.engine.sync()
            val before = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }
            world.habits.setColor(before.id, "PURPLE")

            // Unmapped — the column is still in Notion, the user just stopped calling it a habit.
            world.mappingStore.save(defaultMapping.copy(habitPropertyIds = setOf(NotionPropertyId(PropertyIds.READ))))
            world.engine.sync()

            // … and mapped again.
            world.mappingStore.save(defaultMapping)
            world.engine.sync()

            // `single` is half the assertion: two rows for one property id would throw here.
            val after = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }
            assertEquals(before.id, after.id)
            assertTrue(after.isActive)
            assertEquals(1, world.completions.current.count { it.habitId == after.id })
            // The local colour is app-only and survives the round trip out of the mapping and back.
            assertEquals("PURPLE", after.color)
            // …and it is on the list again, which is the second half of the user's rule: the
            // invisible row becomes visible in place rather than as a habit that started today.
            assertTrue(
                world.habits
                    .observeActive()
                    .first()
                    .any { it.id == after.id },
            )
        }

    // ---- start dates ---------------------------------------------------------------------------

    @Test
    fun aHabitIsDatedFromItsFirstCheckOff() =
        runTest {
            // ADR-046. Notion holds no start date, and an old page carries `false` for a habit that
            // did not exist yet and for one simply not done — identically.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.JOURNAL to true)))
            world.api.givePage(page("p2", august20, checks = mapOf(PropertyIds.READ to true)))

            world.engine.sync()

            assertEquals(
                august20.toEpochDay(),
                world.habits.current
                    .single { it.notionPropertyId == PropertyIds.READ }
                    .startDateEpochDay,
            )
        }

    @Test
    fun aHabitNeverCheckedOffIsDatedFromTheOldestDayOnRecord() =
        runTest {
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))
            world.api.givePage(page("p2", august20, checks = mapOf(PropertyIds.READ to true)))

            world.engine.sync()

            assertEquals(
                august19.toEpochDay(),
                world.habits.current
                    .single { it.notionPropertyId == PropertyIds.JOURNAL }
                    .startDateEpochDay,
            )
        }

    // ---- the run as a whole --------------------------------------------------------------------

    @Test
    fun withNoMappingThereIsNothingToSync() =
        runTest {
            val world = SyncWorld()

            assertEquals(SyncOutcome.NotConfigured, world.engine.sync())
            assertTrue(world.habits.current.isEmpty())
        }

    // ---- where the database lives (M7.5) -------------------------------------------------------

    @Test
    fun aSyncLearnsWhereTheDatabaseLives() =
        runTest {
            // The wizard cannot save this: it keeps a DATA SOURCE id, and a notion.so link needs the
            // DATABASE id. The schema fetched to validate the mapping carries it, so the first sync
            // after any connection supplies it — at no extra request.
            val world = connectedWorld()

            world.engine.sync()

            assertEquals(NotionDatabaseId(PropertyIds.DATABASE), world.mappingStore.read()?.databaseId)
        }

    @Test
    fun aBrokenMappingStillLearnsWhereTheDatabaseLives() =
        runTest {
            // ⭐ The case the ordering exists for. A mapping breaks when a column is deleted in
            // Notion, and the fix is to go to Notion — so this is precisely when the user needs the
            // link. Recording it AFTER the validation would withhold it exactly then.
            val world = connectedWorld()
            world.api.schema = dataSourceOf(NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"))

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.MappingBroken)
            assertEquals(NotionDatabaseId(PropertyIds.DATABASE), world.mappingStore.read()?.databaseId)
        }

    @Test
    fun aDataSourceWithNoDatabaseParentIsSyncedAnyway() =
        runTest {
            // An externally synced source parents to a data source rather than a database. There is
            // no link to build, and that is not a reason to refuse to sync the habits.
            val world = SyncWorld(schema = defaultSchema.copy(parent = null))
            world.connect()

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.Success)
            assertNull(world.mappingStore.read()?.databaseId)
        }

    @Test
    fun aBrokenMappingStopsTheRunBeforeAnythingIsWritten() =
        runTest {
            // CLAUDE.md §3.4: pause and surface. Pushing under a stale mapping is how a real value
            // gets overwritten with a null.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))
            world.api.schema = dataSourceOf(NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"))

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.MappingBroken)
            assertTrue(world.habits.current.isEmpty())
            assertTrue(world.days.current.isEmpty())
            assertTrue(world.api.updated.isEmpty())
        }

    @Test
    fun aFailedPullLeavesEverythingStillOwed() =
        runTest {
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)
            world.api.failQueryWith = NotionError.Network(java.io.IOException("offline"))

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.Failed)
            assertEquals(
                SyncState.PENDING_PUSH,
                world
                    .dayAt(august19)
                    .syncState,
            )
            assertTrue(world.api.updated.isEmpty())
        }

    @Test
    fun onlyASuccessfulRunIsRecordedAsTheLastSync() =
        runTest {
            // The user needs to know when their data was last actually correct, not when something
            // last tried.
            val world = connectedWorld()
            world.api.failEverythingWith = NotionError.Unauthorized

            world.engine.sync()

            assertNull(world.settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aSuccessfulRunRecordsWhenItFinished() =
        runTest {
            val world = connectedWorld()

            world.engine.sync()

            assertEquals(syncTestClock.millis(), world.settings.lastSyncedAtEpochMillis.first())
        }
}

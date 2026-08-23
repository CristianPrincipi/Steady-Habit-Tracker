package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.dataSourceOf
import com.cristianprincipi.habits.core.testing.defaultMapping
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.core.testing.syncTestClock
import com.cristianprincipi.habits.data.local.entity.SyncState
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
    fun aHabitTheMappingNoLongerNamesGoesInactiveAndKeepsItsHistory() =
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

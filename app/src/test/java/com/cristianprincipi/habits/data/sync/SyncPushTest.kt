package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.core.testing.weightValue
import com.cristianprincipi.habits.core.testing.withWeight
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.remote.notion.NotionError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The push: days out, one page update each.
 *
 * The two that matter most are adoption by date — the reason a killed run cannot duplicate a page —
 * and the body never naming a property the app does not own.
 */
class SyncPushTest {
    // ---- the push ------------------------------------------------------------------------------

    @Test
    fun aDayOwedToNotionIsSentAsOneUpdateCarryingEveryMappedHabit() =
        runTest {
            // One page update for the whole day, not one per checkbox — and the unchecked habit is
            // sent as `false` rather than left out, because un-checking is a value, not an omission.
            val world = connectedWorld()
            world.api.givePage(
                page(
                    "p1",
                    august19,
                    checks =
                        mapOf(
                            PropertyIds.READ to false,
                            PropertyIds.JOURNAL to false,
                        ),
                ),
            )
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 0, daysPushed = 1), outcome)
            val (pageId, properties) = world.api.updated.single()
            assertEquals("p1", pageId)
            assertEquals(setOf(PropertyIds.READ, PropertyIds.JOURNAL), properties.keys)
            assertEquals(
                true,
                properties
                    .getValue(PropertyIds.READ)
                    .jsonObject["checkbox"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toBoolean(),
            )
            assertEquals(
                false,
                properties
                    .getValue(PropertyIds.JOURNAL)
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
            val world = connectedWorld()
            world.api.givePage(page("p1", august19).withWeight(84.0))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)

            world.engine.sync()

            assertTrue(
                PropertyIds.WEIGHT !in
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
            val world = connectedWorld()
            world.engine.sync()
            world.checkOffLocally(august20, PropertyIds.READ)

            world.engine.sync()

            val created = world.api.created.single()
            assertEquals(setOf(PropertyIds.READ, PropertyIds.JOURNAL, PropertyIds.DATE), created.keys)
            assertEquals(
                "2026-08-20",
                created
                    .getValue(PropertyIds.DATE)
                    .jsonObject["date"]
                    ?.jsonObject
                    ?.get("start")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                SyncState.SYNCED,
                world
                    .dayAt(august20)
                    .syncState,
            )
        }

    @Test
    fun aPageLeftBehindByAKilledRunIsAdoptedByItsDateInsteadOfDuplicated() =
        runTest {
            // ⭐ The idempotency argument for pulling before pushing. A run killed between
            // `createPage` returning and the id being stored leaves an orphan page and a day that
            // still believes it has none. The next pull matches it *by date* and adopts it.
            val world = connectedWorld()
            world.engine.sync()
            world.checkOffLocally(august20, PropertyIds.READ)
            world.api.givePage(
                page("orphan", august20, checks = mapOf(PropertyIds.READ to true), editedAtMillis = 9_000),
            )

            world.engine.sync()

            assertTrue(world.api.created.isEmpty())
            val day = world.dayAt(august20)
            assertEquals("orphan", day.notionPageId)
            assertEquals(SyncState.SYNCED, day.syncState)
        }

    @Test
    fun aCheckOffMadeWhileThePushIsInFlightIsNotMarkedSynced() =
        runTest {
            // ⭐ The race a device actually produces: the write lands in Room immediately, so a
            // blind SYNCED here would erase the only record that Notion is behind.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ, at = 5_000)
            world.api.duringWrite = { world.checkOffLocally(august19, PropertyIds.JOURNAL, at = 9_000) }

            world.engine.sync()

            val day = world.dayAt(august19)
            assertEquals(SyncState.PENDING_PUSH, day.syncState)
            assertEquals("p1", day.notionPageId)
        }

    @Test
    fun aPushToAPageThatIsGoneForgetsThePageIdAndKeepsTheDayOwed() =
        runTest {
            // ⭐ The only remote delete this app can detect (docs/notion-api.md §14). The id is only
            // ever a cache of the date, so dropping it costs nothing and the next run recreates.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)
            world.api.failUpdateWith = NotionError.MissingOrNotShared

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Failed(NotionError.MissingOrNotShared), outcome)
            val day = world.dayAt(august19)
            assertNull(day.notionPageId)
            assertEquals(SyncState.PENDING_PUSH, day.syncState)
        }
}

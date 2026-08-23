package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.remote.notion.NotionError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Days both sides changed, and the notices that keep resolution from being silent.
 *
 * `ConflictResolverTest` covers the rule itself; this covers what the engine does with it — which
 * copy is applied, what is pushed, and what the user is left able to find out.
 */
class SyncConflictTest {
    // ---- conflicts -----------------------------------------------------------------------------

    @Test
    fun aNewerRemoteEditWinsAndTheLocalOneIsDiscardedOutLoud() =
        runTest {
            // ⭐ Last-write-wins, and the second half of CLAUDE.md §3.5: the run has to say what it
            // destroyed. The local check-off loses to a newer edit in Notion, and a notice survives
            // the run so a background sync cannot resolve this quietly.
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
            world.checkOffLocally(august19, PropertyIds.READ, at = 2_000)
            world.api.givePage(
                page(
                    "p1",
                    august19,
                    checks = mapOf(PropertyIds.READ to false, PropertyIds.JOURNAL to true),
                    editedAtMillis = 9_000,
                ),
            )

            world.engine.sync()

            val day = world.dayAt(august19)
            assertEquals(SyncState.SYNCED, day.syncState)
            val read = world.habits.current.single { it.notionPropertyId == PropertyIds.READ }
            val journal = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }
            // Notion's copy applied wholesale: Read gone, Journal added.
            assertEquals(
                setOf(journal.id),
                world.completions.current
                    .map { it.habitId }
                    .toSet(),
            )
            val notice = world.discardedEdits.read().single()
            assertEquals(august19.toEpochDay(), notice.epochDay)
            assertEquals(SyncSide.LOCAL, notice.discardedSide)
            assertEquals(setOf(read.id, journal.id), notice.habitIds)
        }

    @Test
    fun aNewerLocalEditWinsAndIsPushedRatherThanWrittenFromHere() =
        runTest {
            // ⭐ Resolution never writes to Notion itself. The day goes back to owing a push, and
            // the push phase — which already knows how to survive being killed — sends it.
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
            world.api.givePage(
                page(
                    "p1",
                    august19,
                    checks = mapOf(PropertyIds.READ to false, PropertyIds.JOURNAL to true),
                    editedAtMillis = 4_000,
                ),
            )
            world.checkOffLocally(august19, PropertyIds.READ, at = 9_000)

            world.engine.sync()

            val read = world.habits.current.single { it.notionPropertyId == PropertyIds.READ }
            assertTrue(world.completions.current.any { it.habitId == read.id })
            // One update, carrying the local truth, sent by the push phase in the same run.
            val (pageId, properties) = world.api.updated.single()
            assertEquals("p1", pageId)
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
                SyncState.SYNCED,
                world
                    .dayAt(august19)
                    .syncState,
            )
            assertEquals(
                SyncSide.REMOTE,
                world.discardedEdits
                    .read()
                    .single()
                    .discardedSide,
            )
        }

    @Test
    fun noDayIsEverLeftInConflict() =
        runTest {
            // The state M4.8 wrote and nothing could clear. A day that reaches resolution comes out
            // one side or the other, so the push can always make progress.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to false)))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ, at = 2_000)
            world.api.givePage(
                page("p1", august19, checks = mapOf(PropertyIds.JOURNAL to true), editedAtMillis = 9_000),
            )

            world.engine.sync()

            assertTrue(
                world.days.current.values
                    .none { it.syncState == SyncState.CONFLICT },
            )
        }

    @Test
    fun aDayLeftInConflictByAnEarlierBuildIsResolvedRatherThanStuck() =
        runTest {
            // The door out of a state nothing writes any more. Without it such a row would never
            // push and never resolve, because DayDao.withState asks only for PENDING_PUSH.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to false)))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ, at = 9_000)
            val stuck = world.dayAt(august19)
            world.days.upsert(stuck.copy(syncState = SyncState.CONFLICT))
            world.api.givePage(
                page("p1", august19, checks = mapOf(PropertyIds.JOURNAL to true), editedAtMillis = 4_000),
            )

            world.engine.sync()

            assertEquals(
                SyncState.SYNCED,
                world
                    .dayAt(august19)
                    .syncState,
            )
            assertEquals(1, world.api.updated.size)
        }

    @Test
    fun aRunThatDestroyedNothingLeavesNoNotice() =
        runTest {
            // The ordinary case. A notice for every sync would train the user to ignore them.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))

            world.engine.sync()

            assertTrue(world.discardedEdits.read().isEmpty())
        }

    @Test
    fun aRemoteChangeThatMatchesTheLocalOneIsNotAConflict() =
        runTest {
            // The killed-push case seen from the other side: the page holds exactly what we were
            // trying to send, so there is nothing to disagree about and nothing to discard.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to false)))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true), editedAtMillis = 9_000))

            world.engine.sync()

            assertEquals(
                SyncState.SYNCED,
                world
                    .dayAt(august19)
                    .syncState,
            )
            assertTrue(world.api.updated.isEmpty())
            assertTrue(world.discardedEdits.read().isEmpty())
        }

    @Test
    fun aNoticeSurvivesARunThatFailsAfterResolving() =
        runTest {
            // The discard happened; a push that fails afterwards is no reason for the user never to
            // hear about it.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to false)))
            world.api.givePage(page("p2", august20, checks = mapOf(PropertyIds.READ to false)))
            world.engine.sync()
            // August 19 loses to a newer edit in Notion; August 20 is simply owed, and its push is
            // what fails — so the run ends in failure with a resolution already behind it.
            world.checkOffLocally(august19, PropertyIds.READ, at = 2_000)
            world.api.givePage(
                page("p1", august19, checks = mapOf(PropertyIds.JOURNAL to true), editedAtMillis = 9_000),
            )
            world.checkOffLocally(august20, PropertyIds.JOURNAL, at = 3_000)
            world.api.failUpdateWith = NotionError.Unauthorized

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.Failed)
            assertEquals(1, world.discardedEdits.read().size)
        }
}

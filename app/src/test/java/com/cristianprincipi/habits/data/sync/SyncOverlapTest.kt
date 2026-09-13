package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M14.10 — two runs of the engine at once must not create a day's page twice.
 *
 * ### ⚠⚠ Why this was reachable before anyone looked
 *
 * The periodic schedule and "Sync now" have always had separate unique work names, so WorkManager
 * would run both at once; the setup wizard's first import calls the engine directly, beside either.
 * Adoption by date (ADR-047) makes a **killed** run safe, because the next run pulls first and finds
 * the orphan page. It does nothing for two **live** runs: both pull before either has created the
 * page, both see a day with no page, and both create one. Notion then holds two pages for one date.
 *
 * M14.10's sync on app open makes that likely rather than theoretical — open the app, pull down
 * straight away, and two runs start within a second of each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncOverlapTest {
    @Test
    fun aSecondRunWaitsForTheFirstInsteadOfCreatingTheSameDayAgain() =
        runTest {
            val world = connectedWorld()
            world.engine.sync()
            world.checkOffLocally(august20, PropertyIds.READ)

            // The first run is held inside `createPage`, after it decided to create and before the
            // page exists — the exact window the race needs.
            val release = CompletableDeferred<Unit>()
            var writes = 0
            world.api.duringWrite = { if (writes++ == 0) release.await() }

            val first = async { world.engine.sync() }
            runCurrent()
            val second = async { world.engine.sync() }
            runCurrent()

            release.complete(Unit)
            val outcomes = listOf(first.await(), second.await())

            assertEquals("one date, one page", 1, world.api.created.size)
            assertTrue("both runs finish cleanly: $outcomes", outcomes.all { it is SyncOutcome.Success })
        }
}

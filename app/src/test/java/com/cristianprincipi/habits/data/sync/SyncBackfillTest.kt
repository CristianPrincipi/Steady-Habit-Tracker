package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.mappingWithWeight
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.core.testing.withWeight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * ⭐⭐ **M15.6e — the pull re-reads everything exactly once when it has learned to read more.**
 *
 * ### The bug, as the user met it
 *
 * They placed a measure widget and its sparkline was a near-vertical line pinned to the right-hand
 * edge of the card: **two readings, drawn correctly, against a month that was empty behind them.**
 * Nothing was wrong with the drawing, the query, the mapping or the average. M15.4 taught
 * `applyPage` to store numbers, and the pull is incremental — so every day page older than the
 * watermark was already "seen" and was never fetched again. Their weights were in Notion the whole
 * time and had simply never been asked for.
 *
 * ⚠ **A fresh install cannot reproduce it**, which is why nothing caught it: with an empty `days`
 * table the watermark is null and the first sync reads everything. Only an install that *upgraded
 * across* the change is affected — and a developer's device is usually the fresh one.
 *
 * ### ⚠ Why [aMappingThatGainsAColumnAlsoEarnsABackfill] is the one that matters most
 *
 * A build constant fixes this once. The mapping is **user data** (`CLAUDE.md` §3.4), so the day the
 * user ticks a third number column its history sits in exactly the position Weight's did, with no
 * version to bump. That case has no ship date and no session to notice it.
 */
class SyncBackfillTest {
    private suspend fun upgradedWorld(): SyncWorld =
        SyncWorld(upgradedInstall = true).apply { connect(mappingWithWeight) }

    @Test
    fun anUpgradedInstallIgnoresTheWatermarkOnceAndThenStopsIgnoringIt() =
        runTest {
            val world = upgradedWorld()
            world.api.givePage(page("p1", august19, editedAtMillis = 1_000).withWeight(84.2))

            world.engine.sync()
            world.api.givePage(page("p2", august20, editedAtMillis = 2_000).withWeight(84.0))
            world.engine.sync()

            // One full pass, then back to incremental. A backfill that repeated would re-read the
            // whole database every fifteen minutes for ever.
            assertEquals(
                listOf(null, Instant.ofEpochMilli(1_000)),
                world.api.queriedEditedAtOrAfter,
            )
        }

    @Test
    fun theBackfillIsWhatPutsHistoricalNumbersIntoRoom() =
        runTest {
            // The user's own case: a page Notion has not touched for weeks, holding a weight this
            // build can read and the build that stored the day could not.
            val world = upgradedWorld()
            world.api.givePage(page("old", august19, editedAtMillis = 1_000).withWeight(84.2))

            world.engine.sync()

            val reading = world.measures.currentReadings.single()
            assertEquals(august19.toEpochDay(), reading.epochDay)
            assertEquals(84.2, reading.value, 0.0001)
        }

    @Test
    fun anInstallThatIsUpToDateKeepsUsingTheWatermark() =
        runTest {
            // The control. Without it the test above passes for a build that simply never went
            // incremental again, which would be a far worse bug wearing this one's fix.
            val world = SyncWorld().apply { connect(mappingWithWeight) }
            world.api.givePage(page("p1", august19, editedAtMillis = 1_000).withWeight(84.2))
            world.engine.sync()

            world.engine.sync()

            assertEquals(
                listOf(null, Instant.ofEpochMilli(1_000)),
                world.api.queriedEditedAtOrAfter,
            )
        }

    @Test
    fun aMappingThatGainsAColumnAlsoEarnsABackfill() =
        runTest {
            // ⚠⚠ The case with no ship date. The user maps a third number next month; its history
            // is in exactly the position Weight's was, and CURRENT_PULL_FORMAT has not moved.
            val world = SyncWorld().apply { connect() }
            world.api.givePage(page("p1", august19, editedAtMillis = 1_000).withWeight(84.2))
            world.engine.sync()
            assertTrue("precondition: Weight is not mapped yet", world.measures.currentReadings.isEmpty())

            world.connect(mappingWithWeight)
            world.engine.sync()

            assertEquals(
                84.2,
                world.measures.currentReadings
                    .single()
                    .value,
                0.0001,
            )
        }

    @Test
    fun droppingAColumnEarnsNothingBecauseThereIsNothingNewToRead() =
        runTest {
            // Backfilling on any *change* rather than on growth would re-read the whole database
            // every time the user unticked a habit, which is a normal thing to do.
            val world = SyncWorld().apply { connect(mappingWithWeight) }
            world.api.givePage(page("p1", august19, editedAtMillis = 1_000).withWeight(84.2))
            world.engine.sync()

            world.connect()
            world.engine.sync()

            assertEquals(
                listOf(null, Instant.ofEpochMilli(1_000)),
                world.api.queriedEditedAtOrAfter,
            )
        }

    @Test
    fun aBackfillThatFailedIsOwedAgain() =
        runTest {
            // Recording it on failure would lose the history permanently and silently — the exact
            // shape of the defect this whole file exists for.
            val world = upgradedWorld()
            // Enough to paginate: the fake only fails a page it is asked for after the first, so a
            // single-page run has nowhere to be killed.
            giveDays(world)
            world.api.failQueryAfterPages = 1
            world.engine.sync()

            world.api.failQueryAfterPages = null
            world.engine.sync()

            assertEquals(listOf(null, null), world.api.queriedEditedAtOrAfter)
        }

    @Test
    fun aTruncatedBackfillIsOwedAgainToo() =
        runTest {
            // At Notion's 10,000-result ceiling `has_more` goes false and lies. The incremental
            // path survives that because its watermark advanced; a backfill has no watermark, so a
            // pass cut short must not count as a pass.
            val world = upgradedWorld()
            giveDays(world)
            world.api.truncateAfterPages = 1
            world.engine.sync()

            world.api.truncateAfterPages = null
            world.engine.sync()

            assertEquals(listOf(null, null), world.api.queriedEditedAtOrAfter)
        }

    /** More days than the fake serves in one page, so a run has a second request to go wrong on. */
    private fun giveDays(world: SyncWorld) {
        repeat(PAGES) { index ->
            world.api.givePage(
                page("p$index", august19.plusDays(index.toLong()), editedAtMillis = 1_000L + index)
                    .withWeight(84.0),
            )
        }
    }

    private companion object {
        const val PAGES = 5
    }
}

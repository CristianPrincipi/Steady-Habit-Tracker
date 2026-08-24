package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.dataSourceOf
import com.cristianprincipi.habits.core.testing.defaultSchema
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/**
 * What happens **after** a run goes wrong, and what the run after that does about it.
 *
 * The other four sync suites each describe one phase working. This one describes recovery, which is
 * where `CLAUDE.md` §3.5's "idempotent and resumable" is actually claimed: *"It will be killed
 * mid-run. Running it twice must not create duplicate pages, and a partial run must leave the local
 * database in a valid state."*
 *
 * Every case here is a **second** sync. That is the point — the first one only sets up the damage.
 */
class SyncRecoveryTest {
    // --- the offline edit queue ----------------------------------------------------------------

    @Test
    fun anEditMadeOfflineIsSentWhenTheNetworkComesBack() =
        runTest {
            // ⭐ The half of "offline edit queue" that matters. That an edit STAYS owed while the
            // network is down is `SyncEngineTest.aFailedPullLeavesEverythingStillOwed`; that it is
            // then actually delivered is this, and nothing proved it before.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()

            world.api.failEverythingWith = NotionError.Network(IOException("airplane mode"))
            world.checkOffLocally(august19, PropertyIds.READ)
            val offlineRun = world.engine.sync()

            assertTrue("the run failed", offlineRun is SyncOutcome.Failed)
            assertTrue("nothing was sent", world.api.updated.isEmpty())
            assertEquals(SyncState.PENDING_PUSH, world.dayAt(august19).syncState)

            world.api.failEverythingWith = null
            val backOnline = world.engine.sync()

            assertTrue("the run worked", backOnline is SyncOutcome.Success)
            assertEquals("the day was pushed", 1, world.api.updated.size)
            assertEquals(SyncState.SYNCED, world.dayAt(august19).syncState)
            assertEquals("and it carried the check-off", true, world.pushedCheck(PropertyIds.READ))
        }

    @Test
    fun aWholeOfflineWeekDrainsInOneRunWhenTheNetworkReturns() =
        runTest {
            // Nothing is lost while the app is offline for days, and the catch-up is one run rather
            // than one run per day — `CLAUDE.md` §1: "if sync is broken for a week the app keeps
            // working and catches up afterwards".
            val world = connectedWorld()
            world.engine.sync()
            world.api.failEverythingWith = NotionError.Network(IOException("offline"))

            val week = (0L until 7L).map { offset -> august19.plusDays(offset) }
            week.forEach { date -> world.checkOffLocally(date, PropertyIds.READ) }
            world.engine.sync()

            world.api.failEverythingWith = null
            val outcome = world.engine.sync()

            assertEquals(7, (outcome as SyncOutcome.Success).daysPushed)
            assertTrue(week.all { date -> world.dayAt(date).syncState == SyncState.SYNCED })
        }

    // --- 429, met by the engine rather than by the client ---------------------------------------

    @Test
    fun aRateLimitTheClientCouldNotClearLeavesTheDayOwedAndIsWorthRetrying() =
        runTest {
            // `KtorNotionApiTest` proves the client honours `Retry-After` and retries. This is the
            // case where that was not enough and the whole run gives up: the day must stay owed, and
            // the outcome must be one `SyncWorker` comes back for — otherwise a rate limit would
            // quietly cost the user a check-off.
            val world = connectedWorld()
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)
            world.api.failEverythingWith = NotionError.RateLimited(retryAfter = 30.seconds)

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Failed(NotionError.RateLimited(30.seconds)), outcome)
            assertTrue("time can fix this one", outcome.isWorthRetrying)
            assertEquals(SyncState.PENDING_PUSH, world.dayAt(august19).syncState)
            assertTrue(world.api.updated.isEmpty())
        }

    @Test
    fun aRevokedTokenIsNotWorthRetryingAndStillKeepsTheEdit() =
        runTest {
            // The mirror case. Both leave the work owed; only one of them is worth coming back for,
            // and getting that wrong either spins the backoff forever or drops the schedule.
            val world = connectedWorld()
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)
            world.api.failEverythingWith = NotionError.Unauthorized

            val outcome = world.engine.sync()

            assertFalse("only a person can fix this one", outcome.isWorthRetrying)
            assertEquals(SyncState.PENDING_PUSH, world.dayAt(august19).syncState)
        }

    // --- killed mid-pagination -----------------------------------------------------------------

    @Test
    fun aPullKilledMidPaginationKeepsWhatItApplied() =
        runTest {
            // The fake paginates at 2. Six days, killed after the first page.
            val world = connectedWorld()
            sixAscendingDays(world)
            world.api.failQueryAfterPages = 1

            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.Failed)
            assertEquals("the first page is stored", 2, world.days.current.size)
        }

    @Test
    fun theNextRunResumesFromTheWatermarkAndLosesNothing() =
        runTest {
            // ⭐⭐ ADR-047's actual claim, and the first test to make it. There is no stored cursor:
            // the resume point is MAX(remoteLastEditedAt) over the days already held, and that is
            // only correct because the query asks for ASCENDING last_edited_time — which makes every
            // prefix of a run a valid stopping point. Unsorted, a kill halfway through pagination
            // would advance the watermark past pages it never applied, and they would never be asked
            // for again.
            val world = connectedWorld()
            val days = sixAscendingDays(world)
            world.api.failQueryAfterPages = 1
            world.engine.sync()
            assertEquals(2, world.days.current.size)

            world.api.failQueryAfterPages = null
            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.Success)
            assertEquals("every day arrived", days.size, world.days.current.size)
            assertEquals(
                "and none was skipped",
                days.map(LocalDate::toEpochDay).toSet(),
                world.days.current.keys,
            )
        }

    @Test
    fun theSecondRunAsksFromTheLastDayItActuallyApplied() =
        runTest {
            // The watermark is a fact about what was stored, not about what was requested. A run
            // that asked for six days and applied two must resume at the second, not the sixth.
            val world = connectedWorld()
            sixAscendingDays(world)
            world.api.failQueryAfterPages = 1
            world.engine.sync()
            val applied =
                world.days.current.values
                    .mapNotNull { it.remoteLastEditedAtEpochMillis }
                    .max()

            world.api.failQueryAfterPages = null
            world.engine.sync()

            assertEquals(
                applied,
                world.api.queriedEditedAtOrAfter
                    .last()
                    ?.toEpochMilli(),
            )
        }

    @Test
    fun aKilledPullNeverDuplicatesOnTheRunThatFollows() =
        runTest {
            // "Running it twice must not create duplicate pages" (`CLAUDE.md` §3.5), from the pull
            // side: the days are keyed by date, so a page seen twice updates rather than doubles.
            val world = connectedWorld()
            val days = sixAscendingDays(world)
            world.api.failQueryAfterPages = 1
            world.engine.sync()

            world.api.failQueryAfterPages = null
            world.engine.sync()
            world.engine.sync()

            assertEquals(days.size, world.days.current.size)
            assertTrue("and nothing was created upstream", world.api.created.isEmpty())
        }

    // --- a mapping that went stale between runs -------------------------------------------------

    @Test
    fun aRetypedDayKeyStopsTheRunWithAnEditStillPending() =
        runTest {
            // ⭐ The most destructive shape of `CLAUDE.md` §3.4's warning. The day key stops being a
            // date, so nothing can say which day a page is — and there is an unpushed check-off
            // waiting. Pushing under that mapping is exactly how "a real value is overwritten with a
            // null" happens, so the run must stop before the push and keep the edit owed.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)

            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.JOURNAL, "✒️ Journal", "checkbox"),
                    // The day key, no longer a date.
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "rich_text"),
                )
            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.MappingBroken)
            assertTrue("nothing was written to Notion", world.api.updated.isEmpty())
            assertEquals("and the edit is still owed", SyncState.PENDING_PUSH, world.dayAt(august19).syncState)
        }

    @Test
    fun aMappingThatIsFixedUpstreamLetsThePendingEditThrough() =
        runTest {
            // The recovery half: the user puts the column back, and the edit that was waiting all
            // along is delivered. Nothing had to be re-made by hand.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)
            val broken = dataSourceOf(NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"))
            world.api.schema = broken
            world.engine.sync()

            world.api.schema = defaultSchema
            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.Success)
            assertEquals(true, world.pushedCheck(PropertyIds.READ))
        }

    /**
     * Six days whose edit times ascend, which is the order the real query is asked to return.
     *
     * Spaced a second apart so a watermark taken from any one of them is unambiguous.
     */
    private fun sixAscendingDays(world: SyncWorld): List<LocalDate> {
        val days = (0L until 6L).map { offset -> august19.plusDays(offset) }
        days.forEachIndexed { index, date ->
            world.api.givePage(page("p$index", date, editedAtMillis = 1_000L + index * 1_000L))
        }
        return days
    }

    /**
     * What the most recent push said about one habit.
     *
     * Asserted on what was **sent** rather than on the fake's stored page, which is the pattern
     * `SyncPushTest` set: the request body is the thing under test, and reading the fake back would
     * test the fake.
     */
    private fun SyncWorld.pushedCheck(propertyId: String): Boolean? =
        api.updated
            .last()
            .second[propertyId]
            ?.jsonObject
            ?.get("checkbox")
            ?.jsonPrimitive
            ?.content
            ?.toBoolean()
}

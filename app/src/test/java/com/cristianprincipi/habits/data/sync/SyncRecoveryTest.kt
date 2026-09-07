package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
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

    // --- killed mid-drain ------------------------------------------------------------------------

    @Test
    fun aDrainKilledPartWayKeepsWhatItDeliveredAndKeepsTheRestOwed() =
        runTest {
            // ⭐⭐ **The push side of "airplane mode mid-sync", and M13.11's one real gap.**
            // `aWholeOfflineWeekDrainsInOneRunWhenTheNetworkReturns` proves a week drains when the
            // network holds. Every push-failure test before this one proved the opposite extreme,
            // because `failUpdateWith` fails EVERY write. What a phone actually produces is neither:
            // the drain starts, some days land, and the socket dies. `CLAUDE.md` §3.5 — "a partial
            // run must leave the local database in a valid state" — is claimed exactly here.
            val world = connectedWorld()
            world.engine.sync()
            val week = offlineWeek(world)

            world.api.failWritesAfter = 3
            val outcome = world.engine.sync()

            assertTrue("the run failed", outcome is SyncOutcome.Failed)
            assertTrue("and it is worth coming back for", outcome.isWorthRetrying)
            assertEquals("three days reached Notion", 3, world.api.created.size)
            // ⭐ The three must be the OLDEST three. `DayDao.withState` is `ORDER BY epochDay ASC`,
            // so the drain is oldest-first, and a run that delivered an arbitrary three would leave
            // the user's history arriving out of order for no reason anyone chose.
            assertEquals(
                "the oldest three are done",
                List(3) { SyncState.SYNCED },
                week.take(3).map { date -> world.dayAt(date).syncState },
            )
            assertEquals(
                "and the rest still owe a push",
                List(4) { SyncState.PENDING_PUSH },
                week.drop(3).map { date -> world.dayAt(date).syncState },
            )
        }

    @Test
    fun theRunAfterAKilledDrainSendsOnlyWhatIsStillOwed() =
        runTest {
            // The half that matters. A day delivered before the kill must not be sent again — that
            // is rate limit spent on nothing — and a day owed must not be skipped, which would cost
            // the user a check-off `CLAUDE.md` §1 promises is "never lost".
            val world = connectedWorld()
            world.engine.sync()
            val week = offlineWeek(world)
            world.api.failWritesAfter = 3
            world.engine.sync()

            world.api.failWritesAfter = null
            val outcome = world.engine.sync()

            assertEquals("only the four still owed were sent", 4, (outcome as SyncOutcome.Success).daysPushed)
            assertEquals("seven pages exist, not ten", 7, world.api.created.size)
            assertTrue("and the whole week is settled", week.all { world.dayAt(it).syncState == SyncState.SYNCED })
        }

    @Test
    fun aDrainKilledTwiceStillLosesNothingAndDuplicatesNothing() =
        runTest {
            // A flaky network does not fail once politely. Two kills in a row must converge on the
            // same place one kill does: "running it twice must not create duplicate pages"
            // (`CLAUDE.md` §3.5), from the push side, where the risk is real — after the first kill
            // some days hold a page id and some do not.
            val world = connectedWorld()
            world.engine.sync()
            val week = offlineWeek(world)

            world.api.failWritesAfter = 3
            world.engine.sync()
            world.api.failWritesAfter = 2
            world.engine.sync()
            world.api.failWritesAfter = null
            world.engine.sync()

            assertEquals("one page per day and no more", 7, world.api.created.size)
            assertTrue("every check-off arrived", week.all { world.dayAt(it).syncState == SyncState.SYNCED })
            assertEquals("and no day was created twice", 7, world.api.currentPages.size)
        }

    // --- a mapping that went stale between runs -------------------------------------------------

    @Test
    fun aHabitColumnDeletedInNotionHaltsTheRunAndLeavesEveryHabitExactlyAsItWas() =
        runTest {
            // ⭐⭐ **M7.4's "removed" case, on an install that has been working** — which is the only
            // shape where the behaviour is visible at all.
            // `SyncEngineTest.aBrokenMappingStopsTheRunBeforeAnythingIsWritten` starts from an empty
            // world, so its "nothing was written" is true whether the run halted or ran to
            // completion with nothing to do. Here there are two habits and a history to disturb.
            //
            // ⚠ **The habit whose column is gone must NOT be deactivated.** Deactivation belongs to
            // a different trigger — the *mapping* ceasing to name a habit (ADR-079) — and since
            // ADR-081 it also means the habit disappears from every screen. Doing it here would
            // hide a habit because of a problem upstream the user has not been told about yet, in
            // the same run that is refusing to read or write anything else.
            val world = connectedWorld()
            world.api.givePage(
                page("p1", august19, checks = mapOf(PropertyIds.READ to true, PropertyIds.JOURNAL to true)),
            )
            world.engine.sync()
            val journal = world.habitId(PropertyIds.JOURNAL)
            world.checkOffLocally(august20, PropertyIds.READ)

            // Journal's column is deleted in Notion. Read's is untouched, and that is the point.
            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
                )
            val outcome = world.engine.sync()

            assertTrue(outcome is SyncOutcome.MappingBroken)
            // `CLAUDE.md` §3.4 pauses the whole sync, not the missing habit's part of it: the run
            // returns before the import, so nothing is deactivated, renamed, re-coloured or dated.
            assertEquals(
                listOf(PropertyIds.READ, PropertyIds.JOURNAL),
                world.habits.current
                    .sortedBy { it.sortOrder }
                    .map { it.notionPropertyId },
            )
            assertTrue("no habit is hidden by a run that halted", world.habits.current.all { it.isActive })
            assertEquals(
                "and the history of the habit whose column is gone is untouched",
                1,
                world.completions.current.count { it.habitId == journal },
            )
            assertTrue("nothing was written to Notion", world.api.updated.isEmpty())
            assertEquals("and the edit is still owed", SyncState.PENDING_PUSH, world.dayAt(august20).syncState)
        }

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
     * A week of check-offs made with no network, oldest first — the state a drain starts from.
     *
     * None of them has a page in Notion yet, so the drain is seven creates. That is the honest shape
     * of the case: a day is only given a page id by the push that first delivers it.
     */
    private suspend fun offlineWeek(world: SyncWorld): List<LocalDate> {
        val week = (0L until 7L).map { offset -> august19.plusDays(offset) }
        week.forEach { date -> world.checkOffLocally(date, PropertyIds.READ) }
        return week
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

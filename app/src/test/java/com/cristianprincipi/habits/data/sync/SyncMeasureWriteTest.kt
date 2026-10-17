package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.dataSourceOf
import com.cristianprincipi.habits.core.testing.defaultMapping
import com.cristianprincipi.habits.core.testing.mappingWithWeight
import com.cristianprincipi.habits.core.testing.numberAt
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.core.testing.withWeight
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionPropertyValue
import com.cristianprincipi.habits.data.remote.notion.mapper.property
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * A number typed on the phone, all the way to Notion and back — M16.2a, ADR-156 §3.
 *
 * `SyncMeasurePullTest` is the read half and stays true for every number nobody typed here. This is
 * the write half: what is owed, what is sent, and the three-way merge that decides a number both
 * sides changed.
 *
 * ⭐⭐ **The two this whole design exists for** are [aNumberTypedLaterNeverUnticksAHabitTickedInNotion]
 * and [aCheckOffNeverResendsANumberNobodyTouched]. A number riding on the day's sync state — the
 * obvious design — fails the first one: the phone's later edit time would win the day, and send its
 * stale checkboxes over a habit the user ticked in Notion.
 *
 * Every local number goes through the real `MeasureRepositoryImpl` (`SyncWorld.numbers`), so these
 * also prove the write records what the engine expects.
 */
class SyncMeasureWriteTest {
    /** A connected world mapping `Weight`, with Aug 19 pulled: nothing ticked, 84.2 kg. */
    private suspend fun syncedWorld(): SyncWorld =
        SyncWorld().apply {
            connect(mappingWithWeight)
            api.givePage(unticked("p1", august19).withWeight(84.2))
            engine.sync()
        }

    private fun unticked(
        id: String,
        date: LocalDate,
        editedAtMillis: Long = 1_000,
        read: Boolean = false,
    ) = page(
        id,
        date,
        checks = mapOf(PropertyIds.READ to read, PropertyIds.JOURNAL to false),
        editedAtMillis = editedAtMillis,
    )

    private fun SyncWorld.notionWeight(pageId: String = "p1"): NotionPropertyValue? = api.page(pageId)?.numberAt()

    // ---- the write and the push ---------------------------------------------------------------

    @Test
    fun aNumberTypedOnThePhoneIsSentAndSurvivesTheRoundTrip() =
        runTest {
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6)

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 1, daysPushed = 1), outcome)
            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertTrue("nothing should be owed after the push", world.measures.currentEdits.isEmpty())

            // ⭐ And the next pull reads our own write back as the value it is, rather than as an
            // absent or unreadable column that would delete the reading.
            world.engine.sync()
            assertEquals(83.6, world.readingOn(august19))
            assertEquals(1, world.api.updated.size)
        }

    @Test
    fun aNumberAloneIsSentWithoutTheDaysCheckboxes() =
        runTest {
            // ⭐⭐ ADR-156's rule in its plainest form: the checkboxes are not owed, so they are not
            // in the body. Sending them would overwrite whatever Notion has with the phone's copy.
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6)

            world.engine.sync()

            val (pageId, body) = world.api.updated.single()
            assertEquals("p1", pageId)
            assertEquals(setOf(PropertyIds.WEIGHT), body.keys)
            assertEquals(SyncState.SYNCED, world.dayAt(august19).syncState)
        }

    @Test
    fun aNumberTypedLaterNeverUnticksAHabitTickedInNotion() =
        runTest {
            // ⭐⭐ The hazard the per-number record exists to remove. Notion's tick (7s) is OLDER than
            // the phone's number (9s). Had the number stamped the day `PENDING_PUSH` at 9s, the
            // day-level last-write-wins would pick the phone — and untick a habit nobody unticked.
            val world = syncedWorld()
            world.api.givePage(unticked("p1", august19, editedAtMillis = 7_000, read = true).withWeight(84.2))
            world.setNumberLocally(august19, 83.6, at = 9_000)

            world.engine.sync()

            assertEquals(NotionPropertyValue.Checkbox(true), world.api.page("p1")?.property(PropertyIds.READ))
            assertTrue(
                "the tick made in Notion should have reached the phone",
                world.completions.current.any { it.habitId == world.habitId(PropertyIds.READ) },
            )
            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertTrue("nothing was contested", world.discardedEdits.read().isEmpty())
        }

    @Test
    fun aCheckOffNeverResendsANumberNobodyTouched() =
        runTest {
            val world = syncedWorld()
            world.checkOffLocally(august19, PropertyIds.READ)

            world.engine.sync()

            val (_, body) = world.api.updated.single()
            assertEquals(setOf(PropertyIds.READ, PropertyIds.JOURNAL), body.keys)
        }

    @Test
    fun aCheckOffAndANumberOnOneDayGoOutAsOneUpdate() =
        runTest {
            val world = syncedWorld()
            world.checkOffLocally(august19, PropertyIds.READ)
            world.setNumberLocally(august19, 83.6)

            world.engine.sync()

            val (_, body) = world.api.updated.single()
            assertEquals(setOf(PropertyIds.READ, PropertyIds.JOURNAL, PropertyIds.WEIGHT), body.keys)
            assertEquals(SyncState.SYNCED, world.dayAt(august19).syncState)
            assertTrue(world.measures.currentEdits.isEmpty())
        }

    @Test
    fun aClearedNumberIsSentAsAnEmptyNumberAndNeverAsZero() =
        runTest {
            // ⚠⚠ The failure that would be invisible: a `0` in Notion is a weigh-in of nothing, and
            // every average over it — in Notion and, after the next pull, here — is quietly wrong.
            val world = syncedWorld()
            world.setNumberLocally(august19, null)
            assertNull("cleared locally at once", world.readingOn(august19))

            world.engine.sync()

            val (_, body) = world.api.updated.single()
            assertEquals(JsonNull, body.getValue(PropertyIds.WEIGHT).jsonObject["number"])
            assertEquals(NotionPropertyValue.Empty, world.notionWeight())
            assertTrue(world.measures.currentEdits.isEmpty())

            world.engine.sync()
            assertNull("the next pull keeps it cleared", world.readingOn(august19))
        }

    @Test
    fun anEditMadeOfflineWaitsAndIsSentWhenTheNetworkReturns() =
        runTest {
            val world = syncedWorld()
            world.api.failEverythingWith = NotionError.Network(IOException("airplane mode"))
            world.setNumberLocally(august19, 83.6)

            val offline = world.engine.sync()

            assertTrue("offline should fail the run, got $offline", offline is SyncOutcome.Failed)
            assertEquals("shown on the phone regardless", 83.6, world.readingOn(august19))
            assertEquals(84.2, world.owedOn(august19)?.baseValue)

            world.api.failEverythingWith = null
            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertTrue(world.measures.currentEdits.isEmpty())
        }

    @Test
    fun aNumberForADayNotionHasNoPageForCreatesOneWithTheDateAndTheNumberOnly() =
        runTest {
            val world = syncedWorld()
            world.setNumberLocally(august20, 83.1)

            world.engine.sync()

            assertEquals(
                setOf(PropertyIds.WEIGHT, PropertyIds.DATE),
                world.api.created
                    .single()
                    .keys,
            )
            assertEquals("created-1", world.dayAt(august20).notionPageId)
            assertTrue(world.measures.currentEdits.isEmpty())

            // Re-read on the next run as an ordinary page: no second page, no update, same number.
            world.engine.sync()
            assertEquals(1, world.api.created.size)
            assertTrue(world.api.updated.isEmpty())
            assertEquals(83.1, world.readingOn(august20))
        }

    // ---- the three-way merge ------------------------------------------------------------------

    @Test
    fun notionAlreadyHoldingOurNumberMeansNothingIsOwed() =
        runTest {
            // Merge case 1. Also what makes a push killed after Notion applied it harmless.
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6, at = 5_000)
            world.api.givePage(unticked("p1", august19, editedAtMillis = 9_000).withWeight(83.6))

            val outcome = world.engine.sync()

            assertEquals(0, (outcome as SyncOutcome.Success).daysPushed)
            assertTrue(world.api.updated.isEmpty())
            assertTrue(world.measures.currentEdits.isEmpty())
            assertTrue(world.discardedEdits.read().isEmpty())
        }

    @Test
    fun aPageEditedInNotionWithoutTouchingTheNumberKeepsOursOwed() =
        runTest {
            // Merge case 2, with a newer page: the page moved, the number did not. Last-write-wins
            // is not consulted at all — there is nothing to decide, so nothing is discarded.
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6, at = 5_000)
            world.api.givePage(unticked("p1", august19, editedAtMillis = 9_000, read = true).withWeight(84.2))

            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertTrue(world.discardedEdits.read().isEmpty())
        }

    @Test
    fun bothSidesChangedTheNumberAndThePhonesLaterEditWins() =
        runTest {
            // Merge case 3, local wins. Notion's 84.0 is replaced — and named, with both values.
            val world = syncedWorld()
            world.api.givePage(unticked("p1", august19, editedAtMillis = 3_000).withWeight(84.0))
            world.setNumberLocally(august19, 83.6, at = 5_000)

            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertEquals(83.6, world.readingOn(august19))
            val notice = world.discardedEdits.read().single()
            assertEquals(august19.toEpochDay(), notice.epochDay)
            assertEquals(SyncSide.REMOTE, notice.discardedSide)
            assertEquals(emptySet<String>(), notice.habitIds)
            assertEquals(DiscardedNumber(world.measureId(), discardedValue = 84.0, keptValue = 83.6), notice.number)
        }

    @Test
    fun bothSidesChangedTheNumberAndNotionsLaterEditWins() =
        runTest {
            // Merge case 3, remote wins: applied here, nothing sent, the phone's value named.
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6, at = 5_000)
            world.api.givePage(unticked("p1", august19, editedAtMillis = 9_000).withWeight(84.0))

            world.engine.sync()

            assertEquals(84.0, world.readingOn(august19))
            assertTrue(world.api.updated.isEmpty())
            assertTrue(world.measures.currentEdits.isEmpty())
            val notice = world.discardedEdits.read().single()
            assertEquals(SyncSide.LOCAL, notice.discardedSide)
            assertEquals(DiscardedNumber(world.measureId(), discardedValue = 83.6, keptValue = 84.0), notice.number)
        }

    @Test
    fun aConflictTheDeviceWonIsNotFoundAgainWhenThePushFails() =
        runTest {
            // Local wins re-bases the edit on the value it beat. Without that, a push that fails
            // leaves the next pull looking at the same three values and filing a second notice.
            val world = syncedWorld()
            world.api.givePage(unticked("p1", august19, editedAtMillis = 3_000).withWeight(84.0))
            world.setNumberLocally(august19, 83.6, at = 5_000)
            world.api.failUpdateWith = NotionError.Server(status = 503)

            world.engine.sync()
            world.api.failUpdateWith = null
            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertEquals(1, world.discardedEdits.read().size)
        }

    @Test
    fun aClearOnThePhoneAgainstANumberTypedInNotionIsAConflictLikeAnyOther() =
        runTest {
            // `null` is a value in the merge, not an absence of one: the phone cleared 84.2 while
            // Notion changed it to 84.0, so both sides changed it.
            val world = syncedWorld()
            world.api.givePage(unticked("p1", august19, editedAtMillis = 3_000).withWeight(84.0))
            world.setNumberLocally(august19, null, at = 5_000)

            world.engine.sync()

            assertEquals(NotionPropertyValue.Empty, world.notionWeight())
            val notice = world.discardedEdits.read().single()
            assertEquals(DiscardedNumber(world.measureId(), discardedValue = 84.0, keptValue = null), notice.number)
        }

    // ---- interruptions and failures -----------------------------------------------------------

    @Test
    fun aNumberChangedWhileItsPushIsInFlightStaysOwedAgainstWhatWasSent() =
        runTest {
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6, at = 5_000)
            var typedAgain = false
            world.api.duringWrite = {
                if (!typedAgain) {
                    typedAgain = true
                    world.setNumberLocally(august19, 83.4, at = 6_000)
                }
            }

            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertEquals(83.4, world.readingOn(august19))
            // ⭐ Based on what Notion holds NOW, so the next pull reads 83.6 as untouched rather
            // than as Notion having changed the number behind the phone's back.
            assertEquals(83.6, world.owedOn(august19)?.baseValue)

            world.api.duringWrite = null
            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.4), world.notionWeight())
            assertTrue(world.measures.currentEdits.isEmpty())
            assertTrue(world.discardedEdits.read().isEmpty())
        }

    @Test
    fun aDrainKilledPartWaySendsOnlyWhatIsStillOwed() =
        runTest {
            val world = syncedWorld()
            world.api.givePage(unticked("p2", august20, editedAtMillis = 1_000).withWeight(84.0))
            world.engine.sync()
            world.setNumberLocally(august19, 83.6)
            world.setNumberLocally(august20, 83.1)
            world.api.failWritesAfter = 1

            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight("p1"))
            assertEquals(NotionPropertyValue.Number(84.0), world.notionWeight("p2"))
            assertEquals(listOf(august20.toEpochDay()), world.measures.currentEdits.map { it.epochDay })

            world.api.failWritesAfter = null
            world.engine.sync()

            assertEquals(listOf("p1", "p2"), world.api.updated.map { (pageId, _) -> pageId })
            assertEquals(NotionPropertyValue.Number(83.1), world.notionWeight("p2"))
            assertTrue(world.measures.currentEdits.isEmpty())
        }

    @Test
    fun aRateLimitTheClientCouldNotClearKeepsTheNumberForTheNextRun() =
        runTest {
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6)
            world.api.failUpdateWith = NotionError.RateLimited(retryAfter = null)

            val limited = world.engine.sync()

            assertTrue("got $limited", limited is SyncOutcome.Failed)
            assertNotNull(world.owedOn(august19))

            world.api.failUpdateWith = null
            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
        }

    @Test
    fun aBrokenMappingSendsNothingAndKeepsTheEdit() =
        runTest {
            // `CLAUDE.md` §3.4: a column retyped upstream stops the run before anything is written.
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6)
            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.JOURNAL, "✒️ Journal", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
                    NotionPropertySchemaDto(PropertyIds.WEIGHT, "Weight", "rich_text"),
                )

            val outcome = world.engine.sync()

            assertTrue("got $outcome", outcome is SyncOutcome.MappingBroken)
            assertTrue(world.api.updated.isEmpty())
            assertEquals(84.2, world.owedOn(august19)?.baseValue)
            assertEquals(83.6, world.readingOn(august19))
        }

    @Test
    fun aNumberWhoseColumnLeftTheMappingWaitsAndGoesOutWhenItReturns() =
        runTest {
            // No validated property to send it to, so it is not sent — and not thrown away either.
            val world = syncedWorld()
            world.setNumberLocally(august19, 83.6)
            world.mappingStore.save(defaultMapping)

            world.engine.sync()

            assertTrue(world.api.updated.isEmpty())
            assertNotNull(world.owedOn(august19))

            world.mappingStore.save(mappingWithWeight)
            world.engine.sync()

            assertEquals(NotionPropertyValue.Number(83.6), world.notionWeight())
            assertTrue(world.measures.currentEdits.isEmpty())
        }

    // ---- a page that is gone ------------------------------------------------------------------

    @Test
    fun aPushToAGonePageRecreatesItWithTheNumberAndTheCheckboxes() =
        runTest {
            // A page made with the number alone would come back with every habit unticked, and the
            // next pull would apply that over the phone's ticks. So the day is owed in full.
            val world = syncedWorld()
            world.checkOffLocally(august19, PropertyIds.READ)
            world.engine.sync()
            world.setNumberLocally(august19, 83.6)
            world.api.givePage(world.api.page("p1")!!.copy(inTrash = true))
            world.api.failUpdateWith = NotionError.MissingOrNotShared

            world.engine.sync()

            assertNull(world.dayAt(august19).notionPageId)
            assertEquals(SyncState.PENDING_PUSH, world.dayAt(august19).syncState)

            world.api.failUpdateWith = null
            world.engine.sync()

            val body = world.api.created.single()
            assertEquals(setOf(PropertyIds.READ, PropertyIds.JOURNAL, PropertyIds.WEIGHT, PropertyIds.DATE), body.keys)
            val created = world.api.page("created-1")!!
            assertEquals(NotionPropertyValue.Checkbox(true), created.property(PropertyIds.READ))
            assertEquals(NotionPropertyValue.Number(83.6), created.numberAt())
            assertTrue(world.measures.currentEdits.isEmpty())
        }

    @Test
    fun aPageReportedGoneKeepsADayThatOwesANumber() =
        runTest {
            // `reconcileGonePage` deletes a day that owes nothing; one owing a number is an unpushed
            // edit like a check-off, and survives to recreate its page.
            val world = syncedWorld()
            world.api.includeTrashedInQuery = true
            world.setNumberLocally(august19, 83.6)
            world.api.givePage(unticked("p1", august19, editedAtMillis = 2_000).withWeight(84.2).copy(inTrash = true))

            world.engine.sync()

            assertEquals(83.6, world.readingOn(august19))
            assertEquals(1, world.api.created.size)
            assertEquals(NotionPropertyValue.Number(83.6), world.api.page("created-1")?.numberAt())
        }
}

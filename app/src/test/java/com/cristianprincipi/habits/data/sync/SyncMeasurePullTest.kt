package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.SyncWorld
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.dataSourceOf
import com.cristianprincipi.habits.core.testing.mappingWithWeight
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.core.testing.withEmptyWeight
import com.cristianprincipi.habits.core.testing.withUnreadableWeight
import com.cristianprincipi.habits.core.testing.withWeight
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import com.cristianprincipi.habits.domain.model.HabitColor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pull, for **numbers** — `Weight` and `Sleep` (M15.4).
 *
 * `SyncPullTest`'s fixtures deliberately leave `Weight` out of the mapping, because half of what
 * that engine has to get right is what it leaves alone. This file is the other half: the same
 * column, now mapped as a measure, and what the engine is supposed to do with it.
 *
 * ⭐ **The one that could go wrong invisibly is [aClearedNumberRemovesTheReadingRatherThanStoringZero].**
 * Every other case here fails loudly if it regresses. That one fails by producing an average that
 * is simply wrong, on a screen where nothing would look unwell.
 */
class SyncMeasurePullTest {
    private suspend fun measuringWorld(): SyncWorld = SyncWorld().apply { connect(mappingWithWeight) }

    @Test
    fun aNumberOnAPageBecomesAReading() =
        runTest {
            val world = measuringWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))

            world.engine.sync()

            val reading = world.measures.currentReadings.single()
            assertEquals(august19.toEpochDay(), reading.epochDay)
            assertEquals(84.2, reading.value, 0.0001)
        }

    @Test
    fun aClearedNumberRemovesTheReadingRatherThanStoringZero() =
        runTest {
            // ⚠⚠ **The failure this whole table is shaped against.** Notion sends `"number": null`
            // for a skipped weigh-in, which decodes to `NotionPropertyValue.Empty`. Read as `0.0`
            // it becomes a zero-kilo day that drags the average down, and there is nothing on any
            // screen that would look wrong while it did.
            val world = measuringWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))
            world.engine.sync()
            assertTrue("precondition: a reading was stored", world.measures.currentReadings.isNotEmpty())

            world.api.givePage(page("p1", august19, editedAtMillis = 2_000).withEmptyWeight())
            world.engine.sync()

            assertEquals(emptyList<Double>(), world.measures.currentReadings.map { it.value })
        }

    @Test
    fun aPageWithoutTheColumnAtAllHasNoReading() =
        runTest {
            // Absent is not the same input as empty — the page predates the column — but it is the
            // same answer: there is no reading for that day.
            val world = measuringWorld()
            world.api.givePage(page("p1", august19))

            world.engine.sync()

            assertTrue(world.measures.currentReadings.isEmpty())
        }

    @Test
    fun aValueThisBuildCannotReadLeavesTheStoredReadingAlone() =
        runTest {
            // ⭐ The opposite answer from empty, and for `applyCompletions`' reason: a value that
            // will not decode means the shape changed upstream, and deleting a real reading over it
            // would be inventing a gap in the user's history rather than recording one.
            val world = measuringWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))
            world.engine.sync()

            world.api.givePage(page("p1", august19, editedAtMillis = 2_000).withUnreadableWeight())
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
    fun aCorrectedNumberReplacesTheOneWeHeld() =
        runTest {
            // The reason `MeasureDao.upsertReading` upserts where `CompletionDao.insert` ignores.
            // Ignoring here would pin the first number ever synced.
            val world = measuringWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))
            world.engine.sync()

            world.api.givePage(page("p1", august19, editedAtMillis = 2_000).withWeight(83.1))
            world.engine.sync()

            assertEquals(
                83.1,
                world.measures.currentReadings
                    .single()
                    .value,
                0.0001,
            )
        }

    @Test
    fun aNumberIsReadEvenOnADayThatStillOwesAPush() =
        runTest {
            // ⭐⭐ **The deliberate asymmetry with completions.** `applyCompletions` is gated on the
            // day being SYNCED so a pull cannot clobber a check-off this device has not sent yet.
            // A number is protected by its own edit row instead (M16.2, `SyncMeasureWriteTest`),
            // and this one has none — so a day that owes a push for a check-off must still get
            // today's weight. Gating it on the day would mean a reading that does not arrive until
            // the push does.
            val world = measuringWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)
            assertEquals(SyncState.PENDING_PUSH, world.days.find(august19.toEpochDay())?.syncState)

            world.api.givePage(page("p1", august19, editedAtMillis = 9_000).withWeight(84.2))
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
    fun aNumberTheMappingDoesNotNameIsNeverStored() =
        runTest {
            // `docs/notion-schema.md` §7 unchanged: a column in the schema is not the user asking
            // for it. `connectedWorld` maps no measures at all, and the page carries a weight.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))

            world.engine.sync()

            assertTrue(world.measures.currentReadings.isEmpty())
            assertTrue(world.measures.current.isEmpty())
        }

    @Test
    fun theMeasureItselfIsImportedWithItsUnitNameAndColour() =
        runTest {
            val world = measuringWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))

            world.engine.sync()

            val measure = world.measures.current.single()
            assertEquals("Weight", measure.notionPropertyName)
            assertEquals("kg", measure.unit)
            // ⭐ Green, positionally: the user asked for Weight green and Sleep purple (M15.2), and
            // Weight is their first number column. Never by name — §3.4 forbids a codebase that
            // knows a column called `Weight` should be green.
            assertEquals(HabitColor.GREEN.name, measure.color)
            assertEquals(0, measure.sortOrder)
        }

    @Test
    fun readingsSpanTheDaysTheyWereRecordedOn() =
        runTest {
            val world = measuringWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))
            world.api.givePage(page("p2", august20, editedAtMillis = 2_000).withWeight(83.9))

            world.engine.sync()

            assertEquals(
                listOf(august19.toEpochDay() to 84.2, august20.toEpochDay() to 83.9),
                world.measures.currentReadings
                    .sortedBy { it.epochDay }
                    .map { it.epochDay to it.value },
            )
        }

    @Test
    fun aRenamedNumberColumnIsNotADeletedOne() =
        runTest {
            // ⭐ ADR-038 reaching the measures: the mapping is keyed on the property id, so a rename
            // upstream hands us a new label for the Stats tile and nothing else. The alternative —
            // reading a rename as a deletion — would pause the sync and tell the user their column
            // was gone.
            val world = measuringWorld()
            world.api.givePage(page("p1", august19).withWeight(84.2))
            world.engine.sync()

            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.JOURNAL, "✒️ Journal", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
                    NotionPropertySchemaDto(PropertyIds.WEIGHT, "Body weight", "number"),
                )
            world.api.givePage(page("p1", august19, editedAtMillis = 2_000).withWeight(83.5))
            val outcome = world.engine.sync()

            assertTrue("the sync should not have paused, got $outcome", outcome is SyncOutcome.Success)
            assertEquals(
                "Body weight",
                world.measures.current
                    .single()
                    .notionPropertyName,
            )
            // The row is the same one, so the reading it already had is still pointing at it.
            assertEquals(
                83.5,
                world.measures.currentReadings
                    .single()
                    .value,
                0.0001,
            )
        }
}

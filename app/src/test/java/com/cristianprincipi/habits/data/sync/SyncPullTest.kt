package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.dateProperty
import com.cristianprincipi.habits.core.testing.keyedByUnknownNames
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The pull: pages in, days out.
 *
 * Fixtures carry a `Weight` column the mapping does not name, because half of what this engine has
 * to get right is what it leaves alone.
 */
class SyncPullTest {
    // ---- the pull ------------------------------------------------------------------------------

    @Test
    fun aFirstRunImportsEveryDayAndItsCheckOffs() =
        runTest {
            val world = connectedWorld()
            world.api.givePage(
                page(
                    "p1",
                    august19,
                    checks =
                        mapOf(
                            PropertyIds.READ to true,
                            PropertyIds.JOURNAL to false,
                        ),
                ),
            )
            world.api.givePage(
                page("p2", august20, checks = mapOf(PropertyIds.READ to true, PropertyIds.JOURNAL to true)),
            )

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 2, daysPushed = 0), outcome)
            assertEquals(
                setOf(august19.toEpochDay(), august20.toEpochDay()),
                world.days.current.keys,
            )
            assertEquals(3, world.completions.current.size)
        }

    @Test
    fun aPageWhosePropertyNamesAllChangedIsStillReadCorrectly() =
        runTest {
            // ⭐⭐ **M7.1's claim, at engine level: the habit set is keyed on the property ID and
            // nothing in the pull path may touch a name.** `NotionMappingValidatorTest` pins the
            // validator on its own, and `SyncPushTest` pins that the write payload is keyed by id —
            // but between them sat the whole read path, tested only against pages whose keys came
            // from the schema the test had also handed the app. A name-keyed lookup would have
            // found those keys and passed.
            //
            // Here every key is a stranger, exactly as it would be the day after the user renames
            // their columns and before any schema fetch. The ids are unchanged, so the correct
            // answer is unchanged, and the day still lands with `Read` ticked and `Journal` not.
            val world = connectedWorld()
            world.api.givePage(
                page(
                    "p1",
                    august19,
                    checks = mapOf(PropertyIds.READ to true, PropertyIds.JOURNAL to false),
                ).keyedByUnknownNames(),
            )

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 1, daysPushed = 0), outcome)
            val read = world.habits.current.single { it.notionPropertyId == PropertyIds.READ }
            val journal = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }
            assertEquals(
                listOf(read.id),
                world.completions.current.map { it.habitId },
            )
            assertTrue(world.completions.current.none { it.habitId == journal.id })
        }

    @Test
    fun aDayIsStillIdentifiedWhenItsDateColumnHasBeenRenamed() =
        runTest {
            // The same trap one property along, and the more dangerous half: the day key is what
            // says *which day a page is*. Reading it by name would make every page in a renamed
            // database undatable — not a wrong day, no day at all — so the pull would import
            // nothing and report success.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)).keyedByUnknownNames())

            world.engine.sync()

            assertEquals(setOf(august19.toEpochDay()), world.days.current.keys)
        }

    @Test
    fun thePullWalksEveryCursorPage() =
        runTest {
            // FakeNotionApi pages two at a time, so five days is three round trips.
            val world = connectedWorld()
            repeat(5) { index ->
                world.api.givePage(
                    page(
                        "p$index",
                        august19.plusDays(index.toLong()),
                        checks =
                            mapOf(PropertyIds.READ to true),
                    ),
                )
            }

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 5, daysPushed = 0), outcome)
        }

    @Test
    fun theSecondRunOnlyAsksForWhatChangedSinceTheNewestPageItHas() =
        runTest {
            // CLAUDE.md §3.5: "pull incrementally... never re-fetch the entire database on every run".
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, editedAtMillis = 1_000))
            world.api.givePage(page("p2", august20, editedAtMillis = 2_000))
            world.engine.sync()

            world.engine.sync()

            assertEquals(listOf(null, Instant.ofEpochMilli(2_000)), world.api.queriedEditedAtOrAfter)
        }

    @Test
    fun anEditSharingTheWatermarksMinuteIsStillPulled() =
        runTest {
            // ⭐ M4.15, found against the real API — and not findable here before it, because
            // `FakeNotionApi.stamp()` advances a second per write, so no two fake edits ever
            // collided the way real ones do.
            //
            // Notion rounds `last_edited_time` DOWN TO THE MINUTE (`notion-api.md` §7a: 511 of 511
            // of the user's real pages came back minute-aligned), so two edits made 45 seconds
            // apart carry the same stamp. The resume point is `MAX(remoteLastEditedAt)`. Asked for
            // *exclusively*, every page sharing that minute is filtered out of this run and of
            // every run after it — the watermark can never move past them. An edit dropped in
            // silence, which `CLAUDE.md` §3.4 forbids outright.
            val theSameMinute = 60_000L
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, editedAtMillis = theSameMinute))
            world.engine.sync()

            // Edited later on the wall clock; stamped by Notion with the very same minute.
            world.api.givePage(page("p2", august20, editedAtMillis = theSameMinute))
            val outcome = world.engine.sync()

            assertTrue("the second run failed: $outcome", outcome is SyncOutcome.Success)
            assertEquals(
                setOf(august19.toEpochDay(), august20.toEpochDay()),
                world.days.current.keys,
            )
        }

    @Test
    fun aPageWithNoReadableDateIsSkippedAndCounted() =
        runTest {
            // A page with no date is not a day. Counted rather than swallowed, so a run that ignored
            // half the database cannot look like a clean one.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))
            world.api.givePage(NotionPageDto(id = "p2", lastEditedTime = "1970-01-01T00:00:09Z"))

            val outcome = world.engine.sync()

            assertEquals(SyncOutcome.Success(daysPulled = 1, daysPushed = 0, pagesSkipped = 1), outcome)
        }

    @Test
    fun aCheckboxThatWillNotDecodeLeavesTheLocalCompletionAlone() =
        runTest {
            // ADR-044 keeps the decoder total; this is the other half. A checkbox is never null in
            // Notion, so a value that will not read means the shape changed — and writing `false`
            // over it would invent a miss the user never had.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))
            world.engine.sync()

            world.api.givePage(
                NotionPageDto(
                    id = "p1",
                    lastEditedTime = "1970-01-01T00:01:00Z",
                    properties =
                        mapOf(
                            "Date" to dateProperty(august19),
                            "📖 Read" to
                                buildJsonObject {
                                    put("id", PropertyIds.READ)
                                    put("type", "checkbox")
                                    put("checkbox", "yes")
                                },
                        ),
                ),
            )
            world.engine.sync()

            assertEquals(1, world.completions.current.size)
        }

    @Test
    fun aPageReportedGoneRemovesTheDayItStoodFor() =
        runTest {
            // The defensive branch, and the fake has to be told to allow it: a real query never
            // returns a trashed page (docs/notion-api.md §14), which is why this is not how a
            // remote delete is normally found. Keeping a day Notion no longer has would be
            // inventing data.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19, checks = mapOf(PropertyIds.READ to true)))
            world.engine.sync()

            world.api.includeTrashedInQuery = true
            world.api.givePage(
                page("p1", august19, checks = mapOf(PropertyIds.READ to true), gone = true, editedAtMillis = 9_000),
            )
            world.engine.sync()

            assertTrue(world.days.current.isEmpty())
            assertTrue(world.completions.current.isEmpty())
        }

    @Test
    fun aGonePageWithAnUnpushedEditIsRecreatedRatherThanHonoured() =
        runTest {
            // The one conflict the resolver cannot decide: a deletion carries no timestamp, so
            // last-write-wins has nothing to weigh. Local wins by construction — this app cannot
            // delete in Notion (ADR-036), so honouring the deletion would destroy the check-off
            // with no way back, while ignoring it costs a page the user can delete again.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.checkOffLocally(august19, PropertyIds.READ)

            world.api.includeTrashedInQuery = true
            world.api.givePage(page("p1", august19, gone = true, editedAtMillis = 9_000))
            world.engine.sync()

            assertEquals(1, world.completions.current.size)
            val recreated =
                world.api.created
                    .single()
                    .getValue(PropertyIds.DATE)
            assertEquals(
                "2026-08-19",
                recreated.jsonObject["date"]
                    ?.jsonObject
                    ?.get("start")
                    ?.jsonPrimitive
                    ?.content,
            )
        }
}

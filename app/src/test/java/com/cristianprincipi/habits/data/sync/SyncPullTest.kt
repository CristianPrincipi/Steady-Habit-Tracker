package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.august20
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.dateProperty
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

            assertEquals(listOf(null, Instant.ofEpochMilli(2_000)), world.api.queriedEditedAfter)
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

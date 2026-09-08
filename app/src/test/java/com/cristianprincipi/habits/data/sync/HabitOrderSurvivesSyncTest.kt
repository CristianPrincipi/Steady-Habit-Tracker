package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.core.testing.PropertyIds
import com.cristianprincipi.habits.core.testing.august19
import com.cristianprincipi.habits.core.testing.connectedWorld
import com.cristianprincipi.habits.core.testing.dataSourceOf
import com.cristianprincipi.habits.core.testing.page
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The habit order against the sync engine — M15.5.
 *
 * ### ⭐⭐ This is the file that makes the feature real, and none of `MoveHabitTest` covers it
 *
 * Reordering is easy; making the order **survive** is the whole problem. `HabitImporter` used to
 * write `sortOrder = index` on every run, so an order the user set was correct until the next pull
 * and then silently gone — and gone in the worst way, because nothing failed and no screen said
 * anything. `sortOrder` is carried forward now, exactly as `color` is, and these are the tests that
 * hold that.
 *
 * A regression here would look like: the user reorders, uses the app happily, and finds their list
 * rearranged some minutes later with no explanation.
 */
class HabitOrderSurvivesSyncTest {
    private fun orderOf(habits: List<HabitEntity>): List<String> =
        habits.sortedBy(HabitEntity::sortOrder).map(HabitEntity::notionPropertyName)

    @Test
    fun aFirstImportNumbersTheHabitsFromZeroInTheMappingsOrder() =
        runTest {
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))

            world.engine.sync()

            assertEquals(
                listOf(0, 1),
                world.habits.current
                    .map(HabitEntity::sortOrder)
                    .sorted(),
            )
        }

    @Test
    fun aReorderSurvivesTheNextSync() =
        runTest {
            // ⭐⭐ **The one that matters.** Before M15.5 this failed: the second sync reassigned
            // `sortOrder` from the mapping's index and put the list back the way Notion's schema
            // ordered it.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            val read = world.habits.current.single { it.notionPropertyId == PropertyIds.READ }
            val journal = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }

            // The user moves Journal above Read, as the screen would.
            world.habits.setSortOrder(journal.id, 0)
            world.habits.setSortOrder(read.id, 1)

            world.api.givePage(page("p1", august19, editedAtMillis = 2_000))
            world.engine.sync()

            assertEquals(listOf("✒️ Journal", "📖 Read"), orderOf(world.habits.current))
        }

    @Test
    fun aRenameUpstreamDoesNotMoveAHabit() =
        runTest {
            // ⚠ The two are independent and it is worth pinning: the order is keyed on the row, and
            // the row is keyed on the property id (ADR-038). Renaming `Read` in Notion changes what
            // the habit is called and nothing else — it must not reshuffle a list the user set.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            val journal = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }
            world.habits.setSortOrder(journal.id, 0)
            world.habits.setSortOrder(
                world.habits.current
                    .single { it.notionPropertyId == PropertyIds.READ }
                    .id,
                1,
            )

            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Reading", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.JOURNAL, "✒️ Journal", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
                    NotionPropertySchemaDto(PropertyIds.WEIGHT, "Weight", "number"),
                )
            world.api.givePage(page("p1", august19, editedAtMillis = 2_000))
            world.engine.sync()

            assertEquals(listOf("✒️ Journal", "📖 Reading"), orderOf(world.habits.current))
        }

    @Test
    fun aNewHabitIsAppendedRatherThanInsertedIntoTheUsersOrder() =
        runTest {
            // ⚠ A column added in Notion must not land in the middle of a list the user arranged,
            // and it cannot take position 0 either — which is what `highestSortOrder() + 1` buys.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            world.habits.current.forEach { habit ->
                world.habits.setSortOrder(habit.id, if (habit.notionPropertyId == PropertyIds.JOURNAL) 0 else 1)
            }

            world.api.schema =
                dataSourceOf(
                    NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.JOURNAL, "✒️ Journal", "checkbox"),
                    NotionPropertySchemaDto("newone", "🆕 Quote", "checkbox"),
                    NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
                )
            world.connect(
                world.mappingStore.read()!!.let { mapping ->
                    mapping.copy(
                        habitPropertyIds =
                            mapping.habitPropertyIds +
                                NotionPropertyId("newone"),
                    )
                },
            )
            world.api.givePage(page("p1", august19, editedAtMillis = 2_000))
            world.engine.sync()

            assertEquals(listOf("✒️ Journal", "📖 Read", "🆕 Quote"), orderOf(world.habits.current))
        }

    @Test
    fun aHabitThatGoesAwayAndComesBackKeepsItsPlace() =
        runTest {
            // The row is kept rather than deleted when a property leaves the mapping (ADR-081), so
            // `previous` still finds it — which means unticking a habit to check something and
            // re-ticking it does not silently send it to the end of the list.
            val world = connectedWorld()
            world.api.givePage(page("p1", august19))
            world.engine.sync()
            val journal = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }
            world.habits.setSortOrder(journal.id, 0)
            world.habits.setSortOrder(
                world.habits.current
                    .single { it.notionPropertyId == PropertyIds.READ }
                    .id,
                1,
            )

            // Journal leaves the mapping…
            world.connect(
                world.mappingStore.read()!!.let { mapping ->
                    mapping.copy(habitPropertyIds = setOf(NotionPropertyId(PropertyIds.READ)))
                },
            )
            world.api.givePage(page("p1", august19, editedAtMillis = 2_000))
            world.engine.sync()
            // …and comes back.
            world.connect(
                world.mappingStore.read()!!.let { mapping ->
                    mapping.copy(
                        habitPropertyIds =
                            setOf(
                                NotionPropertyId(PropertyIds.READ),
                                NotionPropertyId(PropertyIds.JOURNAL),
                            ),
                    )
                },
            )
            world.api.givePage(page("p1", august19, editedAtMillis = 3_000))
            world.engine.sync()

            val back = world.habits.current.single { it.notionPropertyId == PropertyIds.JOURNAL }
            assertNotNull(back)
            assertEquals(0, back.sortOrder)
        }
}

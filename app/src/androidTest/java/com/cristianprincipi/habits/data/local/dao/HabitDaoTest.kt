package com.cristianprincipi.habits.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.testing.createInMemoryDatabase
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.domain.model.HabitColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * `HabitDao` against real SQLite.
 *
 * Instrumented rather than JVM, per ADR-017 — Room's generated code needs the Android SQLite
 * bindings. What is proved here is the **SQL**: the ordering clauses, the `NOT IN` deactivation and
 * the unique index. The repository above it is tested separately, and against fakes for speed.
 */
@RunWith(AndroidJUnit4::class)
class HabitDaoTest {
    private lateinit var database: HabitsDatabase
    private lateinit var dao: HabitDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        dao = database.habitDao()
    }

    @After
    fun tearDown() = database.close()

    private fun habit(
        id: String,
        sortOrder: Int,
        isActive: Boolean = true,
        propertyId: String = "prop-$id",
        color: HabitColor = HabitColor.BLUE,
    ) = HabitEntity(
        id = id,
        notionPropertyId = propertyId,
        notionPropertyName = "Habit $id",
        color = color.name,
        startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
        isActive = isActive,
        sortOrder = sortOrder,
    )

    @Test
    fun activeHabitsComeBackInSortOrder() =
        runTest {
            dao.upsert(listOf(habit("c", sortOrder = 2), habit("a", sortOrder = 0), habit("b", sortOrder = 1)))

            assertEquals(listOf("a", "b", "c"), dao.observeActive().first().map { it.id })
        }

    @Test
    fun activeHabitsExcludeTheInactiveOnes() =
        runTest {
            dao.upsert(listOf(habit("a", sortOrder = 0), habit("gone", sortOrder = 1, isActive = false)))

            assertEquals(listOf("a"), dao.observeActive().first().map { it.id })
        }

    @Test
    fun observeAllPutsActiveHabitsFirstThenSortOrder() =
        runTest {
            // M7.5 lists habits whose Notion property has disappeared, below the live ones. Their
            // local history may be the only copy left — deleting a property destroys the column.
            dao.upsert(
                listOf(
                    habit("gone", sortOrder = 0, isActive = false),
                    habit("b", sortOrder = 2),
                    habit("a", sortOrder = 1),
                ),
            )

            assertEquals(listOf("a", "b", "gone"), dao.observeAll().first().map { it.id })
        }

    @Test
    fun upsertUpdatesAnExistingHabitRatherThanDuplicatingIt() =
        runTest {
            dao.upsert(listOf(habit("a", sortOrder = 0)))

            dao.upsert(listOf(habit("a", sortOrder = 5)))

            val rows = dao.observeAll().first()
            assertEquals(1, rows.size)
            assertEquals(5, rows.single().sortOrder)
        }

    @Test
    fun upsertOverwritesTheUsersChosenColour() =
        runTest {
            // Named loudly because the DAO's KDoc used to claim the opposite. An upsert writes
            // EVERY column, and colour exists nowhere but this device — so M4's pull path must read
            // the existing row and carry it forward, or lose it. This test is the proof, and it
            // will fail the day someone makes the pull path do that, which is when to change it.
            dao.upsert(listOf(habit("a", sortOrder = 0)))
            dao.setColor(habitId = "a", color = HabitColor.PURPLE.name)

            dao.upsert(listOf(habit("a", sortOrder = 0, color = HabitColor.BLUE)))

            assertEquals(
                HabitColor.BLUE.name,
                dao
                    .observeAll()
                    .first()
                    .single()
                    .color,
            )
        }

    @Test
    fun deactivateAllExceptMarksTheMissingOnesInactiveAndKeepsThem() =
        runTest {
            // Never a delete: the rows are the only surviving record of a column Notion destroyed.
            dao.upsert(listOf(habit("a", sortOrder = 0), habit("b", sortOrder = 1), habit("c", sortOrder = 2)))

            dao.deactivateAllExcept(listOf("a", "c"))

            val rows = dao.observeAll().first().associateBy { it.id }
            assertEquals(3, rows.size)
            assertEquals(true, rows.getValue("a").isActive)
            assertEquals(false, rows.getValue("b").isActive)
            assertEquals(true, rows.getValue("c").isActive)
        }

    @Test
    fun deactivateAllExceptWithAnEmptyListDeactivatesEverything() =
        runTest {
            // The shape of a sync that finds no checkbox properties at all — a renamed database, or
            // a mapping that has come adrift. Nothing is deleted, so the history survives the scare.
            dao.upsert(listOf(habit("a", sortOrder = 0), habit("b", sortOrder = 1)))

            dao.deactivateAllExcept(emptyList())

            assertEquals(listOf(false, false), dao.observeAll().first().map { it.isActive })
        }

    @Test
    fun setColorTouchesOnlyTheHabitNamed() =
        runTest {
            dao.upsert(listOf(habit("a", sortOrder = 0), habit("b", sortOrder = 1)))

            dao.setColor(habitId = "a", color = HabitColor.GREEN.name)

            val rows = dao.observeAll().first().associateBy { it.id }
            assertEquals(HabitColor.GREEN.name, rows.getValue("a").color)
            assertEquals(HabitColor.BLUE.name, rows.getValue("b").color)
        }

    @Test
    fun aSecondHabitClaimingTheSameNotionPropertyIdIsSILENTLYDROPPED() =
        runTest {
            // **A hazard for M4, found by this test on 2026-08-22 (ADR-035).** The unique index on
            // notionPropertyId is real, but @Upsert does not surface it: Room catches the
            // constraint failure on insert and falls back to UPDATE ... WHERE id = ?. The new row's
            // primary key does not exist, so the update matches nothing, and the call returns
            // normally having written nothing at all.
            //
            // No exception, no return value, no log. A pull that somehow produced two habits
            // pointing at one Notion property would drop one on the floor — exactly the silent data
            // loss CLAUDE.md §3.4 forbids. M4's pull path must detect duplicate property ids
            // ITSELF, before it upserts, because the database will not do it for us.
            dao.upsert(listOf(habit("a", sortOrder = 0, propertyId = "shared")))

            dao.upsert(listOf(habit("b", sortOrder = 1, propertyId = "shared")))

            val rows = dao.observeAll().first()
            assertEquals(listOf("a"), rows.map { it.id })
            assertEquals(0, rows.single().sortOrder)
        }

    @Test
    fun theUniqueIndexItselfIsRealAndDoesReject() =
        runTest {
            // Proof that the index exists and bites — it is only @Upsert's error handling that
            // hides it. A plain INSERT still fails, which is what the test above is contrasted with.
            dao.upsert(listOf(habit("a", sortOrder = 0, propertyId = "shared")))

            assertThrows(SQLiteConstraintException::class.java) {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO habits (id, notionPropertyId, notionPropertyName, color, " +
                        "startDateEpochDay, isActive, sortOrder) VALUES ('b', 'shared', 'b', 'BLUE', 0, 1, 1)",
                )
            }
        }

    @Test
    fun anEmptyTableObservesAsAnEmptyListNotAsNothing() =
        runTest {
            // The first-run state. A flow that never emits would leave the UI on its loading
            // spinner forever instead of showing an empty state.
            assertEquals(emptyList<HabitEntity>(), dao.observeActive().first())
        }
}

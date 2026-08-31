package com.cristianprincipi.habits.feature.managehabits

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeHabitRepository
import com.cristianprincipi.habits.core.testing.FakeNotionSetupRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.core.testing.testHabit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.GetHabits
import com.cristianprincipi.habits.domain.usecase.GetNotionDatabaseUrl
import com.cristianprincipi.habits.domain.usecase.SetHabitColor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the habit list is told, and what its one control does.
 *
 * The fakes are the point of the shape here: the ViewModel takes three use cases, so a test wires
 * three one-line classes over two fakes and never has to arrange a Notion connection.
 *
 * ⚠ **Nothing here tests a hidden habit, and nothing can** (ADR-081). A habit whose property has
 * left the mapped set is filtered out by `HabitDao.observeActive`, and no use case above it can ask
 * for one — so the rule is pinned where it is enforced: `HabitRepositoryImplTest` for the read, and
 * `SyncEngineTest` for a habit leaving the set and coming back with its history.
 */
class ManageHabitsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val connection = FakeNotionSetupRepository()

    private fun viewModel(habits: FakeHabitRepository) =
        ManageHabitsViewModel(
            getHabits = GetHabits(habits),
            getNotionDatabaseUrl = GetNotionDatabaseUrl(connection),
            setHabitColor = SetHabitColor(habits),
        )

    @Test
    fun theLinkOutToNotionArrivesWhenTheAppHasLearnedIt() =
        runTest {
            val habits = FakeHabitRepository(habits = listOf(testHabit()))
            connection.notionDatabaseUrl.value = "https://www.notion.so/8f2c41d97b5e40a3ac6e1d9f0b73e254"

            viewModel(habits).uiState.test {
                val state = awaitItem() as ManageHabitsUiState.Content
                assertEquals("https://www.notion.so/8f2c41d97b5e40a3ac6e1d9f0b73e254", state.notionUrl)
            }
        }

    @Test
    fun aConnectionThatHasNotSyncedYetHasNoLinkAndStillListsItsHabits() =
        runTest {
            // Not an error state and not a loading one: the habits are in Room whether or not the
            // app has been told where the database lives. The screen disables the button in place.
            val habits = FakeHabitRepository(habits = listOf(testHabit()))

            viewModel(habits).uiState.test {
                val state = awaitItem() as ManageHabitsUiState.Content
                assertNull(state.notionUrl)
                assertEquals(1, state.habits.size)
            }
        }

    @Test
    fun pickingAColourWritesItAndTheListComesBackWearingIt() =
        runTest {
            // Both halves matter. The write is the screen's only job; the second emission is what
            // proves the store is observed rather than read once — M7.3's finding, that eleven
            // colour tests wrote and then read and none watched a live subscriber find out.
            val habits = FakeHabitRepository(habits = listOf(testHabit(color = HabitColor.BLUE)))
            val viewModel = viewModel(habits)

            viewModel.uiState.test {
                assertEquals(HabitColor.BLUE, (awaitItem() as ManageHabitsUiState.Content).habits.single().color)

                viewModel.onEvent(
                    ManageHabitsEvent.ColorPicked(habitId = HabitId("read"), color = HabitColor.ORANGE),
                )

                assertEquals(HabitColor.ORANGE, (awaitItem() as ManageHabitsUiState.Content).habits.single().color)
            }
            assertEquals(listOf(HabitId("read") to HabitColor.ORANGE), habits.colorWrites)
        }

    @Test
    fun theScreenStartsAtLoadingRatherThanAtAnEmptyList() =
        runTest {
            // An empty list is a real answer — a connection whose first import has not landed — so
            // showing it before Room has spoken would state something the app does not yet know.
            val habits = FakeHabitRepository()

            assertTrue(viewModel(habits).uiState.value is ManageHabitsUiState.Loading)
        }
}

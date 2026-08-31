package com.cristianprincipi.habits.feature.notionsetup

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeNotionSetupRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.SavedNotionMapping
import com.cristianprincipi.habits.domain.model.SyncFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Changing the habit set without disconnecting — **M7.8, the last step in M7**.
 *
 * The cases below are the two ADR-079 said the app could not recover from: a column **added** in
 * Notion that could never be adopted, and a column **deleted** in Notion that halted sync with no
 * way out but a disconnect. Both are now one screen.
 *
 * ⚠ **[itNeverAsksForOrTouchesTheToken] is the one that matters most.** The whole reason this screen
 * exists rather than sending the user back through setup is that re-connecting costs a paste of a
 * credential only they have (`CLAUDE.md` §3.6).
 *
 * `MainDispatcherRule` runs unconfined, so the load launched in `init` has already finished by the
 * time each test body starts and `uiState.value` is the loaded screen. Only [EditMappingViewModel.saved]
 * needs Turbine, being a one-shot channel rather than state.
 */
class EditMappingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNotionSetupRepository()

    /** The user's real shape: a date column, checkbox habits, and a number that can be neither. */
    private fun schema(vararg habitIds: String) =
        listOf(NotionProperty(DATE, "Date", "date", NotionPropertyRole.DayKey)) +
            habitIds.map { id -> NotionProperty(id, "📖 $id", "checkbox", NotionPropertyRole.Habit) } +
            NotionProperty("weight", "Weight", "number", NotionPropertyRole.Unavailable)

    private fun connected(
        mapped: List<String> = listOf(READ, JOURNAL),
        schema: List<NotionProperty> = schema(READ, JOURNAL),
    ) {
        repository.existingMapping = SavedNotionMapping(SOURCE, DATE, mapped)
        repository.propertiesResult = NotionSetupResult.Success(schema)
    }

    private fun viewModel() = EditMappingViewModel(repository)

    @Test
    fun itReadsTheSchemaOfTheDatabaseTheAppIsConnectedTo() =
        runTest {
            // Not a remembered copy: the reason to open this screen is that Notion has changed, so
            // a cached schema would offer the columns that existed the last time it was set up.
            connected()

            viewModel()

            assertEquals(listOf(SOURCE), repository.loadedSources)
        }

    @Test
    fun aColumnAddedInNotionSinceSetupArrivesUnticked() =
        runTest {
            // ⭐ ADR-051's rule, and the case that made M7.8 necessary. `Diet` is new in the schema
            // and is NOT ticked: only the user can say whether a new checkbox is a habit or a
            // `Reviewed?` flag, and assuming would make the app start writing `false` into it.
            connected(mapped = listOf(READ, JOURNAL), schema = schema(READ, JOURNAL, DIET))

            val state = viewModel().uiState.value

            assertEquals(setOf(READ, JOURNAL), state.habitPropertyIds)
            assertEquals(listOf(READ, JOURNAL, DIET), state.habitCandidates.map { it.id })
            assertTrue("nothing is dropped by opening the screen", state.habitsBeingDropped.isEmpty())
        }

    @Test
    fun aHabitWhoseColumnIsGoneIsNotOfferedAndIsNotReportedAsOneTheUserIsDropping() =
        runTest {
            // The other half of the drift: `Journal` was mapped and its column has been deleted
            // upstream. It cannot be ticked because it no longer exists — and it must not be named
            // in the warning either, which is about what *this selection* would stop syncing.
            connected(mapped = listOf(READ, JOURNAL), schema = schema(READ))

            val state = viewModel().uiState.value

            assertEquals(setOf(READ), state.habitPropertyIds)
            assertTrue(state.habitsBeingDropped.isEmpty())
        }

    @Test
    fun untickingAMappedHabitNamesItAsOneThatWillStopSyncing() =
        runTest {
            // ⭐ The sentence the user chose on 2026-08-31: named, and only when it applies.
            connected()
            val viewModel = viewModel()

            viewModel.onEvent(NotionSetupEvent.HabitToggled(JOURNAL))

            assertEquals(
                listOf(JOURNAL),
                viewModel.uiState.value.habitsBeingDropped
                    .map { it.id },
            )
        }

    @Test
    fun tickingItBackSaysNothingAgain() =
        runTest {
            // The note is state, not a warning that has been issued: undoing the untick withdraws
            // it. A toast could not do this, which is why it is not one.
            connected()
            val viewModel = viewModel()
            viewModel.onEvent(NotionSetupEvent.HabitToggled(JOURNAL))

            viewModel.onEvent(NotionSetupEvent.HabitToggled(JOURNAL))

            assertTrue(
                viewModel.uiState.value.habitsBeingDropped
                    .isEmpty(),
            )
        }

    @Test
    fun savingWritesTheNewMappingInCandidateOrderAndLeavesTheScreen() =
        runTest {
            connected(mapped = listOf(READ, JOURNAL), schema = schema(READ, JOURNAL, DIET))
            val viewModel = viewModel()

            viewModel.onEvent(NotionSetupEvent.HabitToggled(DIET))
            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)

            viewModel.saved.test { awaitItem() }
            // Candidate order, not tick order — the order becomes `HabitEntity.sortOrder`, and the
            // user expects the list they were just reading, top to bottom.
            assertEquals(
                listOf(SavedNotionMapping(SOURCE, DATE, listOf(READ, JOURNAL, DIET))),
                repository.savedMappings,
            )
        }

    @Test
    fun anImportThatFailsKeepsTheScreenAndSaysWhy() =
        runTest {
            // ⚠ The mapping is saved either way, which is correct — the next successful sync will
            // apply it. But the habit list behind this screen is still the old one until then, so
            // leaving would read as a save that did nothing.
            connected()
            repository.importResult = NotionSetupResult.Failure(SyncFailure.Offline)
            val viewModel = viewModel()

            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)

            assertEquals(SyncFailure.Offline, viewModel.uiState.value.failure)
            assertEquals("and the mapping was still stored", 1, repository.savedMappings.size)
            viewModel.saved.test { expectNoEvents() }
        }

    @Test
    fun itNeverAsksForOrTouchesTheToken() =
        runTest {
            // ⭐⭐ The reason this screen exists at all. Sending the user back through setup to
            // change one checkbox would cost them a paste of a credential only they have
            // (`CLAUDE.md` §3.6), and would overwrite the stored one on the way.
            connected()
            val viewModel = viewModel()

            viewModel.onEvent(NotionSetupEvent.HabitToggled(JOURNAL))
            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)
            viewModel.saved.test { awaitItem() }

            assertTrue(repository.savedTokens.isEmpty())
            assertEquals("", viewModel.uiState.value.token)
        }

    @Test
    fun aSchemaThatWillNotLoadOffersAWayToAskAgain() =
        runTest {
            // Offline on open. The banner says so, and the screen is not a dead end: the retry is
            // the same schema read, which is all this screen ever loads.
            repository.existingMapping = SavedNotionMapping(SOURCE, DATE, listOf(READ))
            repository.propertiesResult = NotionSetupResult.Failure(SyncFailure.Offline)
            val viewModel = viewModel()

            assertEquals(SyncFailure.Offline, viewModel.uiState.value.failure)
            assertTrue(
                "and nothing is drawn that would claim the database has no columns",
                viewModel.uiState.value.properties
                    .isEmpty(),
            )

            repository.propertiesResult = NotionSetupResult.Success(schema(READ))
            viewModel.onEvent(NotionSetupEvent.DatabaseConfirmed)

            assertEquals(setOf(READ), viewModel.uiState.value.habitPropertyIds)
            assertNull("the banner goes when the retry works", viewModel.uiState.value.failure)
        }

    @Test
    fun aDisconnectRacingTheScreenSaysSoRatherThanRenderingNothing() =
        runTest {
            // Not reachable from the UI — the route is only offered to a configured app — but a
            // blank screen with no explanation is the worst of the available answers.
            repository.existingMapping = null

            assertEquals(SyncFailure.NotConnected, viewModel().uiState.value.failure)
        }

    private companion object {
        const val SOURCE = "0b1d3f7a95c84e62b8a0f4d2e6c15938"
        const val DATE = "M%3BBw"
        const val READ = "ZI%40W"
        const val JOURNAL = "b%3DdC"
        const val DIET = "Yr%3Aq"
    }
}

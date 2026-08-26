package com.cristianprincipi.habits.feature.notionsetup

import com.cristianprincipi.habits.core.testing.FakeNotionSetupRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SavedNotionMapping
import com.cristianprincipi.habits.domain.model.SyncFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The wizard's behaviour, with the network and the stores faked away.
 *
 * The two things worth pinning here are the ones a later session could plausibly "simplify" into
 * something worse: **the token never survives in the state**, and **pre-ticking happens on the first
 * run only**.
 */
class NotionSetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNotionSetupRepository()

    // Constructed lazily, so that it is built from inside the test body rather than while the class
    // is being instantiated: the ViewModel now collects in `init`, and a rule has not yet swapped
    // `Dispatchers.Main` at field-initialisation time.
    private val viewModel by lazy { NotionSetupViewModel(repository) }

    private val state get() = viewModel.uiState.value

    private val date = NotionProperty("d", "Date", "date", NotionPropertyRole.DayKey)
    private val read = NotionProperty("r", "📖 Read", "checkbox", NotionPropertyRole.Habit)
    private val journal = NotionProperty("j", "✒️ Journal", "checkbox", NotionPropertyRole.Habit)
    private val progress = NotionProperty("p", "Progress", "formula", NotionPropertyRole.Unavailable)

    private val habitsDatabase = NotionSource("ds-1", "Habits")

    private fun reachDatabaseStep(vararg sources: NotionSource = arrayOf(habitsDatabase)) {
        repository.sourcesResult = NotionSetupResult.Success(sources.toList())
        viewModel.onEvent(NotionSetupEvent.TokenChanged("ntn_pretend"))
        viewModel.onEvent(NotionSetupEvent.TokenSubmitted)
    }

    private fun reachMappingStep(vararg properties: NotionProperty = arrayOf(date, read, journal, progress)) {
        reachDatabaseStep()
        repository.propertiesResult = NotionSetupResult.Success(properties.toList())
        viewModel.onEvent(NotionSetupEvent.DatabaseConfirmed)
    }

    // --- the token ---------------------------------------------------------------------------

    @Test
    fun theWizardStartsOnTheTokenStepWithNothingSent() =
        runTest {
            assertEquals(NotionSetupStep.Token, state.step)
            assertFalse(state.canContinueFromToken)
            assertTrue(repository.savedTokens.isEmpty())
        }

    @Test
    fun aTypedTokenEnablesContinue() =
        runTest {
            viewModel.onEvent(NotionSetupEvent.TokenChanged("ntn_pretend"))

            assertTrue(state.canContinueFromToken)
        }

    @Test
    fun theTokenIsGoneFromTheStateOnceItIsSubmitted() =
        runTest {
            // ⚠ CLAUDE.md §3.6. A UiState that keeps the token has put it somewhere that will
            // eventually be printed — a crash report, a recomposition trace, a debug log.
            reachDatabaseStep()

            assertEquals("", state.token)
            assertEquals(listOf("ntn_pretend"), repository.savedTokens)
        }

    @Test
    fun aBlankTokenIsNeverSubmitted() =
        runTest {
            viewModel.onEvent(NotionSetupEvent.TokenSubmitted)

            assertTrue(repository.savedTokens.isEmpty())
            assertEquals(NotionSetupStep.Token, state.step)
        }

    // --- choosing the database ---------------------------------------------------------------

    @Test
    fun theOnlyDatabaseIsPreselectedBecauseThereIsNothingToChoose() =
        runTest {
            reachDatabaseStep(habitsDatabase)

            assertEquals(NotionSetupStep.ChooseDatabase, state.step)
            assertEquals("ds-1", state.selectedDatabaseId)
        }

    @Test
    fun oneOfSeveralDatabasesIsNotChosenForTheUser() =
        runTest {
            reachDatabaseStep(habitsDatabase, NotionSource("ds-2", "Reading log"))

            assertEquals(null, state.selectedDatabaseId)
            assertFalse(state.canContinueFromDatabase)
        }

    @Test
    fun aFailureKeepsTheUserWhereTheyAreAndSaysWhy() =
        runTest {
            // The not-shared case, which is the one this screen exists for. It must not advance:
            // there is nothing to show on the next step.
            repository.sourcesResult = NotionSetupResult.Failure(SyncFailure.DatabaseNotShared)
            viewModel.onEvent(NotionSetupEvent.TokenChanged("ntn_pretend"))
            viewModel.onEvent(NotionSetupEvent.TokenSubmitted)

            assertEquals(NotionSetupStep.Token, state.step)
            assertEquals(SyncFailure.DatabaseNotShared, state.failure)
        }

    @Test
    fun anUnreadableLinkIsAFieldErrorRatherThanABanner() =
        runTest {
            // Nothing was sent, so nothing about Notion is wrong. Showing the sharing instruction
            // here would be the app's most useful sentence, spent on the wrong problem.
            reachDatabaseStep()
            repository.linkResult = NotionSetupResult.UnreadableLink
            viewModel.onEvent(NotionSetupEvent.LinkFieldOpened)
            viewModel.onEvent(NotionSetupEvent.DatabaseLinkChanged("my habits database"))
            viewModel.onEvent(NotionSetupEvent.DatabaseLinkSubmitted)

            assertTrue(state.isLinkUnreadable)
            assertEquals(null, state.failure)
        }

    @Test
    fun editingTheLinkClearsItsError() =
        runTest {
            reachDatabaseStep()
            repository.linkResult = NotionSetupResult.UnreadableLink
            viewModel.onEvent(NotionSetupEvent.DatabaseLinkChanged("nonsense"))
            viewModel.onEvent(NotionSetupEvent.DatabaseLinkSubmitted)

            viewModel.onEvent(NotionSetupEvent.DatabaseLinkChanged("nonsense2"))

            assertFalse(state.isLinkUnreadable)
        }

    // --- the mapping, and the pre-ticking rule -----------------------------------------------

    @Test
    fun theOnlyDateColumnBecomesTheDayKey() =
        runTest {
            // Not a guess: with one candidate it is the only possible answer, and it is visibly
            // selected for the user to disagree with.
            reachMappingStep()

            assertEquals(NotionSetupStep.MapProperties, state.step)
            assertEquals("d", state.dayKeyPropertyId)
        }

    @Test
    fun aFirstRunTicksEveryCheckboxColumn() =
        runTest {
            // ⭐ The user's decision, 2026-08-23. Their database is all-habits, so this is one tap.
            reachMappingStep()

            assertEquals(setOf("r", "j"), state.habitPropertyIds)
            assertTrue(state.canSaveMapping)
        }

    @Test
    fun aRepeatRunTicksOnlyWhatWasMappedBefore() =
        runTest {
            // ⭐ The other half, and the point of it: a column added in Notion since last time
            // arrives UNTICKED, so the user is asked rather than assumed at. Assuming would quietly
            // turn a `Reviewed?` flag into a habit the app writes `false` into.
            repository.existingMapping = SavedNotionMapping("ds-1", "d", listOf("r"))

            reachMappingStep()

            assertEquals(setOf("r"), state.habitPropertyIds)
        }

    @Test
    fun aMappingForADifferentDatabaseCountsAsAFirstRun() =
        runTest {
            // The saved answers describe someone else's columns; carrying them over would tick ids
            // that do not exist in this schema.
            repository.existingMapping = SavedNotionMapping("ds-OTHER", "zzz", listOf("qqq"))

            reachMappingStep()

            assertEquals(setOf("r", "j"), state.habitPropertyIds)
        }

    @Test
    fun aHabitWhoseColumnWasDeletedDoesNotComeBackAsATickAgainstNothing() =
        runTest {
            repository.existingMapping = SavedNotionMapping("ds-1", "d", listOf("r", "deleted-one"))

            reachMappingStep()

            assertEquals(setOf("r"), state.habitPropertyIds)
        }

    @Test
    fun aReadOnlyColumnIsNeverACandidateForAnything() =
        runTest {
            // CLAUDE.md §3.2 — prevent it being picked rather than fail at sync time.
            reachMappingStep()

            assertEquals(listOf(progress), state.unavailableProperties)
            assertFalse(progress.id in state.habitPropertyIds)
            assertFalse(state.habitCandidates.contains(progress))
        }

    @Test
    fun untickingEveryHabitBlocksTheSave() =
        runTest {
            // An empty mapping is representable and syncs nothing. NotionPropertyMapping says the
            // screen that collects them is where that rule belongs; this is it.
            reachMappingStep()

            viewModel.onEvent(NotionSetupEvent.HabitToggled("r"))
            viewModel.onEvent(NotionSetupEvent.HabitToggled("j"))

            assertTrue(state.habitPropertyIds.isEmpty())
            assertFalse(state.canSaveMapping)
        }

    // --- finishing ----------------------------------------------------------------------------

    @Test
    fun savingWritesTheMappingInTheOrderTheColumnsAreShown() =
        runTest {
            // Not the tick order: the user reads the list top to bottom, and this becomes
            // HabitEntity.sortOrder.
            reachMappingStep()
            viewModel.onEvent(NotionSetupEvent.HabitToggled("r"))
            viewModel.onEvent(NotionSetupEvent.HabitToggled("r"))
            repository.importResult = NotionSetupResult.Success(12)

            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)

            assertEquals(listOf(SavedNotionMapping("ds-1", "d", listOf("r", "j"))), repository.savedMappings)
            assertEquals(NotionSetupStep.Done, state.step)
            assertEquals(12, state.importedDays)
        }

    @Test
    fun anImportThatFailedDoesNotClaimToHaveFinished() =
        runTest {
            reachMappingStep()
            repository.importResult = NotionSetupResult.Failure(SyncFailure.Offline)

            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)

            assertEquals(NotionSetupStep.MapProperties, state.step)
            assertEquals(SyncFailure.Offline, state.failure)
        }

    @Test
    fun zeroDaysIsStillASuccess() =
        runTest {
            // A database whose pages all predate the app imports nothing and is correctly connected.
            reachMappingStep()
            repository.importResult = NotionSetupResult.Success(0)

            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)

            assertEquals(NotionSetupStep.Done, state.step)
        }

    // --- going back ---------------------------------------------------------------------------

    @Test
    fun backFromTheMappingDropsTheSchemaItWasBuiltFrom() =
        runTest {
            // The reason to go back is to pick a different database, and ticks against the previous
            // one's columns would be ticks against ids the new schema has never heard of.
            reachMappingStep()

            viewModel.onEvent(NotionSetupEvent.BackPressed)

            assertEquals(NotionSetupStep.ChooseDatabase, state.step)
            assertTrue(state.properties.isEmpty())
            assertTrue(state.habitPropertyIds.isEmpty())
            assertEquals(null, state.dayKeyPropertyId)
        }

    @Test
    fun backFromTheFirstStepGoesNowhere() =
        runTest {
            viewModel.onEvent(NotionSetupEvent.BackPressed)

            assertEquals(NotionSetupStep.Token, state.step)
        }

    @Test
    fun tryingAgainClearsTheLastFailureFirst() =
        runTest {
            // The banner means "the last thing you tried failed", not "something failed once".
            repository.sourcesResult = NotionSetupResult.Failure(SyncFailure.Offline)
            viewModel.onEvent(NotionSetupEvent.TokenChanged("ntn_pretend"))
            viewModel.onEvent(NotionSetupEvent.TokenSubmitted)
            assertEquals(SyncFailure.Offline, state.failure)

            repository.sourcesResult = NotionSetupResult.Success(listOf(habitsDatabase))
            viewModel.onEvent(NotionSetupEvent.TokenChanged("ntn_pretend"))
            viewModel.onEvent(NotionSetupEvent.TokenSubmitted)

            assertEquals(null, state.failure)
            assertEquals(NotionSetupStep.ChooseDatabase, state.step)
        }

    // --- coming back after a disconnect ---------------------------------------------------------

    @Test
    fun disconnectingStartsTheWizardOver() =
        runTest {
            // ⭐ M4.18, found on the device rather than here. This ViewModel is activity-scoped, so
            // it survives being swapped away and came back after a disconnect still on Done: a dead
            // end reading "Connected, 513 days imported" at someone who had just disconnected.
            reachMappingStep()
            repository.importResult = NotionSetupResult.Success(513)
            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)
            assertEquals(NotionSetupStep.Done, state.step)

            repository.disconnect(alsoDeleteLocalData = false)

            assertEquals(NotionSetupStep.Token, state.step)
            assertEquals(0, state.importedDays)
            assertTrue(state.properties.isEmpty())
            assertTrue(state.databases.isEmpty())
        }

    @Test
    fun aRunInProgressIsNotResetByBeingUnconfigured() =
        runTest {
            // The reset watches the *transition*, not the value. `isConfigured` is false for the
            // whole of a normal first run, so resetting on the value would wipe the wizard between
            // steps — which is every run, rather than the one case this exists for.
            reachMappingStep()

            assertEquals(NotionSetupStep.MapProperties, state.step)
            assertTrue(state.properties.isNotEmpty())
        }
}

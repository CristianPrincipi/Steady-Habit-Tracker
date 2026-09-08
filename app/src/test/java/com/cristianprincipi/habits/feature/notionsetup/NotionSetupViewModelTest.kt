package com.cristianprincipi.habits.feature.notionsetup

import com.cristianprincipi.habits.core.testing.FakeNotionSetupRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SavedMeasure
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
    private val weight = NotionProperty("w", "⚖️ Weight", "number", NotionPropertyRole.Measure)
    private val sleep = NotionProperty("s", "😴 Sleep", "number", NotionPropertyRole.Measure)

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

    // --- coming back to a half-finished wizard (M13.7) ------------------------------------------

    /**
     * ⭐⭐ **Constructing this ViewModel *is* the process death.**
     *
     * A test cannot kill its own process, but it does not need to: a ViewModel holds nothing but
     * memory, so "the process died and the user came back" is exactly "a fresh instance, over the
     * storage the old one left behind". That is the same reasoning
     * `WeekCheckOffPersistenceTest` uses to rebuild its stack from the file.
     *
     * ⚠⚠ **Why this is the defect and not a nicety.** The wizard is a **gate around the whole app**
     * (ADR-032): no mapping, no shell. `saveToken` stores the token at step one, two steps before
     * the user has chosen anything — the repository's own KDoc says so — and §3.3 then sends them
     * *out of the app*, into Notion, to share the database with the connection. Backgrounding the
     * app in the middle of setup is not an edge case here; **it is the documented flow.** If the
     * system reclaims the process while they are gone, they come back to "paste your token", the
     * app is holding a perfectly good one it will not show them, and ADR-107 records that Notion
     * shows a token once. That is a lock-out, not an inconvenience.
     */
    @Test
    fun aWizardRebuiltOverAStoredTokenDoesNotAskForItAgain() =
        runTest {
            repository.pretendTokenAlreadyStored()
            repository.sourcesResult = NotionSetupResult.Success(listOf(habitsDatabase))

            assertEquals(NotionSetupStep.ChooseDatabase, state.step)
            assertEquals(listOf(habitsDatabase), state.databases)
            // Nothing was pasted, so nothing may be saved: the resume must reuse the stored token
            // rather than write one back.
            assertTrue(repository.savedTokens.isEmpty())
        }

    /**
     * ⚠ **The resume must fail back to the token step, not past it.**
     *
     * A stored token that Notion now rejects — revoked, or rotated in the workspace — is the one
     * case where asking for a token again is the *right* thing to do. The resume therefore runs the
     * same `thenShowDatabases` path a fresh paste does, so a failure leaves the step where it was
     * and puts the reason on screen instead of stranding the user on an empty database list.
     */
    @Test
    fun aStoredTokenThatNoLongerWorksLeavesTheWizardOnTheTokenStepSayingWhy() =
        runTest {
            repository.pretendTokenAlreadyStored()
            repository.sourcesResult = NotionSetupResult.Failure(SyncFailure.TokenRejected)

            assertEquals(NotionSetupStep.Token, state.step)
            assertEquals(SyncFailure.TokenRejected, state.failure)
        }

    // --- the token ---------------------------------------------------------------------------

    /**
     * The control for the two above: with **no** stored token this is a first run, and the resume
     * must not fire — no step change and, just as importantly, no network call spent looking for
     * databases a token that does not exist could not reach.
     */
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

    // --- measures, and the rule that is NOT the pre-ticking rule (M15.4) ------------------------

    @Test
    fun aFirstRunTicksNoMeasureAtAll() =
        runTest {
            // ⭐⭐ **The one that matters, and it is the deliberate opposite of
            // [aFirstRunTicksEveryCheckboxColumn] directly above.** Pre-ticking every checkbox is
            // safe because of a fact about *this* database: every checkbox column in it is a habit.
            // No such fact holds for numbers — a number column could be a target, a page count, an
            // ID, a rating. "Average this on the Stats screen" is a claim only the user can make.
            reachMappingStep(date, read, journal, weight, sleep)

            assertTrue(state.measurePropertyIds.isEmpty())
            // ⚠ And it does not block the save: measures are optional, habits are not.
            assertTrue(state.canSaveMapping)
        }

    @Test
    fun aNumberColumnIsOfferedAsAMeasureRatherThanAsUnavailable() =
        runTest {
            reachMappingStep(date, read, journal, weight, progress)

            assertEquals(listOf(weight), state.measureCandidates)
            assertEquals(listOf(progress), state.unavailableProperties)
            assertFalse(state.habitCandidates.contains(weight))
        }

    @Test
    fun aRepeatRunRestoresTheMeasuresAndTheUnitsTypedForThem() =
        runTest {
            repository.existingMapping =
                SavedNotionMapping("ds-1", "d", listOf("r"), listOf(SavedMeasure("w", "kg")))

            reachMappingStep(date, read, journal, weight, sleep)

            assertEquals(setOf("w"), state.measurePropertyIds)
            assertEquals("kg", state.measureUnits["w"])
        }

    @Test
    fun aMeasureWhoseColumnWasDeletedDoesNotComeBackAsATickAgainstNothing() =
        runTest {
            // [aHabitWhoseColumnWasDeletedDoesNotComeBackAsATickAgainstNothing]'s twin.
            repository.existingMapping =
                SavedNotionMapping("ds-1", "d", listOf("r"), listOf(SavedMeasure("gone", "kg")))

            reachMappingStep(date, read, journal, weight)

            assertTrue(state.measurePropertyIds.isEmpty())
        }

    @Test
    fun aUnitSurvivesUntickingAndReTickingTheColumn() =
        runTest {
            // The units map is keyed rather than paired with the tick, so a user who unticks
            // `Weight` to read the section note and ticks it straight back does not retype `kg`.
            reachMappingStep(date, read, journal, weight)
            viewModel.onEvent(NotionSetupEvent.MeasureToggled("w"))
            viewModel.onEvent(NotionSetupEvent.MeasureUnitChanged("w", "kg"))

            viewModel.onEvent(NotionSetupEvent.MeasureToggled("w"))
            viewModel.onEvent(NotionSetupEvent.MeasureToggled("w"))

            assertEquals("kg", state.measureUnits["w"])
        }

    @Test
    fun savingSendsOnlyTickedMeasuresAndTrimsTheirUnits() =
        runTest {
            // ⚠ Two things at once, and both are one-liners that would be invisible if wrong: an
            // untyped-into but unticked column must not be saved, and a stray space would render
            // as `84.2  kg` on the Stats tile for ever.
            reachMappingStep(date, read, journal, weight, sleep)
            viewModel.onEvent(NotionSetupEvent.MeasureToggled("w"))
            viewModel.onEvent(NotionSetupEvent.MeasureUnitChanged("w", " kg "))
            // Typed for `Sleep` and then never ticked — it must not reach the mapping.
            viewModel.onEvent(NotionSetupEvent.MeasureUnitChanged("s", "h"))
            repository.importResult = NotionSetupResult.Success(3)

            viewModel.onEvent(NotionSetupEvent.MappingSubmitted)

            assertEquals(listOf(SavedMeasure("w", "kg")), repository.savedMappings.single().measures)
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

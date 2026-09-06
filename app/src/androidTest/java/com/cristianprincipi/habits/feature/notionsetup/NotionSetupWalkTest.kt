package com.cristianprincipi.habits.feature.notionsetup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.testing.FakeNotionSetupRepository
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ⭐⭐ M13.8 — **the connection wizard, walked end to end on a device: token → database → mapping →
 * done.**
 *
 * ### Why this is one of the three critical paths, and why it had none of this
 *
 * `CLAUDE.md` §6 names three paths to cover: "check off a habit, create a habit, navigate to
 * detail". **Two of those no longer exist** — ADR-010 removed habit creation, and M1.10 renamed the
 * detail screen to `feature/history/`. The app's real three are the check-off, the shell, and
 * **connecting to Notion**, and the first two were already covered end to end
 * (`WeekCheckOffPersistenceTest`, `StreakOnScreenTest`, `HabitsShellNavigationTest`).
 *
 * The wizard was not. `NotionSetupEmptyDatabaseTest` renders **one hand-built `NotionSetupUiState`**
 * with `onEvent = {}` — a snapshot of a step, not a walk through them — and `MainActivityTest`
 * asserts only that Hilt builds the graph and the first screen draws. So the one screen a first run
 * cannot skip, the gate in front of the entire app, had never had its steps driven in a composition.
 *
 * ### What is real here and what is arranged
 *
 * Real `NotionSetupViewModel`, real `NotionSetupContent`, real events, real step transitions, real
 * pre-ticking rules. Only the repository is a fake — the same `FakeNotionSetupRepository` the JVM
 * suite already trusts, which is why it now lives in `src/sharedTest` rather than being copied
 * (see `app/build.gradle.kts`). A second fake would be a second thing to keep true.
 *
 * ⚠ **This deliberately does not go through Hilt or Ktor.** Doing so needs `hilt-android-testing`
 * and a custom instrumentation runner, which would change `testInstrumentationRunner` for all of the
 * existing instrumented tests to serve this one — the user's call, taken in M13.8. Those two layers
 * are covered elsewhere: `MainActivityTest` proves Hilt builds the real graph including the Ktor
 * client and the Keystore token store, and the sync suite drives a fake Notion API over real
 * request and response shapes.
 */
@RunWith(AndroidJUnit4::class)
class NotionSetupWalkTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val repository = FakeNotionSetupRepository()

    private val habitsDatabase = NotionSource("ds-1", "Habits")
    private val date = NotionProperty("d", "Date", "date", NotionPropertyRole.DayKey)
    private val read = NotionProperty("r", "Read", "checkbox", NotionPropertyRole.Habit)
    private val journal = NotionProperty("j", "Journal", "checkbox", NotionPropertyRole.Habit)

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /**
     * The wizard over a workspace that answers.
     *
     * The ViewModel is `remember`ed rather than hoisted so that the composition owns exactly one,
     * the way `NotionSetupScreen` does through `hiltViewModel()`.
     */
    private fun startWizard() {
        repository.sourcesResult = NotionSetupResult.Success(listOf(habitsDatabase))
        repository.propertiesResult = NotionSetupResult.Success(listOf(date, read, journal))
        repository.importResult = NotionSetupResult.Success(IMPORTED_DAYS)

        composeTestRule.setContent {
            val viewModel = remember { NotionSetupViewModel(repository) }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            HabitsTheme {
                NotionSetupContent(state = state, onEvent = viewModel::onEvent)
            }
        }
    }

    /**
     * Waits for a step to arrive.
     *
     * ⚠ Needed rather than `waitForIdle()`: every transition here is a `viewModelScope.launch`
     * behind a suspending repository call, and Compose's idling resource knows nothing about a
     * coroutine that is not part of the composition.
     */
    private fun awaitText(id: Int) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(string(id)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A control by its label, told apart from a heading of the same words by its click action. */
    private fun button(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(hasClickAction())

    @Test
    fun theWizardWalksFromAPastedTokenToAnImportedDatabase() {
        startWizard()

        // --- 1. the token step ---------------------------------------------------------------
        composeTestRule.onNodeWithText(string(R.string.setup_token_title)).assertExists()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.setup_token_label))
            .performTextInput("ntn_pretend")
        button(R.string.setup_token_continue).performClick()

        // --- 2. the database step ------------------------------------------------------------
        awaitText(R.string.setup_database_title)
        // One candidate, so the wizard preselects it — a list of one with nothing chosen is a
        // question with a single possible answer. Its name must still be on screen.
        composeTestRule.onNodeWithText(habitsDatabase.title).assertExists()
        button(R.string.setup_database_continue).performClick()

        // --- 3. the mapping step -------------------------------------------------------------
        awaitText(R.string.setup_mapping_title)
        // ⭐ First run, so every checkbox column arrives ticked (ADR-051). Unticking one is what
        // proves the events reach the ViewModel rather than the screen merely rendering a state
        // somebody handed it — the defect `NotionSetupEmptyDatabaseTest`'s `onEvent = {}` cannot see.
        composeTestRule.onNodeWithText(journal.name).performClick()
        button(R.string.setup_mapping_submit).performClick()

        // --- 4. done --------------------------------------------------------------------------
        awaitText(R.string.setup_done_title)

        val saved = repository.savedMappings.single()
        assertEquals(habitsDatabase.id, saved.sourceId)
        assertEquals(date.id, saved.dayKeyPropertyId)
        // Read survived, Journal was unticked on the way through.
        assertEquals(listOf(read.id), saved.habitPropertyIds)
        assertEquals(listOf("ntn_pretend"), repository.savedTokens)
    }

    private companion object {
        const val IMPORTED_DAYS = 513
        const val TIMEOUT_MS = 5_000L
    }
}

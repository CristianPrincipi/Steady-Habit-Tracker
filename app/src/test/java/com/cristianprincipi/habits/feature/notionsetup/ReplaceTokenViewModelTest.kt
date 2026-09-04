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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Replacing a rotated token without losing the setup around it — **M12.7**.
 *
 * This is [EditMappingViewModelTest]'s mirror, and it exists for the mirrored reason. That screen
 * repairs the mapping without costing the user a token; this one repairs the token without costing
 * them a database choice and a mapping that were never wrong.
 *
 * ⚠ **[itNeverTouchesTheMapping] is the one that matters most**, exactly as its twin's token test is.
 * The failure this screen answers — a token rotated or revoked in Notion — used to be recoverable
 * only by disconnecting, which threw away both halves of the connection to fix one of them.
 */
class ReplaceTokenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNotionSetupRepository()

    @Before
    fun setUp() {
        repository.existingMapping = SavedNotionMapping(SOURCE, DATE, listOf(READ))
        repository.propertiesResult =
            NotionSetupResult.Success(
                listOf(
                    NotionProperty(DATE, "Date", "date", NotionPropertyRole.DayKey),
                    NotionProperty(READ, "Read", "checkbox", NotionPropertyRole.Habit),
                ),
            )
    }

    private fun viewModel() = ReplaceTokenViewModel(repository)

    private fun ReplaceTokenViewModel.paste(token: String) {
        onEvent(NotionSetupEvent.TokenChanged(token))
        onEvent(NotionSetupEvent.TokenSubmitted)
    }

    @Test
    fun aTokenThatWorksIsSavedAndTheScreenLeaves() =
        runTest {
            val viewModel = viewModel()

            viewModel.saved.test {
                viewModel.paste(NEW_TOKEN)

                awaitItem()
                assertEquals(listOf(NEW_TOKEN), repository.savedTokens)
            }
        }

    /**
     * ⭐ The whole point of the screen, in one assertion.
     *
     * The database and the property mapping are untouched, so the user pays for a rotated token with
     * one paste rather than with the whole of setup.
     */
    @Test
    fun itNeverTouchesTheMapping() =
        runTest {
            viewModel().paste(NEW_TOKEN)

            assertTrue(repository.savedMappings.isEmpty())
        }

    /**
     * The token is proved against **the database this app is set up against**, not against whatever
     * the token happens to be able to see.
     */
    @Test
    fun theTokenIsProvedAgainstTheDatabaseAlreadyChosen() =
        runTest {
            viewModel().paste(NEW_TOKEN)

            assertEquals(listOf(SOURCE), repository.loadedSources)
        }

    @Test
    fun aRefusedTokenSaysSoAndTheScreenStays() =
        runTest {
            repository.propertiesResult = NotionSetupResult.Failure(SyncFailure.TokenRejected)
            val viewModel = viewModel()

            viewModel.saved.test {
                viewModel.paste(NEW_TOKEN)

                expectNoEvents()
                assertEquals(SyncFailure.TokenRejected, viewModel.uiState.value.failure)
            }
        }

    /**
     * ⚠ The field is cleared whether or not the token worked, exactly as the wizard clears it.
     *
     * `CLAUDE.md` §3.6: a credential does not sit on screen after it has been handed over, and a
     * refused one is the thing being replaced rather than something to correct in place.
     */
    @Test
    fun theFieldDoesNotKeepTheCredentialAfterwards() =
        runTest {
            repository.propertiesResult = NotionSetupResult.Failure(SyncFailure.TokenRejected)
            val viewModel = viewModel()

            viewModel.paste(NEW_TOKEN)

            assertEquals("", viewModel.uiState.value.token)
        }

    @Test
    fun anEmptyFieldSubmitsNothingAtAll() =
        runTest {
            val viewModel = viewModel()

            viewModel.paste("")

            assertTrue(repository.savedTokens.isEmpty())
            assertNull(viewModel.uiState.value.failure)
        }

    private companion object {
        const val SOURCE = "source-1"
        const val DATE = "date-1"
        const val READ = "read-1"
        const val NEW_TOKEN = "ntn_a_replacement_that_is_not_real"
    }
}

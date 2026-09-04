package com.cristianprincipi.habits.feature.notionsetup

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.domain.model.NotionSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The database picker with nothing to pick — M12.3, and the empty state the app was missing.
 *
 * ### ⚠⚠ Why this one mattered more than the others
 *
 * `CLAUDE.md` §3.3 warns that an integration cannot see a database until that database is shared
 * with it, and that the failure "typically returns a *not found* style error rather than a
 * permission error". The app handles that error end to end — `NotionError.MissingOrNotShared` to
 * `SyncFailure.DatabaseNotShared` to a banner.
 *
 * **This is the other half of the same trap, and it is not an error at all.** A valid token that has
 * been shared with nothing returns a *successful* search carrying zero results. Nothing fails, so no
 * banner fires, and the picker had nothing to draw: the user met "These are the databases your token
 * can reach" above an empty space, on the one screen a first run cannot skip.
 *
 * Continue was already disabled correctly, so this was never a dead end — it was a silence.
 */
@RunWith(AndroidJUnit4::class)
class NotionSetupEmptyDatabaseTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showPicker(databases: List<NotionSource>) {
        composeTestRule.setContent {
            HabitsTheme {
                NotionSetupContent(
                    state =
                        NotionSetupUiState(
                            step = NotionSetupStep.ChooseDatabase,
                            databases = databases,
                        ),
                    onEvent = {},
                )
            }
        }
    }

    @Test
    fun anEmptyPickerSaysWhatToDoInNotionRatherThanNothingAtAll() {
        showPicker(databases = emptyList())

        composeTestRule.onNodeWithText(string(R.string.setup_database_empty)).assertIsDisplayed()
    }

    /**
     * The half that was already right, pinned so it stays right.
     *
     * With nothing selected there is nothing to continue to, and `canContinueFromDatabase` is what
     * says so. A note that explained the emptiness while the button still invited a tap would be
     * worse than the silence it replaced.
     */
    @Test
    fun continueIsDisabledWhenThereIsNothingToChoose() {
        showPicker(databases = emptyList())

        composeTestRule.onNodeWithText(string(R.string.setup_database_continue)).assertIsNotEnabled()
    }

    /**
     * ⭐ The assertion that keeps the note honest.
     *
     * An empty-state note that renders whenever the screen does is not an empty state — it is a
     * paragraph. This is what fails if the `isEmpty()` guard is ever lost.
     */
    @Test
    fun aPickerWithDatabasesDoesNotExplainAnEmptyOne() {
        showPicker(
            databases =
                listOf(
                    NotionSource("a", "Habits"),
                    NotionSource("b", "Reading log"),
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.setup_database_empty)).assertDoesNotExist()
        composeTestRule.onNodeWithText("Habits").assertIsDisplayed()
    }
}

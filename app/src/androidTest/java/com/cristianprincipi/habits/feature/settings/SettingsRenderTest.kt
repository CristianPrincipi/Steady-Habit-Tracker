package com.cristianprincipi.habits.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Settings hub on a device, driven through its stateless half.
 *
 * `createComposeRule` rather than the Android one: [SettingsContent] takes its state as a parameter,
 * so the disconnected and never-synced cases can be set up exactly — both of which need a broken or
 * absent Notion connection to reach for real.
 *
 * **Every string is read from the same resources the screen uses**, so a test cannot pass against
 * wording that no longer exists.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        state: SettingsUiState,
        onOpenSyncStatus: () -> Unit = {},
        onOpenHabits: () -> Unit = {},
        onOpenAbout: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HabitsTheme {
                SettingsContent(
                    state = state,
                    onOpenSyncStatus = onOpenSyncStatus,
                    onOpenHabits = onOpenHabits,
                    onOpenAbout = onOpenAbout,
                )
            }
        }
    }

    /** `createComposeRule` has no activity, so the strings come from the app context. */
    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun topOf(id: Int) =
        composeTestRule
            .onNodeWithText(string(id))
            .fetchSemanticsNode()
            .positionInRoot
            .y

    /**
     * ⭐ The whole point of M11.1: the tab answers the question without a tap.
     *
     * Before this step Settings was a title and two rows — a user had to open the sync screen to
     * learn whether the app was connected at all.
     */
    @Test
    fun theTabSaysWhetherItIsConnectedWithoutBeingTapped() {
        show(SettingsUiState(connection = ConnectionSummary(isConnected = true, lastSynced = Elapsed.Minutes(3))))

        composeTestRule.onNodeWithText(string(R.string.sync_connected)).assertIsDisplayed()
    }

    /**
     * ⚠ Reachable despite the setup gate: `HabitsShell` routes on a stored mapping, not a stored
     * token, so a revoked token leaves this screen drawn with no connection behind it.
     */
    @Test
    fun aRevokedTokenLeavesTheCardSayingNotConnectedRatherThanNothing() {
        show(SettingsUiState())

        composeTestRule.onNodeWithText(string(R.string.sync_not_connected)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sync_not_connected_body)).assertIsDisplayed()
    }

    @Test
    fun aRunInProgressIsSaidOutLoudRatherThanShownAsAStaleTime() {
        show(
            SettingsUiState(
                connection = ConnectionSummary(isConnected = true, isSyncing = true, lastSynced = Elapsed.Hours(1)),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.sync_in_progress)).assertIsDisplayed()
    }

    /**
     * ⭐ The row order M11.1 reversed, pinned so it cannot drift back.
     *
     * "Notion sync" is the continuation of the sentence the card starts; the habit list between the
     * two would break it in half. Nothing renders differently if this order flips, which is exactly
     * why it needs an assertion rather than a comment.
     */
    @Test
    fun notionSyncSitsDirectlyUnderTheCardItContinues() {
        show(SettingsUiState(connection = ConnectionSummary(isConnected = true)))

        assertTrue(topOf(R.string.sync_connected) < topOf(R.string.settings_notion_sync))
        assertTrue(topOf(R.string.settings_notion_sync) < topOf(R.string.settings_habits))
        // ⭐ About is last (M11.8): the two rows above it are where something gets fixed, and
        // this one is where something gets read.
        assertTrue(topOf(R.string.settings_habits) < topOf(R.string.settings_about))
    }

    @Test
    fun everyRowGoesWhereItSays() {
        var syncStatusOpened = 0
        var habitsOpened = 0
        var aboutOpened = 0
        show(
            state = SettingsUiState(connection = ConnectionSummary(isConnected = true)),
            onOpenSyncStatus = { syncStatusOpened++ },
            onOpenHabits = { habitsOpened++ },
            onOpenAbout = { aboutOpened++ },
        )

        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_habits)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_about)).performClick()

        assertEquals(1, syncStatusOpened)
        assertEquals(1, habitsOpened)
        assertEquals(1, aboutOpened)
    }
}

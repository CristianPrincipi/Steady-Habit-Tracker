package com.cristianprincipi.habits.feature.syncstatus

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.EditSide
import com.cristianprincipi.habits.domain.model.MappingIssue
import com.cristianprincipi.habits.domain.model.SyncFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * The sync-status screen on a device, driven through its stateless half.
 *
 * `createComposeRule` rather than the Android one: [SyncStatusContent] takes its state as a
 * parameter, so every case below can be set up exactly — including the two that are hard to reach
 * on a real device, a rejected token and a day whose edit was thrown away.
 *
 * **Every string is read from the same resources the screen uses**, so a test cannot pass against
 * wording that no longer exists.
 */
@RunWith(AndroidJUnit4::class)
class SyncStatusRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val notice =
        DiscardedEditNotice(
            date = LocalDate.of(2026, 8, 21),
            habitNames = listOf("📖 Read"),
            habitCount = 1,
            discardedSide = EditSide.Local,
            resolvedAt = Instant.parse("2026-08-23T09:00:00Z"),
        )

    private fun show(
        state: SyncStatusUiState,
        onEvent: (SyncStatusEvent) -> Unit = {},
        onEditMapping: () -> Unit = {},
        onReplaceToken: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HabitsTheme {
                SyncStatusContent(
                    state = state,
                    onEvent = onEvent,
                    onEditMapping = onEditMapping,
                    onReplaceToken = onReplaceToken,
                )
            }
        }
    }

    /** `createComposeRule` has no activity, so the strings come from the app context. */
    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** Where the destructive button currently sits, so a test can assert it has not moved. */
    private fun disconnectPosition() =
        composeTestRule
            .onNodeWithText(string(R.string.sync_disconnect))
            .fetchSemanticsNode()
            .positionInRoot

    @Test
    fun aHealthyConnectionSaysWhenItLastSynced() {
        show(SyncStatusUiState(isConnected = true, lastSynced = Elapsed.Minutes(3)))

        composeTestRule.onNodeWithText(string(R.string.sync_connected)).assertIsDisplayed()
    }

    @Test
    fun anAppThatHasNeverSyncedSaysThatRatherThanNothing() {
        show(SyncStatusUiState(isConnected = true, lastSynced = null))

        composeTestRule.onNodeWithText(string(R.string.sync_never)).assertIsDisplayed()
    }

    @Test
    fun aHeldSyncSaysWhatHappensNextAndDisablesTheButtonItAnswersFor() {
        // ⭐ ADR-060, from watching this on the device in M4.18. The button must stay *in place*:
        // hiding it let "Disconnect from Notion" slide up into the pixels a second tap was headed
        // for. Displayed-but-disabled is the assertion that pins the fix.
        show(SyncStatusUiState(isConnected = true, isWaitingForNetwork = true, lastSynced = Elapsed.Hours(2)))

        composeTestRule
            .onNodeWithText(string(R.string.sync_waiting_for_network))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.sync_retry))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun theDestructiveButtonDoesNotMoveWhenASyncIsHeld() {
        // The hazard itself, rather than its cause: whatever the card is saying, Disconnect must
        // stay where the user last saw it.
        // Driven through one composition rather than two `show` calls: `setContent` may only be
        // called once, and the point is what the *same* screen does when its state changes under it.
        val state = mutableStateOf(SyncStatusUiState(isConnected = true, lastSynced = Elapsed.Hours(2)))
        composeTestRule.setContent {
            HabitsTheme {
                SyncStatusContent(
                    state = state.value,
                    onEvent = {},
                    onEditMapping = {},
                    onReplaceToken = {},
                )
            }
        }
        val settled = disconnectPosition()

        state.value = state.value.copy(isWaitingForNetwork = true)
        composeTestRule.waitForIdle()

        assertEquals(settled, disconnectPosition())
    }

    @Test
    fun aFailureRendersAsTheFixRatherThanTheFault() {
        // ⭐ CLAUDE.md §3.3's case. The sentence must send the user to Notion's sharing menu — the
        // whole reason NotionFailureBanner is shared with the setup wizard rather than duplicated.
        show(SyncStatusUiState(isConnected = true, failure = SyncFailure.DatabaseNotShared))

        composeTestRule
            .onNodeWithText(string(R.string.notion_failure_database_not_shared))
            .assertIsDisplayed()
    }

    @Test
    fun aBrokenMappingOffersTheOneThingThatCanFixIt() {
        // ⭐⭐ ADR-079's *"the diagnosis is good and the recovery is missing"*, closed by M7.8. This
        // screen already named the problem correctly and then offered a retry that would fail
        // identically and a disconnect that would cost the user their token. A mapped column is gone
        // upstream, and the only thing that helps is changing the mapping.
        var edited = false
        show(
            state =
                SyncStatusUiState(
                    isConnected = true,
                    failure = SyncFailure.MappingBroken(listOf(MappingIssue.HabitMissing("ZI%40W"))),
                ),
            onEditMapping = { edited = true },
        )

        composeTestRule.onNodeWithText(string(R.string.mapping_edit_action)).performClick()

        assertTrue(edited)
    }

    @Test
    fun everyOtherFailureLeavesTheMappingAlone() {
        // The route is offered for one failure, not as a general-purpose button. Offline is fixed by
        // waiting, and sending the user to re-pick their columns would be advice to break something
        // that is not broken.
        show(SyncStatusUiState(isConnected = true, failure = SyncFailure.Offline))

        composeTestRule.onNodeWithText(string(R.string.mapping_edit_action)).assertDoesNotExist()
    }

    @Test
    fun aDiscardedEditIsAdmittedTo() {
        // ⭐ The other half of CLAUDE.md §3.5: resolution may destroy an edit, but not silently.
        show(SyncStatusUiState(isConnected = true, discardedEdits = listOf(notice)))

        composeTestRule
            .onNodeWithText(string(R.string.sync_discarded_eyebrow))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("📖 Read").assertIsDisplayed()
    }

    @Test
    fun theDisconnectConfirmationStartsWithTheDataCheckboxOff() {
        // The safe direction, and CLAUDE.md §3.6's separate choice. A tick here deletes history that
        // may be the only copy left.
        show(SyncStatusUiState(isConnected = true, isConfirmingDisconnect = true))

        composeTestRule
            .onNodeWithText(string(R.string.sync_disconnect_delete_local))
            .assertIsOff()
    }

    @Test
    fun tickingTheDataCheckboxReportsItAndShowsTheWarning() {
        var toggled: Boolean? = null
        show(
            state = SyncStatusUiState(isConnected = true, isConfirmingDisconnect = true, willDeleteLocalData = true),
            onEvent = { event ->
                if (event is SyncStatusEvent.DeleteLocalDataToggled) toggled = event.delete
            },
        )

        composeTestRule
            .onNodeWithText(string(R.string.sync_disconnect_delete_local))
            .assertIsOn()
        composeTestRule
            .onNodeWithText(string(R.string.sync_disconnect_delete_local_warning))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(R.string.sync_disconnect_delete_local))
            .performClick()

        assertEquals(false, toggled)
    }

    @Test
    fun anUnconnectedAppOffersNoDisconnect() {
        show(SyncStatusUiState(isConnected = false))

        composeTestRule
            .onNodeWithText(string(R.string.sync_not_connected))
            .assertIsDisplayed()
    }

    /**
     * ⭐ M12.7, and the mirror of the mapping button's rule: the way out is offered **only** for the
     * failure it fixes.
     *
     * A permanent "paste a new token" row would invite a working connection to be overwritten by a
     * typo, because the token has to be saved before it can be tested (`ReplaceTokenViewModel`).
     */
    @Test
    fun aRejectedTokenIsOfferedAWayToReplaceIt() {
        show(SyncStatusUiState(isConnected = true, failure = SyncFailure.TokenRejected))

        composeTestRule.onNodeWithText(string(R.string.token_replace_action)).assertIsDisplayed()
    }

    @Test
    fun aStoredTokenThatIsGoneIsOfferedTheSameWayBack() {
        show(SyncStatusUiState(isConnected = false, failure = SyncFailure.NotConnected))

        composeTestRule.onNodeWithText(string(R.string.token_replace_action)).assertIsDisplayed()
    }

    /**
     * ⚠ A capability failure is not a token failure.
     *
     * Notion accepted the token and will not let it do this; the remedy is in the workspace's
     * permissions, which is what that failure's own sentence says. Offering a new token here would
     * send the user to fetch a credential that changes nothing.
     */
    @Test
    fun aTokenThatIsMerelyUnderprivilegedIsNotOfferedAReplacement() {
        show(SyncStatusUiState(isConnected = true, failure = SyncFailure.MissingCapability))

        composeTestRule.onNodeWithText(string(R.string.token_replace_action)).assertDoesNotExist()
    }

    @Test
    fun aHealthyConnectionIsNotInvitedToReplaceItsToken() {
        show(SyncStatusUiState(isConnected = true))

        composeTestRule.onNodeWithText(string(R.string.token_replace_action)).assertDoesNotExist()
    }
}

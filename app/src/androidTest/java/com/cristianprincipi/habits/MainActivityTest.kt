package com.cristianprincipi.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the app launches into the Notion connection wizard and it renders.
 *
 * ⚠ **The assertions are deliberately thin, and the reason has changed since M1.** This used to look
 * for a bootstrap placeholder; it now looks at the real first screen, because M4.12 hosts
 * `NotionSetupScreen` directly in `MainActivity` until M5's navigation host exists. When M5 lands,
 * this becomes a test about the navigation shell and the setup flow gets its own.
 *
 * What it actually proves is the thing only a device can: that Hilt builds `NotionSetupViewModel`
 * and everything under it — the repository, the Ktor client, the Keystore token store, the sync
 * engine — at the moment an `@AndroidEntryPoint` activity composes it. ADR-031 and ADR-049 track
 * that graph; this closes the **UI** half of it, which the sync worker could not.
 *
 * **Nothing here touches the network.** The screen renders its first step without sending anything,
 * which is exactly why the first step is a text field and not a request.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theConnectionWizardIsTheFirstThingShown() {
        val title = composeTestRule.activity.getString(R.string.setup_token_title)

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun continueIsDisabledUntilATokenIsTyped() {
        // The one rule the first step has, and the prototype's own (`docs/design-spec.md` §6.5:
        // disabled until the fields are non-empty). Nothing should be sent on an empty field.
        val continueLabel = composeTestRule.activity.getString(R.string.setup_token_continue)

        composeTestRule.onNodeWithText(continueLabel).assertIsNotEnabled()
    }
}

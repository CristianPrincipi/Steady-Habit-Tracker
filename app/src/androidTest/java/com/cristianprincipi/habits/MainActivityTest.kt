package com.cristianprincipi.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.testing.FreshInstallRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
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
 *
 * ### ⭐ M4.17 — the precondition is stated, not inherited
 *
 * Both assertions are about the **wizard**, and `HabitsAppRoot` only shows the wizard while setup is
 * unfinished; a configured app gets `SyncStatusScreen` instead, where neither node exists. So this
 * class passed for eleven steps on the strength of an accident — no device it had ever run on had
 * been connected to Notion by hand. The first one that was would have failed it, and the failure
 * would have read as a broken wizard rather than as a dirty device.
 *
 * ⚠ **And the routing key is the mapping, not the token.** M4.17 was written as "clear the auth
 * DataStore", which would not have fixed it: `HabitsAppRoot` routes on `isConfigured`, which
 * `NotionMappingStore` answers out of the **settings** file, while the token lives in its own
 * (`AuthDataStoreModule` — kept apart on purpose). [FreshInstallRule] clears both, which is what
 * "fresh install" means and what these assertions actually depend on.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * The clear has to happen **outside** the compose rule, not in an `@Before`.
     *
     * `createAndroidComposeRule` launches the activity from its own `before()`, and JUnit runs rules
     * outside `@Before` methods — so an `@Before` would land after the activity had already composed
     * against whatever was on disk. [FreshInstallRule] documents the rest.
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshInstallRule()).around(composeTestRule)

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

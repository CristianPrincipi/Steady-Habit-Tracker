package com.cristianprincipi.habits.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.gallery.ShellHarnessActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell on a device: the bar switches tabs, and sync status is reachable.
 *
 * Driven through [ShellHarnessActivity], a debug-only `@AndroidEntryPoint` that renders
 * `HabitsShell(isConfigured = true)`. Its KDoc records why the three more obvious routes do not
 * work — seeding the mapping, a Hilt `@EntryPoint` in `androidTest`, and replacing `MainActivity`'s
 * content — and each of those was tried and failed before this one.
 *
 * What the harness does **not** fake is the graph: every destination, the bar, the back stack and
 * the real `SyncStatusScreen` are the app's own. Only the gate is a parameter.
 */
@RunWith(AndroidJUnit4::class)
class HabitsShellNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ShellHarnessActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun theShellOpensOnWeekWithEveryTabPresent() {
        // Week, not Month — the user's decision (`docs/design-spec.md` §1). Getting the start
        // destination wrong is invisible to every other assertion here, because all five
        // destinations exist either way.
        composeTestRule.onNodeWithText(string(R.string.tab_week)).assertIsSelected()

        listOf(
            R.string.tab_month,
            R.string.tab_week,
            R.string.tab_year,
            R.string.tab_stats,
            R.string.tab_settings,
        ).forEach { label -> composeTestRule.onNodeWithText(string(label)).assertIsDisplayed() }
    }

    @Test
    fun tappingATabSelectsItAndDeselectsTheLast() {
        composeTestRule.onNodeWithText(string(R.string.tab_month)).performClick()

        composeTestRule.onNodeWithText(string(R.string.tab_month)).assertIsSelected()
        composeTestRule.onNodeWithText(string(R.string.tab_week)).assertIsNotSelected()
    }

    @Test
    fun settingsReachesSyncStatusAndBackReturnsToTheShell() {
        // ⭐ The reason this test exists. Deleting `HabitsAppRoot` in M5.2 removed the only thing
        // pointing at sync status, and a destination nothing navigates to is unreachable however
        // carefully it is declared. The user asked that it keep a way in (ADR-062).
        composeTestRule.onNodeWithText(string(R.string.tab_settings)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).performClick()

        // No token is stored, so the screen correctly reports the connection as absent. That it
        // renders at all is the point.
        composeTestRule.onNodeWithText(string(R.string.sync_not_connected)).assertIsDisplayed()

        Espresso.pressBack()

        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tab_settings)).assertIsSelected()
    }

    @Test
    fun theBarIsHiddenOnADestinationThatIsNotATab() {
        // A bar with nothing selected would be a bar lying about where the user is.
        composeTestRule.onNodeWithText(string(R.string.tab_settings)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).performClick()

        composeTestRule.onNodeWithText(string(R.string.tab_week)).assertDoesNotExist()
    }
}

package com.cristianprincipi.habits.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    /**
     * A bottom-nav item.
     *
     * ⚠ Matching on text alone stopped being enough when M5.3 gave the screens headers: "Week" is
     * now both a tab and a heading, and `onNodeWithText` fails outright on two matches. The tab is
     * the selectable one — that is what `NavigationBarItem` puts in semantics — and the heading is
     * the one with no click action.
     */
    private fun tab(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(isSelectable())

    /** A screen's own header title, as opposed to the tab of the same name. */
    private fun heading(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(hasNoClickAction())

    @Test
    fun theShellOpensOnWeekWithEveryTabPresent() {
        // Week, not Month — the user's decision (`docs/design-spec.md` §1). Getting the start
        // destination wrong is invisible to every other assertion here, because all five
        // destinations exist either way.
        tab(R.string.tab_week).assertIsSelected()

        listOf(
            R.string.tab_month,
            R.string.tab_week,
            R.string.tab_year,
            R.string.tab_stats,
            R.string.tab_settings,
        ).forEach { label -> tab(label).assertIsDisplayed() }
    }

    @Test
    fun tappingATabSelectsItAndDeselectsTheLast() {
        tab(R.string.tab_month).performClick()

        tab(R.string.tab_month).assertIsSelected()
        tab(R.string.tab_week).assertIsNotSelected()
    }

    @Test
    fun settingsReachesSyncStatusAndBackReturnsToTheShell() {
        // ⭐ The reason this test exists. Deleting `HabitsAppRoot` in M5.2 removed the only thing
        // pointing at sync status, and a destination nothing navigates to is unreachable however
        // carefully it is declared. The user asked that it keep a way in (ADR-062).
        tab(R.string.tab_settings).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).performClick()

        // No token is stored, so the screen correctly reports the connection as absent. That it
        // renders at all is the point.
        composeTestRule.onNodeWithText(string(R.string.sync_not_connected)).assertIsDisplayed()

        Espresso.pressBack()

        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).assertIsDisplayed()
        tab(R.string.tab_settings).assertIsSelected()
    }

    @Test
    fun theBarIsHiddenOnADestinationThatIsNotATab() {
        // A bar with nothing selected would be a bar lying about where the user is.
        tab(R.string.tab_settings).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).performClick()

        composeTestRule.onNodeWithText(string(R.string.tab_week)).assertDoesNotExist()
    }

    @Test
    fun everyTabLandsOnItsOwnScreen() {
        // ⭐ M5.3. Until the stubs existed, all five destinations rendered nothing, so a bar that
        // lit the right tab while showing the wrong screen would have looked identical. This is the
        // assertion that would have caught a copy-paste in the graph.
        heading(R.string.screen_title_week).assertIsDisplayed()

        tab(R.string.tab_month).performClick()
        heading(R.string.screen_title_month).assertIsDisplayed()

        tab(R.string.tab_year).performClick()
        heading(R.string.screen_title_year).assertIsDisplayed()

        tab(R.string.tab_stats).performClick()
        // "Statistics" in the header, "Stats" on the tab — the design's own asymmetry (§1).
        heading(R.string.screen_title_stats).assertIsDisplayed()

        tab(R.string.tab_settings).performClick()
        heading(R.string.screen_title_settings).assertIsDisplayed()
    }
}

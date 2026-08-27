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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * What proves the **Week** screen is the one on show.
     *
     * ⚠ **Not a heading, because Week no longer has one.** M6.3 replaced its `StubScreen` with the
     * real `WeekContent`, and `docs/design-spec.md` §1 puts the title in the screen *header* — which
     * **M6.4** builds. Four tests failed on this the moment Week stopped being a stub.
     *
     * The harness has no Notion connection, so Room is empty and Week correctly renders its empty
     * state. That string is the anchor until the header exists. **M6.4 should point this back at
     * the heading**, at which point Week matches the other four again.
     */
    private fun weekIsShowing() = composeTestRule.onNodeWithText(string(R.string.week_empty_title))

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

    // --- back (M5.4) -----------------------------------------------------------------------

    @Test
    fun backFromAnyTabReturnsToWeekRatherThanTheTabBefore() {
        // ⭐ The behaviour `docs/design-spec.md` §1 asks for: "system back exits from Week and
        // returns to Week from any other tab". `switchTab` pops to the start destination on every
        // switch, so three tab taps must still leave a stack one deep — the failure mode this pins
        // is a back stack that grows with every tab the user has ever visited, which is what
        // `navigate(route)` on its own would do.
        tab(R.string.tab_month).performClick()
        tab(R.string.tab_year).performClick()
        tab(R.string.tab_stats).performClick()

        Espresso.pressBack()

        weekIsShowing().assertIsDisplayed()
        tab(R.string.tab_week).assertIsSelected()
    }

    @Test
    fun backFromWeekLeavesTheApp() {
        // Week is the start destination, so there is nothing behind it. `pressBackUnconditionally`
        // rather than `pressBack`: the latter throws when back would leave the app, which is
        // precisely the case under test.
        // Held before the press: once the activity is gone, `composeTestRule.activity` throws
        // "Cannot run onActivity since Activity has been destroyed already" — which is itself the
        // answer, but not an assertion.
        val activity = composeTestRule.activity

        Espresso.pressBackUnconditionally()

        composeTestRule.waitUntil { activity.isFinishing || activity.isDestroyed }
        assertTrue(activity.isFinishing || activity.isDestroyed)
    }

    @Test
    fun backOutOfSyncStatusDoesNotDisturbTheTabBehindIt() {
        // A destination without a tab sits *on top of* the tab that opened it. Coming back should
        // land on Settings with Settings still selected — not on the start destination.
        tab(R.string.tab_month).performClick()
        tab(R.string.tab_settings).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).performClick()

        Espresso.pressBack()

        heading(R.string.screen_title_settings).assertIsDisplayed()
        tab(R.string.tab_settings).assertIsSelected()
    }

    @Test
    fun theShellClaimsBackOnlyWhenItHasSomewhereToGo() {
        // ⭐ This is the mechanism predictive back rides on, which is why it is worth pinning. The
        // platform can only animate a predictive gesture when the app has an *enabled* back callback
        // registered; `NavHost` enables one exactly while its back stack is more than one deep.
        //
        // It is also the pair of claims that keeps back honest at both ends: on the start
        // destination the app must NOT claim back, or it would swallow the gesture that leaves the
        // app; anywhere else it must, or the gesture would leave the app from the middle of it.
        assertFalse(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())

        tab(R.string.tab_month).performClick()
        // ⚠ The `waitForIdle` is load-bearing, and this test failed without it. `performClick`
        // syncs the composition, but `NavController` enables its callback from an observer that has
        // not necessarily run by then — so the assertion read a stale `false` and looked exactly
        // like a real defect. The behavioural test above passed the whole time, which is what said
        // otherwise.
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun everyTabLandsOnItsOwnScreen() {
        // ⭐ M5.3. Until the stubs existed, all five destinations rendered nothing, so a bar that
        // lit the right tab while showing the wrong screen would have looked identical. This is the
        // assertion that would have caught a copy-paste in the graph.
        weekIsShowing().assertIsDisplayed()

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

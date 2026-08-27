package com.cristianprincipi.habits.navigation

import android.content.Context
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.gallery.ShellHarnessActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M5.5 — the insets rule, asserted rather than looked at.
 *
 * ### The rule
 *
 * **The shell owns insets; a screen inside the graph takes the padding it is handed** (ADR-063).
 * `HabitsShell` puts every destination in a `Scaffold` whose content padding already clears the
 * status bar and the bottom bar, so no screen in the graph applies insets of its own.
 *
 * ### Both ways it can break, and both are covered
 *
 * A screen that applies **no** padding when the shell somehow stops supplying it slides under the
 * status bar — unreadable, and the reason the rule exists. A screen that applies its **own**
 * `safeDrawing` on top of the shell's is padded twice and drops a whole status bar down the page.
 * The second is not hypothetical: `SyncStatusScreen` did exactly that the moment M5.2 put it in the
 * graph, and it was caught by eye rather than by anything that would fail a build.
 *
 * [everyDestinationClearsTheStatusBar] catches the first. [stubsAllStartAtTheSameHeight] catches
 * the second with no tolerance at all — screens sharing a layout must share a top pixel — and
 * [noDestinationIsPaddedTwice] keeps catching it once they stop sharing one, because double-padding
 * has an exact signature: content that begins two status bars down instead of one.
 *
 * ### What this cannot see, and what did the seeing
 *
 * One emulated phone in portrait. Landscape, the display cutout, three-button versus gesture
 * navigation and a raised keyboard were walked by hand on the emulator in M5.5 — the shell was
 * correct in all of them, and the one real defect was in the setup wizard, which is outside this
 * graph (see `NotionSetupShortViewportTest`).
 *
 * `ManageHabits` is not exercised here: it has no entry point until M6/M7.5 build the header's `+`,
 * so there is no way to reach it without reaching past the harness into the `NavController`. It is
 * the same `StubScreen` behind the same `composable<>` entry as the five tabs.
 */
@RunWith(AndroidJUnit4::class)
class HabitsShellInsetsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ShellHarnessActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun tab(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(isSelectable())

    /** A screen's own title, as opposed to the tab of the same name — the tab is the selectable one. */
    private fun headingTop(id: Int): Float =
        composeTestRule
            .onAllNodesWithText(string(id))
            .filterToOne(hasNoClickAction())
            .fetchSemanticsNode()
            .positionInRoot
            .y

    /**
     * The status bar's height, read from the window rather than assumed.
     *
     * `systemBars` and not `statusBars`: the top inset is whichever of the two is larger, and on a
     * device with a cutout at the top they are not the same number.
     */
    private fun systemBarTop(): Int {
        var top = 0
        composeTestRule.activity.runOnUiThread {
            val root = composeTestRule.activity.window.decorView
            top =
                ViewCompat
                    .getRootWindowInsets(root)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())
                    ?.top ?: 0
        }
        composeTestRule.waitForIdle()
        return top
    }

    /**
     * The tabs still drawn by `StubScreen`, paired with the title each draws.
     *
     * ⚠ **Week is no longer among them.** M6.3 replaced its stub with the real `WeekContent`, which
     * has no header at all — `docs/design-spec.md` §1 puts the title in the screen header and
     * **M6.4** builds it. Week is covered by [everyDestinationClearsTheStatusBar] through its empty
     * state instead, and it is deliberately out of [stubsAllStartAtTheSameHeight], because that
     * test's zero-tolerance comparison only means anything between screens that share a layout.
     *
     * **This list shrinks as M6–M8 replace the stubs, and that is fine.** What it protects is the
     * exact regression M5.2 produced — one screen padding itself on top of the shell — and it keeps
     * protecting it for as long as two screens still share `StubScreen`. Stats is "Statistics" in
     * the header and "Stats" on the tab: the design's own asymmetry.
     */
    private val stubTabsAndHeadings =
        listOf(
            R.string.tab_month to R.string.screen_title_month,
            R.string.tab_year to R.string.screen_title_year,
            R.string.tab_stats to R.string.screen_title_stats,
            R.string.tab_settings to R.string.screen_title_settings,
        )

    /**
     * The **topmost** piece of text on each destination, whatever kind of screen it is.
     *
     * Week has no heading, so it is anchored on its empty state — the harness has no Notion
     * connection, so Room is empty and that is what Week correctly shows.
     *
     * ⚠ **The emoji, not the title, and the difference matters.** `EmptyState` draws a large emoji
     * above its heading, so anchoring on the heading measured the emoji's line height as if it were
     * padding — 318px against a 142px inset — and [noDestinationIsPaddedTwice] failed on a screen
     * that was laid out perfectly correctly. These anchors have to be the first thing drawn, or the
     * test measures content and calls it padding.
     */
    private val everyTabAndItsFirstText =
        stubTabsAndHeadings + (R.string.tab_week to R.string.week_empty_emoji)

    @Test
    fun everyDestinationClearsTheStatusBar() {
        val barTop = systemBarTop()
        // A zero inset would make this pass against anything, so it is worth knowing the device
        // under test actually has a status bar before trusting the rest.
        assertTrue("expected a non-zero top inset to test against, got $barTop", barTop > 0)

        everyTabAndItsFirstText.forEach { (tabLabel, heading) ->
            tab(tabLabel).performClick()
            composeTestRule.waitForIdle()

            val top = headingTop(heading)
            assertTrue(
                "${string(heading)} starts at $top, above the $barTop status bar",
                top >= barTop,
            )
        }

        // And the one destination without a bottom bar, which is where the double-padding bug was.
        tab(R.string.tab_settings).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_notion_sync)).performClick()
        composeTestRule.waitForIdle()

        val syncTop = headingTop(R.string.sync_title)
        assertTrue(
            "${string(R.string.sync_title)} starts at $syncTop, above the $barTop status bar",
            syncTop >= barTop,
        )
    }

    @Test
    fun stubsAllStartAtTheSameHeight() {
        // ⭐ The assertion that would have caught M5.2's double-padded sync status, and it needs no
        // tolerance to do it: every `StubScreen` uses the same modifier chain, so their titles must
        // land on exactly the same pixel. A screen that adds `windowInsetsPadding` of its own is
        // the only way for one to drift, and it drifts by a whole status bar.
        val tops =
            stubTabsAndHeadings.map { (tabLabel, heading) ->
                tab(tabLabel).performClick()
                composeTestRule.waitForIdle()
                string(heading) to headingTop(heading)
            }

        val reference = tops.first()
        tops.drop(1).forEach { (name, top) ->
            assertEquals(
                "$name starts at $top but ${reference.first} starts at ${reference.second}",
                reference.second,
                top,
                0f,
            )
        }
    }

    @Test
    fun noDestinationIsPaddedTwice() {
        // ⭐ What [stubsAllStartAtTheSameHeight] cannot say once screens stop sharing a layout, and
        // what will still be true when the last stub is gone.
        //
        // Double-padding has an exact signature: the screen applies the same inset the shell already
        // applied, so its content starts at **two** status bars rather than one. A real screen's own
        // content padding is far smaller than that — 20dp of `screenPadding` against a 48dp bar — so
        // one bar's worth of headroom separates "padded once, plus some design padding" from "padded
        // twice", with no number to tune.
        val barTop = systemBarTop()
        assertTrue("expected a non-zero top inset to test against, got $barTop", barTop > 0)

        everyTabAndItsFirstText.forEach { (tabLabel, anchor) ->
            tab(tabLabel).performClick()
            composeTestRule.waitForIdle()

            val top = headingTop(anchor)
            assertTrue(
                "${string(anchor)} starts at $top — a whole extra status bar below the $barTop " +
                    "inset, which is what applying the shell's insets a second time looks like",
                top < barTop * 2,
            )
        }
    }
}

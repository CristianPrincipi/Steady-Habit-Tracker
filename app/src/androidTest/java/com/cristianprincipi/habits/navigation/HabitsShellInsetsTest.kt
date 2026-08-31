package com.cristianprincipi.habits.navigation

import android.content.Context
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
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
import com.cristianprincipi.habits.core.datetime.monthName
import com.cristianprincipi.habits.gallery.ShellHarnessActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.Locale

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
 * [everyDestinationClearsTheStatusBar] catches the first. [everyBareTitleStartsAtTheSameHeight] catches
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
 * ⭐ **`ManageHabits` IS exercised here now.** That note used to say it could not be — it had no
 * entry point and could only be reached by reaching past the harness into the `NavController`. M7.5
 * gave it a Settings row, and it stopped being a `StubScreen` in the same step, so it is exactly the
 * kind of screen [noDestinationIsPaddedTwice] exists for: a real layout, reached without a tab.
 */
@RunWith(AndroidJUnit4::class)
class HabitsShellInsetsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ShellHarnessActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun tab(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(isSelectable())

    /** A screen's own title, as opposed to the tab of the same name — the tab is the selectable one. */
    private fun headingTop(id: Int): Float = headingTop(string(id))

    /**
     * The same, for a title that is not a resource.
     *
     * Month's is `monthName()` of whatever month it is (M8.1), so there is nothing to look up.
     */
    private fun headingTop(text: String): Float =
        composeTestRule
            .onAllNodesWithText(text)
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
     * The tabs whose first text is a bare title on `StubScreen`'s modifier chain, and that title.
     *
     * ⚠ **Neither Week nor Month is among them.** M6.3 replaced Week's stub with the real
     * `WeekContent` and M8.1 replaced Month's with `MonthContent`, which
     * has no header at all — `docs/design-spec.md` §1 puts the title in the screen header and
     * **M6.4** builds it. Week is covered by [everyDestinationClearsTheStatusBar] through its empty
     * state instead, and it is deliberately out of [everyBareTitleStartsAtTheSameHeight], because that
     * test's zero-tolerance comparison only means anything between screens that share a layout.
     *
     * ⚠ **Month left this list for a second reason**: its title is not a string resource at all.
     * The design takes it from the paged-to month (`monthName`), so it is computed below rather than
     * looked up — which is also why [everyTabAndItsFirstText] carries text and not ids.
     *
     * ⭐ **Year and Settings are no longer stubs and are still in here on purpose** (M8.3, M7.5).
     * What earns a place is not being a stub — it is opening with a bare title under the same
     * modifier chain, which is what makes a zero-tolerance comparison mean something. A real screen
     * that keeps that chain is exactly the one worth holding to it.
     *
     * **This list shrinks as M6–M9 replace the stubs, and that is fine.** What it protects is the
     * exact regression M5.2 produced — one screen padding itself on top of the shell — and it keeps
     * protecting it for as long as two screens still share `StubScreen`. Stats is "Statistics" in
     * the header and "Stats" on the tab: the design's own asymmetry.
     */
    private val tabsOpeningWithABareTitle =
        listOf(
            R.string.tab_year to R.string.screen_title_year,
            R.string.tab_stats to R.string.screen_title_stats,
            R.string.tab_settings to R.string.screen_title_settings,
        )

    /**
     * The **topmost** piece of text on each destination, whatever kind of screen it is.
     *
     * Week rejoins by its own header title now that M6.4 has built one — but only here, not in
     * [everyBareTitleStartsAtTheSameHeight]. Its title sits in a `Row` beside the "This week" button, and
     * a `TextButton` has a 40dp minimum height, so the row centres the title a few dp lower than a
     * stub's bare `Text`. That is a real cross-tab alignment question — the design puts a control
     * in this row on three views of five and none on the other two — and it is **the user's to
     * judge at M6.11**, not something to paper over with a fixed row height the design never gave.
     *
     * ⚠ Whatever is named here must be the **first thing drawn**. An earlier version anchored Week
     * on its empty state's *title*, which sits below a large emoji — and
     * [noDestinationIsPaddedTwice] duly failed on a perfectly correct screen, having measured that
     * emoji's line height as if it were padding.
     */
    private val everyTabAndItsFirstText: List<Pair<Int, String>>
        get() =
            tabsOpeningWithABareTitle.map { (tab, heading) -> tab to string(heading) } +
                (R.string.tab_week to string(R.string.screen_title_week)) +
                (R.string.tab_month to monthName(YearMonth.now(), Locale.getDefault()))

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
            assertTrue("$heading starts at $top, above the $barTop status bar", top >= barTop)
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
    fun theHabitListIsPaddedOnceLikeEveryOtherDestination() {
        // ⭐ The second screen without a bottom bar (M7.5), and the first one built after the rule
        // was written down. Both halves are asserted in one walk: clear of the status bar, and not a
        // whole second bar below it — which is what a `windowInsetsPadding` added here would look
        // like. Its own debug harness needs exactly that padding, because it stands outside the
        // shell; this screen must not have it, because it stands inside.
        val barTop = systemBarTop()
        assertTrue("expected a non-zero top inset to test against, got $barTop", barTop > 0)

        tab(R.string.tab_settings).performClick()
        composeTestRule
            .onAllNodesWithText(string(R.string.settings_habits))
            .filterToOne(hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()

        val top = headingTop(R.string.screen_title_habits)
        assertTrue("the habit list starts at $top, above the $barTop status bar", top >= barTop)
        assertTrue(
            "the habit list starts at $top — a whole extra status bar below the $barTop inset, " +
                "which is what applying the shell's insets a second time looks like",
            top < barTop * 2,
        )
    }

    @Test
    fun theMappingScreenIsPaddedOnceEvenThoughItsTwinIsNot() {
        // ⭐⭐ **The one place in the app where the same composable is drawn under two inset rules**,
        // and therefore the one most likely to get it wrong. `MapPropertiesStep` is rendered by the
        // setup wizard, which sits *outside* this graph and pads itself with `safeDrawing`; M7.8
        // renders it here, *inside* the shell, which pads for it. Copying the wizard's modifier
        // across would have looked entirely reasonable and put the content a second status bar down.
        //
        // Three destinations deep — a tab, then the habit list, then this — which nothing before
        // M7.8 exercised.
        val barTop = systemBarTop()
        assertTrue("expected a non-zero top inset to test against, got $barTop", barTop > 0)

        tab(R.string.tab_settings).performClick()
        composeTestRule
            .onAllNodesWithText(string(R.string.settings_habits))
            .filterToOne(hasClickAction())
            .performClick()
        composeTestRule.onNodeWithText(string(R.string.mapping_edit_action)).performClick()
        composeTestRule.waitForIdle()

        // Nothing is connected in an instrumented run, so the banner is the screen's first content —
        // and it is the only node here with no click action, which is what makes it measurable.
        val top = headingTop(R.string.notion_failure_not_connected)
        assertTrue("the mapping screen starts at $top, above the $barTop status bar", top >= barTop)
        assertTrue(
            "the mapping screen starts at $top — a whole extra status bar below the $barTop inset, " +
                "which is what rendering it with the wizard's own insets looks like",
            top < barTop * 2,
        )
    }

    @Test
    fun everyBareTitleStartsAtTheSameHeight() {
        // ⭐ The assertion that would have caught M5.2's double-padded sync status, and it needs no
        // tolerance to do it: these screens share a modifier chain, so their titles must
        // land on exactly the same pixel. A screen that adds `windowInsetsPadding` of its own is
        // the only way for one to drift, and it drifts by a whole status bar.
        val tops =
            tabsOpeningWithABareTitle.map { (tabLabel, heading) ->
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
        // ⭐ What [everyBareTitleStartsAtTheSameHeight] cannot say once screens stop sharing a layout, and
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
                "$anchor starts at $top — a whole extra status bar below the $barTop " +
                    "inset, which is what applying the shell's insets a second time looks like",
                top < barTop * 2,
            )
        }
    }
}

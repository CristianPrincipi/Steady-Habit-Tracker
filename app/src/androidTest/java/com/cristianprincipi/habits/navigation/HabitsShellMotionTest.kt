package com.cristianprincipi.habits.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.gallery.ShellHarnessActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M12.5 — the graph moves two ways, and this is what keeps them different.
 *
 * ### ⚠⚠ What was here before: a 700ms cross-fade nobody had chosen
 *
 * `NavHost`'s own defaults are `fadeIn(tween(700))` / `fadeOut(tween(700))`, so every tab switch
 * dissolved for seven tenths of a second — 4.7× the 150ms the design gives everything else, on the
 * control the user taps most. M12.5's audit had recorded "no screen transition exists", and it was
 * looking in the right place: the animation was not in our code. **That is how a library default
 * hides, and it is why this test asserts about time rather than about destinations.**
 * `HabitsShellNavigationTest` beside it already proves the graph arrives where it should — and a
 * 700ms fade passes every one of its assertions.
 *
 * ### It counts frames, and asserts a bound rather than an exact number
 *
 * With `autoAdvance = false` the frame clock only moves when this test says so, so the screen being
 * left behind can be watched frame by frame until it leaves the tree. The count is **not zero** even
 * for an instant switch — the navigation commits on one frame and `AnimatedContent` retires the
 * outgoing content on a later one — and pinning whatever that number happens to be today would be a
 * change detector aimed at Compose's internals. [MAX_LATERAL_FRAMES] is a ceiling far below the
 * **43 frames** a 700ms fade needs at 60Hz, and comfortably above the handful this really takes.
 *
 * The push test is the control. Without it the first would pass just as well if *every* transition
 * had been deleted, and `isLateral` could rot into a function that always answers `true`.
 */
@RunWith(AndroidJUnit4::class)
class HabitsShellMotionTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ShellHarnessActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** A bottom-nav item — the selectable one, as opposed to the heading of the same name. */
    private fun tab(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(isSelectable())

    /** A screen's own header title. */
    private fun heading(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filterToOne(hasNoClickAction())

    /**
     * Every *heading* reading this string — usually one, and after a completed move zero.
     *
     * ⚠ Not `onAllNodesWithText` alone. "Week" and "Settings" are each a screen title **and** a
     * permanent tab label, so a bare text count can never reach zero, and the first draft of this
     * test failed by counting the bar it was not asking about. The tab has a click action; the
     * heading does not.
     */
    private fun headings(id: Int) = composeTestRule.onAllNodesWithText(string(id)).filter(hasNoClickAction())

    /** The Settings row that pushes the sync status screen. */
    private fun syncRow() = composeTestRule.onNodeWithText(string(R.string.settings_notion_sync))

    /**
     * ⚠ **Settle the shell before stopping its clock**, or there is nothing on screen to tap.
     *
     * `ShellHarnessActivity` renders the real graph over the real database, so the bar arrives some
     * frames after the activity does. Freezing at frame zero froze it before the bar existed, and
     * both tests here failed on a missing tab rather than on anything they were written to measure.
     */
    private fun settleThenStopTheClock() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false
    }

    /**
     * How many frames the screen titled [id] survives the move that replaced it.
     *
     * Returns [FRAME_BUDGET] if it is still there at the end — which is more frames than a 700ms
     * cross-fade would need, so a regression to the default reads as "never left" rather than as a
     * flake.
     */
    private fun framesUntilGone(id: Int): Int {
        repeat(FRAME_BUDGET) { frame ->
            if (headings(id).fetchSemanticsNodes().isEmpty()) return frame
            composeTestRule.mainClock.advanceTimeByFrame()
        }
        return FRAME_BUDGET
    }

    @Test
    fun switchingTabsIsOverAlmostImmediately() {
        heading(R.string.screen_title_week).assertIsDisplayed()
        settleThenStopTheClock()

        tab(R.string.tab_stats).performClick()
        val frames = framesUntilGone(R.string.screen_title_week)

        assertTrue(
            "the outgoing tab was still on screen after $frames frames — the 700ms default is back",
            frames <= MAX_LATERAL_FRAMES,
        )
        heading(R.string.screen_title_stats).assertIsDisplayed()
    }

    /**
     * ⭐ The control: a push is **not** instant, so the branch above is a real branch.
     *
     * Settings stays on screen well past the point a tab switch has finished, because it is sliding
     * out from under the destination pushed over it. If `isLateral` ever starts answering `true`
     * everywhere — or the four transition lambdas are deleted — this is what notices.
     */
    @Test
    fun butPushingADestinationIsNot() {
        // Reached with the clock still running: getting *to* Settings is the other move, and this
        // test is about the one that starts here.
        tab(R.string.tab_settings).performClick()
        syncRow().assertIsDisplayed()
        settleThenStopTheClock()

        syncRow().performClick()
        val frames = framesUntilGone(R.string.screen_title_settings)

        assertTrue(
            "the pushed destination replaced Settings in $frames frames — it is not sliding",
            frames > MAX_LATERAL_FRAMES,
        )
    }

    private companion object {
        /**
         * A tab switch has to be finished inside this many frames.
         *
         * Generous against what an instant swap costs, and nowhere near the **43 frames** — 700ms at
         * 60Hz — that the default it replaced would have taken.
         */
        const val MAX_LATERAL_FRAMES = 8

        /** Long enough that a restored 700ms fade is still running when the count gives up. */
        const val FRAME_BUDGET = 30
    }
}

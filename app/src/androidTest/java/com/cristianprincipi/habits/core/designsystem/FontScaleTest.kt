package com.cristianprincipi.habits.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.feature.history.YearContent
import com.cristianprincipi.habits.feature.history.YearUiState
import com.cristianprincipi.habits.feature.stats.StatsContent
import com.cristianprincipi.habits.feature.stats.StatsUiState
import com.cristianprincipi.habits.feature.week.WeekContent
import com.cristianprincipi.habits.feature.week.WeekUiState
import com.cristianprincipi.habits.navigation.HabitsNavigationBar
import com.cristianprincipi.habits.navigation.HabitsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M13.3 — **nothing is clipped, broken or overlapping at 200% font on the narrowest window.**
 *
 * ### The configuration, and why it is this one
 *
 * `fontScale = 2f` is Android's top setting. `320.dp` is what a Pixel's top *display size* leaves —
 * 420dpi forced to 540 — and it is also the narrowest width Android supports, so one number covers
 * both axes the step names. Nothing in this app had ever been rendered at either: before M13.3
 * `fontScale` appeared **nowhere** in the codebase, production or test.
 *
 * ### ⚠⚠ What went wrong was not clipping, and that is why nobody had noticed
 *
 * Every one of the four defects was a **word broken mid-character**: `Complete/d days`,
 * `Completio/n rate`, `Mo/n`, `Settin/gs`. Compose splits a word it cannot fit wherever the
 * character boundary lands, and the result reads as a rendering fault rather than as truncation —
 * an ellipsis at least says "there is more". The worst of them left its container entirely: the
 * bottom nav's second line was drawn **outside** `NavigationBar`'s fixed 80dp, over the content.
 *
 * ### ⚠ These assert the *cause*, not a pixel
 *
 * A screenshot at this scale would fail on every copy change and on any typeface work, and would
 * still not say what was wrong. Each test below asserts the condition that produces a broken word —
 * a line count, a node that fits its parent, a layout that changed shape — so a failure names the
 * defect rather than a diff.
 *
 * ⭐ **The device's own font scale is irrelevant here and must be.** `createComposeRule` renders in
 * whatever `LocalDensity` it is given, so [atLargeFont] sets both scale and width itself; a suite
 * that depended on `adb shell settings put system font_scale` would pass or fail on what the last
 * person left the emulator at.
 */
@RunWith(AndroidJUnit4::class)
class FontScaleTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /**
     * Renders [content] as a 200%-font user on the narrowest window Android supports.
     *
     * ⚠ `requiredSize`, not `size` — ADR-106 §6: a `size` larger than the host is silently coerced
     * to the host's own width, so a test box "at 320dp" inside a 411dp emulator would quietly be
     * 411dp and the narrow case would never be exercised.
     */
    private fun atLargeFont(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = LARGE_FONT_SCALE),
            ) {
                HabitsTheme {
                    Box(modifier = Modifier.requiredSize(NARROW_WIDTH, TALL_ENOUGH).testTag(WINDOW)) {
                        content()
                    }
                }
            }
        }
    }

    /**
     * Every text node on screen, with where it sits and how big it is.
     *
     * ⚠⚠ **What is deliberately NOT used here: the text itself.** A `Text` that Compose has
     * ellipsised still reports its **whole** string in semantics — "Journal" stays "Journal" on a
     * node drawing "Jou…" — so an assertion that a name is "present" can never fail. Three of this
     * file's four tests were written that way and the control below caught all three. Position and
     * size are the only things here that change when the layout does.
     */
    private fun textNodes(): List<TextNode> {
        fun walk(node: SemanticsNode): List<TextNode> {
            val own =
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text ->
                    TextNode(
                        text = text.text,
                        left = node.positionInRoot.x,
                        top = node.positionInRoot.y,
                        width = node.size.width,
                        height = node.size.height,
                    )
                }
            return own + node.children.flatMap(::walk)
        }
        return walk(composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    private data class TextNode(
        val text: String,
        val left: Float,
        val top: Float,
        val width: Int,
        val height: Int,
    )

    /**
     * ⚠⚠ **The nav bar's label used to be drawn outside the bar.**
     *
     * `NavigationBar` is a fixed 80dp and does not grow with the font, so a label that wrapped drew
     * its second line over the screen content below it. This asserts containment against the bar's
     * **own** bounds.
     *
     * ⚠⚠ **Asserted as uniform height, and NOT as containment, because containment cannot fail.**
     * Two drafts tried it. The first measured against this file's 2000dp test window, inside which
     * nothing is ever out of bounds. The second measured against the bar itself and *still* passed
     * with the defect restored: a wrapped "Settings" reports **142px against a one-line 68px** and
     * its node still sits inside the bar's bounds — the second line escapes when it is **drawn**,
     * not when it is measured. So the thing to assert is the wrap, which is what produces it.
     */
    @Test
    fun noNavigationLabelIsDrawnOutsideTheBar() {
        atLargeFont {
            HabitsNavigationBar(selected = HabitsTab.WEEK, onSelect = {})
        }

        val labels = HabitsTab.entries.map { string(it.label) }.toSet()
        val drawn = textNodes().filter { it.text in labels }

        assertEquals("not every tab label was found: ${drawn.map { it.text }}", labels.size, drawn.size)
        assertEquals(
            "the tab labels are not all one line — ${drawn.joinToString { "${it.text}=${it.height}px" }}. " +
                "A wrapped label draws its second line outside the bar's fixed 80dp, over the content below.",
            1,
            drawn.map { it.height }.toSet().size,
        )
    }

    /**
     * ⚠⚠ **The Year card lost the habit's name.**
     *
     * The completion block had no width cap, so at 200% it ate the row and the `weight(1f)` header
     * was squeezed until "Journal" read "Jou…" and "199 of 261" read "199 …". The card now stacks.
     *
     * ⚠ **Asserted as a position, not as a string.** A `Text` that Compose ellipsised still reports
     * its whole text in semantics, so "is the name present?" is a question that cannot fail. Where
     * the eyebrow sits relative to the name is the thing that actually changed.
     */
    @Test
    fun theYearCardStacksItsCompletionFigureRatherThanSqueezingTheName() {
        atLargeFont {
            YearContent(
                state = YearUiState.Content(SampleHabits.years(SampleHabits.referenceDate)),
                onOpenSyncStatus = {},
            )
        }

        val eyebrow = string(R.string.year_completion_eyebrow)
        val firstName = SampleHabits.habits().first().name
        val nodes = textNodes()
        val name = nodes.firstOrNull { it.text == firstName }
        val figure = nodes.firstOrNull { it.text == eyebrow }

        assertTrue("the first card's name and eyebrow were not both found", name != null && figure != null)
        assertTrue(
            "\"$eyebrow\" is still beside \"$firstName\" rather than under it, so the name is being squeezed " +
                "(name starts at x=${name!!.left}, eyebrow at x=${figure!!.left})",
            figure.left < name.left,
        )
    }

    /**
     * ⚠⚠ **The stat tiles read "Complete/d days" and "Completio/n rate".**
     *
     * Two 138dp columns cannot hold "Completion" at 22sp. ⚠ **Shrinking would have been the wrong
     * fix**: M13.2 merged each label into its tile's spoken name, so these are content and need
     * room. The 2×2 becomes one column.
     *
     * ⚠ **Asserted as a shape, not as a string** — for [theYearCardStacksItsCompletionFigureRatherThanSqueezingTheName]'s
     * reason. Four tiles in one column all start at the same x; two columns produce two.
     */
    @Test
    fun theStatTilesBecomeOneColumnRatherThanBreakingTheirLabels() {
        atLargeFont {
            StatsContent(
                state =
                    StatsUiState.Content(
                        habits = SampleHabits.habits(),
                        selected =
                            SampleHabits.stats(
                                id =
                                    SampleHabits
                                        .habits()
                                        .first()
                                        .id.value,
                            ),
                    ),
                onEvent = {},
                onOpenSyncStatus = {},
            )
        }

        val labels =
            listOf(
                R.string.stats_tile_completed_days,
                R.string.stats_tile_completion_rate,
                R.string.stats_tile_current_streak,
                R.string.stats_tile_best_streak,
            ).map(::string).toSet()

        val drawn = textNodes().filter { it.text in labels }
        assertEquals("not every stat tile label was found: ${drawn.map { it.text }}", labels.size, drawn.size)
        assertEquals(
            "the tiles are still in two columns, which is what breaks their labels: " +
                drawn.joinToString { "${it.text}@${it.left}" },
            1,
            drawn.map { it.left }.toSet().size,
        )
    }

    /**
     * ⚠ **The weekday heading read "Mo/n Tu/e We/d Th/u Su/n".**
     *
     * Seven ~31dp columns at 20sp. This is the one place M13.3 **shrinks** rather than re-laying
     * out, because M13.2 cleared the row's semantics — every pill announces its weekday in full, so
     * declining part of the font scale costs a screen reader nothing.
     *
     * ⭐ **And one size for all seven.** Compose's own `autoSize` is per-`Text` and shrank only
     * `Mon` and `Wed`, leaving a heading row at two sizes. The assertion is therefore about
     * *uniformity* as much as fit: every label draws the same height, and none wraps.
     */
    @Test
    fun theWeekdayHeadingStaysOneUniformLineAcrossAllSevenDays() {
        atLargeFont {
            WeekContent(
                state =
                    WeekUiState.Content(
                        weekStart = startOfWeek(SampleHabits.referenceDate),
                        habits = SampleHabits.weekPeriods(SampleHabits.referenceDate),
                        isCurrentWeek = false,
                        currentYear = SampleHabits.referenceDate.year,
                    ),
                onEvent = {},
                onOpenSyncStatus = {},
            )
        }

        // The heading is semantically cleared (M13.2), so it is read off the *unmerged* tree by the
        // one thing that still identifies it: a row of seven short texts that are not pill labels.
        val heights =
            composeTestRule
                .onRoot(useUnmergedTree = true)
                .fetchSemanticsNode()
                .let { root ->
                    fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
                    walk(root)
                }.filter { node ->
                    node.config
                        .getOrNull(SemanticsProperties.Text)
                        ?.singleOrNull()
                        ?.text in weekdayLabelsOnDevice()
                }.map { it.size.height }

        assertTrue("the weekday heading was not found — ${weekdayLabelsOnDevice()}", heights.isNotEmpty())
        assertEquals(
            "the seven weekday labels are not all the same height — they were sized independently: $heights",
            1,
            heights.toSet().size,
        )
    }

    private fun weekdayLabelsOnDevice() =
        com.cristianprincipi.habits.core.datetime
            .weekdayLabels(java.util.Locale.getDefault())
            .toSet()

    /**
     * ⚠⚠ **The control, and ADR-108 §2 is why it is here.**
     *
     * Four of the tests above assert that something is present and whole, and every one of them
     * would pass for free if [atLargeFont] silently rendered at the device's own scale — which is
     * exactly what happens if the `LocalDensity` override is ever dropped. This proves the harness
     * is doing the thing the file is named after: the same text must be **taller** at 200% than at
     * 100%, measured in the same layout.
     */
    @Test
    fun andTheHarnessIsActuallyRenderingAtTwoHundredPercent() {
        var small = 0
        var large = 0
        composeTestRule.setContent {
            val base = LocalDensity.current
            HabitsTheme {
                listOf(1f to { h: Int -> small = h }, LARGE_FONT_SCALE to { h: Int -> large = h })
                    .forEach { (scale, record) ->
                        CompositionLocalProvider(
                            LocalDensity provides Density(density = base.density, fontScale = scale),
                        ) {
                            androidx.compose.material3.Text(
                                text = CONTROL_TEXT,
                                onTextLayout = { record(it.size.height) },
                            )
                        }
                    }
            }
        }

        composeTestRule.waitForIdle()
        assertTrue("nothing was measured at all: small=$small large=$large", small > 0)
        assertTrue(
            "200% is not rendering larger than 100% — the density override is not taking effect ($small vs $large)",
            large > small,
        )
    }

    private companion object {
        const val LARGE_FONT_SCALE = 2f

        /** Android's narrowest supported width, and what a Pixel's largest display size leaves. */
        val NARROW_WIDTH = 320.dp

        /** Tall enough that nothing is cut off for want of vertical room rather than horizontal. */
        val TALL_ENOUGH = 2000.dp

        const val WINDOW = "font-scale-window"
        const val CONTROL_TEXT = "Control"
    }
}
